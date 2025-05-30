package com.example.projectmdp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remembered_user")
data class RememberUser(
    @PrimaryKey val id: Int = 0,
    val email: String,
    val password: String
)