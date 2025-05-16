package com.example.projectmdp

import com.google.firebase.firestore.PropertyName

data class User(
    @PropertyName("id") val id: String = "", // Firestore document ID
    @PropertyName("fullName") val fullName: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("password") val password: String = "", // Note: Avoid storing passwords if using Firebase Auth
    @PropertyName("balance") val balance: Double = 0.0,
    @PropertyName("premium") val premium: Boolean = false,
    @PropertyName("pin") val pin: String = "",
    @PropertyName("role") val role: Int = 0, // 0 = regular user, 1 = admin
    @PropertyName("status") val status: String = "active" // Default status
)