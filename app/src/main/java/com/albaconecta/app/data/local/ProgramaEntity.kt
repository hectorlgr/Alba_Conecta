package com.albaconecta.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Caché local de programas publicados (`GET /api/v1/contenidos/programas`). */
@Entity(tableName = "programas")
data class ProgramaEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val descripcion: String,
    val categoriaId: String? = null,
    val imagenUrl: String? = null,
    /** Timestamp de la última sincronización exitosa (epoch millis UTC). */
    val ultimaSincronizacion: Long = System.currentTimeMillis()
)