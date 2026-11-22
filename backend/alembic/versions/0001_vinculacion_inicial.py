"""Migración inicial de vinculación."""

from alembic import op
import sqlalchemy as sa

revision = "0001_vinculacion"
down_revision = None
branch_labels = ("vinculacion",)
depends_on = None


def upgrade() -> None:
    op.create_table(
        "solicitud", sa.Column("id", sa.String(40), primary_key=True),
        sa.Column("folio", sa.String(16), nullable=False), sa.Column("estado", sa.String(30), nullable=False),
        sa.Column("nombre_contacto", sa.String(120), nullable=False), sa.Column("organizacion", sa.String(120)),
        sa.Column("email", sa.String(200)), sa.Column("telefono", sa.String(30)),
        sa.Column("mensaje", sa.String(2000), nullable=False), sa.Column("programa_id", sa.String(32)),
        sa.Column("producto_id", sa.String(32)), sa.Column("programa_titulo", sa.String(200)),
        sa.Column("producto_titulo", sa.String(200)), sa.Column("referencia_no_verificada", sa.Boolean(), nullable=False),
        sa.Column("detalles", sa.JSON(), nullable=False), sa.Column("observaciones_internas", sa.JSON(), nullable=False),
        sa.Column("fecha_creacion", sa.DateTime(timezone=True), nullable=False),
        sa.Column("fecha_actualizacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_solicitud_folio", "solicitud", ["folio"], unique=True)
    op.create_table(
        "historial_solicitud", sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("solicitud_id", sa.String(40), sa.ForeignKey("solicitud.id", ondelete="CASCADE"), nullable=False),
        sa.Column("estado_anterior", sa.String(30)), sa.Column("estado_nuevo", sa.String(30), nullable=False),
        sa.Column("observacion", sa.String(1000)), sa.Column("autor_id", sa.String(32)),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "idempotencia_solicitud", sa.Column("clave", sa.String(36), primary_key=True),
        sa.Column("huella", sa.String(64), nullable=False),
        sa.Column("solicitud_id", sa.String(40), sa.ForeignKey("solicitud.id", ondelete="SET NULL")),
        sa.Column("expira", sa.Float(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("idempotencia_solicitud")
    op.drop_table("historial_solicitud")
    op.drop_table("solicitud")
