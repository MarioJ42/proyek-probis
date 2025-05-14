package com.example.projectmdp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val email: String,
    val password: String,
    var balance: Double = 0.0,
    var premium: Boolean = false,
    val pin: String // Add 6-digit PIN
)