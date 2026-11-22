"""Migración inicial de identidad."""

from alembic import op
import sqlalchemy as sa

revision = "0001_identidad"
down_revision = None
branch_labels = ("identidad",)
depends_on = None


def upgrade() -> None:
    op.create_table(
        "usuario", sa.Column("id", sa.String(32), primary_key=True),
        sa.Column("nombre", sa.String(120), nullable=False), sa.Column("email", sa.String(200), nullable=False),
        sa.Column("password_hash", sa.String(300), nullable=False), sa.Column("tipo", sa.String(20), nullable=False),
        sa.Column("rol", sa.String(30), nullable=False), sa.Column("organizacion", sa.String(120)),
        sa.Column("activo", sa.Boolean(), nullable=False), sa.Column("fecha_creacion", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_usuario_email", "usuario", ["email"], unique=True)
    op.create_table(
        "refresh_token", sa.Column("token", sa.String(64), primary_key=True),
        sa.Column("usuario_id", sa.String(32), sa.ForeignKey("usuario.id", ondelete="CASCADE"), nullable=False),
        sa.Column("expira", sa.Float(), nullable=False), sa.Column("revocado_en", sa.DateTime(timezone=True)),
    )


def downgrade() -> None:
    op.drop_table("refresh_token")
    op.drop_table("usuario")
