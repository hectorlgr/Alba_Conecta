package com.albaconecta.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        ProgramaEntity::class,
        IndicadorEntity::class,
        SolicitudOutboxEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AlbaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun programaDao(): ProgramaDao
    abstract fun indicadorDao(): IndicadorDao
    abstract fun solicitudOutboxDao(): SolicitudOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: AlbaDatabase? = null

        /** Migración v1 → v2: agrega tablas de caché (programas, indicadores) y outbox. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tabla de programas (caché de contenidos)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS programas (
                        id TEXT NOT NULL PRIMARY KEY,
                        titulo TEXT NOT NULL,
                        descripcion TEXT NOT NULL,
                        categoriaId TEXT,
                        imagenUrl TEXT,
                        ultimaSincronizacion INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Tabla de indicadores (caché de contenidos)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS indicadores (
                        id TEXT NOT NULL PRIMARY KEY,
                        nombre TEXT NOT NULL,
                        valor REAL NOT NULL,
                        unidad TEXT,
                        periodo TEXT,
                        fuente TEXT,
                        ultimaSincronizacion INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Tabla de outbox de solicitudes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS solicitudes_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        idempotencyKey TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                        intentos INTEGER NOT NULL DEFAULT 0,
                        folioRemoto TEXT,
                        fechaCreacion INTEGER NOT NULL DEFAULT 0,
                        fechaActualizacion INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Room exige este nombre de índice exacto (schema 2.json); los índices extra rompen la validación.
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_solicitudes_outbox_idempotencyKey` " +
                        "ON `solicitudes_outbox` (`idempotencyKey`)"
                )
            }
        }

        fun getInstance(context: Context): AlbaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlbaDatabase::class.java,
                    "alba_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
