package com.example.projectmdp

import com.google.firebase.Timestamp

data class BankAccount(
    val id: String = "",
    val userEmail: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val accountHolderName: String = "",
    val createdAt: Timestamp = Timestamp.now()
)