package com.albaconecta.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nombre: String,
    val apellido: String,
    val rut: String,
    val correo: String,
    val telefono: String,
    val fechaNacimiento: Long,
    val passwordHash: String,
    val passwordSalt: String
)
