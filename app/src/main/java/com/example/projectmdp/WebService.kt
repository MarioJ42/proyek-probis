package com.example.projectmdp

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface WebService {
    @GET("students")
    suspend fun getStudents(@Query("q")q:String = ""):List<User>
//    @GET("students/{nrp}")
//    suspend fun getStudentByNrp(@Path("nrp")nrp:String):Student
//    @POST("students")
//    suspend fun insertStudent(@Body student: Student):Student
//    @PUT("students/{nrp}")
//    suspend fun updateStudent(@Path("nrp")nrp: String, @Body student: Student):Student
//    @DELETE("students/{nrp}")
//    suspend fun deleteStudent(@Path("nrp")nrp:String):Student
}