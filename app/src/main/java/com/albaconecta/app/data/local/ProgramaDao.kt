package com.albaconecta.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProgramaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programas: List<ProgramaEntity>)

    @Query("SELECT * FROM programas ORDER BY titulo ASC")
    suspend fun getAll(): List<ProgramaEntity>

    @Query("SELECT * FROM programas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProgramaEntity?

    @Query("DELETE FROM programas")
    suspend fun clearAll()

    @Update
    suspend fun update(programa: ProgramaEntity)
}