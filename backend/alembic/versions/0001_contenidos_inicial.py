"""Migración inicial de contenidos."""

from alembic import op
import sqlalchemy as sa

revision = "0001_contenidos"
down_revision = None
branch_labels = ("contenidos",)
depends_on = None


def upgrade() -> None:
    op.create_table(
        "categoria",
        sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("nombre", sa.String(120), nullable=False),
        sa.Column("tipo", sa.String(20), nullable=False),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado_en", sa.DateTime(timezone=True)),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "programa",
        sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("titulo", sa.String(200), nullable=False),
        sa.Column("descripcion", sa.String(2000), nullable=False),
        sa.Column("categoria_id", sa.String(32)),
        sa.Column("imagen_url", sa.String(500)),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado_en", sa.DateTime(timezone=True)),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "historia",
        sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("titulo", sa.String(200), nullable=False),
        sa.Column("resumen", sa.String(2000), nullable=False),
        sa.Column("autorizada", sa.Boolean(), nullable=False),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado_en", sa.DateTime(timezone=True)),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "indicador",
        sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("nombre", sa.String(120), nullable=False),
        sa.Column("valor", sa.Float(), nullable=False),
        sa.Column("unidad", sa.String(40), nullable=False),
        sa.Column("periodo", sa.String(20), nullable=False),
        sa.Column("fuente", sa.String(200), nullable=False),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado_en", sa.DateTime(timezone=True)),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "producto",
        sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("nombre", sa.String(200), nullable=False),
        sa.Column("descripcion", sa.String(2000), nullable=False),
        sa.Column("categoria_id", sa.String(32)),
        sa.Column("disponible", sa.Boolean(), nullable=False),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado_en", sa.DateTime(timezone=True)),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    for tabla in ("producto", "indicador", "historia", "programa", "categoria"):
        op.drop_table(tabla)
