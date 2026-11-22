"""Backend Alba Conecta.

Un solo paquete con cuatro servicios independientes (`contenidos`, `vinculacion`,
`identidad`, `talento`). Cada uno expone su propio objeto `app` y se levanta en su
puerto con uvicorn; comparten el paquete `common` para no duplicar config/errores.
"""
