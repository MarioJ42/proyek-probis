package com.example.projectmdp

import com.google.firebase.Timestamp

data class Transaksi(
    val userEmail: String = "",
    val type: String = "",
    val recipient: String? = null,
    val amount: Double = 0.0,
    val timestamp: Timestamp? = null,
    val status: String = "Completed",
    val orderId: String? = null // Tambahkan untuk QRIS
)