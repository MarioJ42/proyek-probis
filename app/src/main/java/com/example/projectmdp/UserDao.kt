package com.example.projectmdp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun getUserByEmailAndPassword(email: String, password: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("UPDATE users SET balance = :newBalance WHERE email = :email")
    suspend fun updateBalance(email: String, newBalance: Double)

    @Update
    suspend fun updateUser(user: User)

    @Transaction
    suspend fun transfer(fromEmail: String, toEmail: String, amount: Double) {
        var sender = getUserByEmail(fromEmail)
        var recipient = getUserByEmail(toEmail)

        if (sender == null || recipient == null) {
            throw IllegalArgumentException("Sender or recipient not found")
        }

        if (sender.balance < amount) {
            throw IllegalArgumentException("Insufficient balance")
        }

        sender.balance -= amount
        recipient.balance += amount

        updateUser(sender)
        updateUser(recipient)
    }
}