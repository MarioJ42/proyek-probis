package com.example.projectmdp.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/generate-qris")
    suspend fun generateQris(@Body request: QrisRequest): QrisResponse

    @GET("api/verify-qris/{orderId}")
    suspend fun verifyQrisPayment(@Path("orderId") orderId: String): QrisVerificationResponse

    @POST("api/qris/simulate")
    suspend fun simulateQrisPayment(@Body request: SimulateQrisRequest): SimulateQrisResponse
}

data class QrisRequest(val amount: Int)
data class QrisResponse(val success: Boolean, val order_id: String, val qr_string: String, val amount: Int, val message: String)
data class QrisVerificationResponse(val success: Boolean, val order_id: String, val status: String, val fraud_status: String?, val payment_method: String?, val settlement_time: String?, val status_code: String?, val amount: Float?, val message: String)
data class SimulateQrisRequest(val order_id: String, val amount: Int)
data class SimulateQrisResponse(val success: Boolean, val message: String, val order_id: String, val status: String)