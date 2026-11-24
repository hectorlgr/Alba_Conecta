package com.albaconecta.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface IndicadorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(indicadores: List<IndicadorEntity>)

    @Query("SELECT * FROM indicadores ORDER BY nombre ASC")
    suspend fun getAll(): List<IndicadorEntity>

    @Query("SELECT * FROM indicadores WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IndicadorEntity?

    @Query("DELETE FROM indicadores")
    suspend fun clearAll()

    @Update
    suspend fun update(indicador: IndicadorEntity)
}