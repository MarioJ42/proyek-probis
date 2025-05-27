package com.example.projectmdp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RememberedUserDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: RememberedUser)

    @Query("SELECT * FROM remembered_user WHERE id = :id")
    suspend fun getUser(id: Int): RememberedUser?
}