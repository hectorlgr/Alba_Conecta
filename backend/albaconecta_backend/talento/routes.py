"""Rutas de `ms-talento` (§4.6, alcance acotado)."""

from typing import Annotated

from fastapi import APIRouter, Depends

from ..common.errors import ApiError
from ..common.http import Limite as LimiteQ
from ..common.http import Pagina as PaginaQ
from ..common.http import paginar
from ..common.security import requiere
from .data import almacen, apoyos_publicos, candidatos_publicos
from .schemas import (
    ApoyoOut,
    CandidatoOmitido,
    CandidatoOut,
    CompatibilidadOut,
    DiagnosticoIn,
    DiagnosticoOut,
    Pagina,
    PuestoIn,
    PuestoOut,
    ResultadoCompatibilidad,
)

router = APIRouter(prefix="/api/v1/talento", tags=["talento"])

EMPRESA = Annotated[dict, Depends(requiere("EMPRESA"))]


@router.get("/candidatos", response_model=Pagina[CandidatoOut])
async def listar_candidatos(page: PaginaQ = 1, limit: LimiteQ = 20):
    items = candidatos_publicos()
    return paginar(items, page, limit)


@router.get("/candidatos/{codigo_publico}", response_model=CandidatoOut)
async def detalle_candidato(codigo_publico: str):
    candidato = next((c for c in candidatos_publicos() if c["codigo_publico"] == codigo_publico), None)
    if candidato is None or not candidato["publicado"] or candidato["eliminado"]:
        raise ApiError(404, "NO_ENCONTRADO", "El candidato no existe o no está publicado.")
    return candidato


@router.get("/apoyos", response_model=list[ApoyoOut])
async def listar_apoyos():
    return apoyos_publicos()


def _puesto_propio(id_: str, empresa: dict) -> dict:
    """Caso borde 14: un puesto ajeno responde 404, sin revelar que existe."""
    puesto = almacen.puestos.get(id_)
    if puesto is None or puesto["empresa_id"] != empresa["sub"]:
        raise ApiError(404, "NO_ENCONTRADO", f"No existe el puesto {id_}.")
    return puesto


def _puesto_out(puesto: dict) -> dict:
    """Recorta el registro interno (no se expone `empresa_id`)."""
    return {k: v for k, v in puesto.items() if k in PuestoOut.model_fields}


@router.post("/puestos", status_code=201, response_model=PuestoOut)
async def crear_puesto(datos: PuestoIn, empresa: EMPRESA):
    return _puesto_out(almacen.crear_puesto(empresa["sub"], datos.model_dump()))


@router.get("/puestos/{id_}", response_model=PuestoOut)
async def detalle_puesto(id_: str, empresa: EMPRESA):
    return _puesto_out(_puesto_propio(id_, empresa))


@router.post("/puestos/{id_}/compatibilidad", response_model=CompatibilidadOut)
async def compatibilidad(id_: str, empresa: EMPRESA):
    """Caso borde 13: sin apoyos declarados el candidato se excluye, sin puntaje inventado."""
    puesto = _puesto_propio(id_, empresa)
    requeridos = {tarea["apoyo_codigo"] for tarea in puesto["tareas"]}
    if not requeridos:
        raise ApiError(409, "REGLA_NEGOCIO", "El puesto no tiene tareas con apoyo asociado.")

    resultados: list[dict] = []
    omitidos: list[dict] = []
    for candidato in candidatos_publicos():
        declarados = set(candidato["apoyos"])
        if not declarados:
            omitidos.append({"codigo_publico": candidato["codigo_publico"], "motivo": "Sin apoyos declarados."})
            continue
        cubiertos = sorted(requeridos & declarados)
        faltantes = sorted(requeridos - declarados)
        resultados.append(
            {
                "codigo_publico": candidato["codigo_publico"],
                "puntaje": round(100 * len(cubiertos) / len(requeridos)),
                "explicacion": f"Cubre {len(cubiertos)} de {len(requeridos)} apoyos requeridos"
                + (f": {', '.join(cubiertos)}." if cubiertos else "."),
                "apoyos_faltantes": faltantes,
            }
        )

    ordenados = [ResultadoCompatibilidad(**r) for r in sorted(resultados, key=lambda r: (-r["puntaje"], r["codigo_publico"]))]
    return {
        "puesto_id": puesto["id"],
        "resultados": ordenados,
        "omitidos": [CandidatoOmitido(**o) for o in omitidos],
    }


@router.post("/diagnostico", status_code=201, response_model=DiagnosticoOut)
async def crear_diagnostico(datos: DiagnosticoIn):
    catalogo = {apoyo["codigo"]: apoyo for apoyo in apoyos_publicos()}
    desconocidos = [codigo for codigo in datos.apoyos_interes if codigo not in catalogo]
    if desconocidos:
        raise ApiError(
            422,
            "VALIDACION",
            "Hay apoyos que no existen en el catálogo.",
            errors=[{"field": "apoyos_interes", "code": "no_existe", "message": f"Desconocidos: {', '.join(desconocidos)}"}],
        )
    diagnostico = almacen.crear_diagnostico(datos.apoyos_interes)
    sugerencias = [
        f"{catalogo[codigo]['nombre']}: {catalogo[codigo]['descripcion']}" for codigo in datos.apoyos_interes
    ]
    return {
        "id": diagnostico["id"],
        "sugerencias": sugerencias,
        "fecha_creacion": diagnostico["fecha_creacion"],
    }


@router.get("/diagnostico/{id_}", response_model=DiagnosticoOut)
async def detalle_diagnostico(id_: str):
    diagnostico = almacen.diagnosticos.get(id_)
    if diagnostico is None:
        raise ApiError(404, "NO_ENCONTRADO", f"No existe el diagnóstico {id_}.")
    catalogo = {apoyo["codigo"]: apoyo for apoyo in apoyos_publicos()}
    sugerencias = [
        f"{catalogo[codigo]['nombre']}: {catalogo[codigo]['descripcion']}"
        for codigo in diagnostico["apoyos_interes"]
        if codigo in catalogo
    ]
    return {"id": diagnostico["id"], "sugerencias": sugerencias, "fecha_creacion": diagnostico["fecha_creacion"]}
