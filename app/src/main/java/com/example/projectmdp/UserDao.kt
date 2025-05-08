package com.example.projectmdp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert
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
}