package com.albaconecta.app.data.repository

import com.albaconecta.app.data.local.UserDao
import com.albaconecta.app.data.local.UserEntity

class UserRepository(private val userDao: UserDao) {
    suspend fun insertUser(user: UserEntity): Long =
        userDao.insertUser(user.copy(correo = user.correo.trim().lowercase()))

    suspend fun getUserByEmail(correo: String): UserEntity? =
        userDao.getUserByEmail(correo.trim().lowercase())

    suspend fun getUserById(id: Int): UserEntity? = userDao.getUserById(id)

    suspend fun getExistingUser(): UserEntity? = userDao.getExistingUser()

    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)
}
