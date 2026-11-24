"""Migración inicial del catálogo público de talento."""

from alembic import op
import sqlalchemy as sa


revision = "0001_talento"
down_revision = None
branch_labels = ("talento",)
depends_on = None


def upgrade() -> None:
    op.create_table(
        "apoyo",
        sa.Column("codigo", sa.String(20), primary_key=True),
        sa.Column("nombre", sa.String(120), nullable=False),
        sa.Column("descripcion", sa.String(500), nullable=False),
    )
    op.create_table(
        "candidato",
        sa.Column("codigo_publico", sa.String(20), primary_key=True),
        sa.Column("iniciales", sa.String(20), nullable=False),
        sa.Column("resumen", sa.String(500), nullable=False),
        sa.Column("publicado", sa.Boolean(), nullable=False),
        sa.Column("eliminado", sa.Boolean(), nullable=False),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "candidato_apoyo",
        sa.Column("candidato_codigo", sa.String(20), sa.ForeignKey("candidato.codigo_publico", ondelete="CASCADE"), primary_key=True),
        sa.Column("apoyo_codigo", sa.String(20), sa.ForeignKey("apoyo.codigo", ondelete="CASCADE"), primary_key=True),
    )


def downgrade() -> None:
    op.drop_table("candidato_apoyo")
    op.drop_table("candidato")
    op.drop_table("apoyo")
