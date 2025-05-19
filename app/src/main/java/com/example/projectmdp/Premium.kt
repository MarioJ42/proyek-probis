package com.example.projectmdp

import com.google.firebase.firestore.PropertyName

data class Premium(
    @PropertyName("id") val id: String = "",
    @PropertyName("userEmail") val userEmail: String = "",
    @PropertyName("premium") val premium: Boolean = false,
    @PropertyName("requestPremium") val requestPremium: Boolean = false,
    @PropertyName("ktpPhoto") val ktpPhoto: String = ""
)