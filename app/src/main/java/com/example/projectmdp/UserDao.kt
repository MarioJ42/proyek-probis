package com.example.projectmdp

import androidx.room.Dao
<<<<<<< Updated upstream
import androidx.room.Insert
import androidx.room.Query
=======
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
>>>>>>> Stashed changes

@Dao
interface UserDao {
    @Insert
<<<<<<< Updated upstream
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun getUserByEmailAndPassword(email: String, password: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("UPDATE users SET balance = :newBalance WHERE email = :email")
    suspend fun updateBalance(email: String, newBalance: Double)
=======
    suspend fun insert(user:UserEntity)

    @Update
    suspend fun update(user:UserEntity)

    @Delete
    suspend fun delete(user:UserEntity)

    @Query("DELETE FROM users where name = :name")
    suspend fun deleteQuery(name: String):Int //return Int jika mau tau brp row yg kehapus

    @Query("SELECT * FROM users")
    suspend fun fetch():List<UserEntity>

    @Query("SELECT * FROM users where name = :name")
    suspend fun get(name:String):UserEntity?
>>>>>>> Stashed changes
}