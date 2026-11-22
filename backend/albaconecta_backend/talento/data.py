"""Almacén en memoria de `ms-talento` (Fase 1). `shortcut: se reemplaza en Fase 8`."""

from datetime import datetime, timezone


def ahora() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


APOYOS = [
    {"codigo": "AP-01", "nombre": "Lector de pantalla", "descripcion": "Software de apoyo para baja visión."},
    {"codigo": "AP-02", "nombre": "Intérprete de señas", "descripcion": "Acompañamiento en lengua de señas."},
    {"codigo": "AP-03", "nombre": "Adecuación de puesto", "descripcion": "Ajustes físicos del espacio de trabajo."},
    {"codigo": "AP-04", "nombre": "Transporte adaptado", "descripcion": "Traslado puerta a puerta."},
]

# Candidatos anonimizados: nunca datos personales reales (§4.6).
_CANDIDATOS = [
    ("C-001", "M.S.", "Operaria de producción con experiencia en líneas de ensamblaje.", ["AP-01", "AP-02"], 40),
    ("C-002", "J.P.", "Auxiliar administrativo con formación técnica en contabilidad.", ["AP-02"], 35),
    ("C-003", "R.T.", "Perfil en evaluación, sin apoyos declarados todavía.", [], 10),
    ("C-004", "L.G.", "Técnica en logística y control de inventario.", ["AP-01", "AP-03"], 45),
]


class Almacen:
    def __init__(self) -> None:
        self.candidatos: dict[str, dict] = {}
        self.puestos: dict[str, dict] = {}
        self.diagnosticos: dict[str, dict] = {}
        self._secuencia_puesto = 0
        self._secuencia_diagnostico = 0
        for codigo, iniciales, resumen, apoyos, minutos in _CANDIDATOS:
            self.candidatos[codigo] = {
                "codigo_publico": codigo,
                "iniciales": iniciales,
                "resumen": resumen,
                "apoyos": apoyos,
                "publicado": True,
                "eliminado": False,
                "fecha_actualizacion": datetime.fromtimestamp(
                    datetime.now(timezone.utc).timestamp() - minutos * 60, tz=timezone.utc
                ).replace(microsecond=0),
            }

    def candidatos_publicos(self) -> list[dict]:
        return [c for c in self.candidatos.values() if c["publicado"] and not c["eliminado"]]

    def crear_puesto(self, empresa_id: str, datos: dict) -> dict:
        self._secuencia_puesto += 1
        puesto = {
            "id": f"pue-{self._secuencia_puesto}",
            "empresa_id": empresa_id,
            "titulo": datos["titulo"],
            "descripcion": datos.get("descripcion"),
            "tareas": datos["tareas"],
            "fecha_creacion": ahora(),
        }
        self.puestos[puesto["id"]] = puesto
        return puesto

    def crear_diagnostico(self, apoyos_interes: list[str]) -> dict:
        self._secuencia_diagnostico += 1
        diagnosticos = {
            "id": f"dia-{self._secuencia_diagnostico}",
            "apoyos_interes": apoyos_interes,
            "fecha_creacion": ahora(),
        }
        self.diagnosticos[diagnosticos["id"]] = diagnosticos
        return diagnosticos


almacen = Almacen()
