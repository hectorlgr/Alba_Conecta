package com.albaconecta.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM usuarios WHERE correo = :correo LIMIT 1")
    suspend fun getUserByEmail(correo: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT * FROM usuarios LIMIT 1")
    suspend fun getExistingUser(): UserEntity?

    @Delete
    suspend fun deleteUser(user: UserEntity)
}
