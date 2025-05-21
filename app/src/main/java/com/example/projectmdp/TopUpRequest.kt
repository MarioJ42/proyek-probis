package com.example.projectmdp.network

data class TopUpRequest(
    val user_email: String,
    val amount: Int
)