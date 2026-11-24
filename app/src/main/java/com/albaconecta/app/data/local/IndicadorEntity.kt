package com.albaconecta.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Caché local de indicadores de impacto (`GET /api/v1/contenidos/indicadores`). */
@Entity(tableName = "indicadores")
data class IndicadorEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val valor: Double,
    val unidad: String? = null,
    val periodo: String? = null,
    val fuente: String? = null,
    /** Timestamp de la última sincronización exitosa (epoch millis UTC). */
    val ultimaSincronizacion: Long = System.currentTimeMillis()
)