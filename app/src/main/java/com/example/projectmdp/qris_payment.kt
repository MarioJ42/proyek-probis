package com.example.projectmdp

import com.google.firebase.Timestamp

data class QrisPayment(
    val orderId: String = "",
    val userEmail: String = "",
    val qrString: String = "",
    val amount: Double = 0.0,
    val status: String = "pending",
    val timestamp: Timestamp? = null
)