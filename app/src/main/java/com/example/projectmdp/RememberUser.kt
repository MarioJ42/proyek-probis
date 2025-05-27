package com.example.projectmdp

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
@Serializable
@Entity(tableName = "remembered_user")
data class RememberedUser(
    @PrimaryKey val id: Int = 0,
    val email: String = "",
    val password: String = ""
)