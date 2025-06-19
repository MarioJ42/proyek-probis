package com.example.projectmdp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class DepositMaturityWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = Firebase.firestore
    private val depositsCollection = db.collection("deposits")
    private val usersCollection = db.collection("users")
    private val transactionsCollection = db.collection("transactions")

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DepositMaturityWorker", "Starting daily deposit maturity check")

                val snapshot = depositsCollection.whereEqualTo("status", "Active").get().await()
                val today = com.google.firebase.Timestamp.now()

                snapshot.documents.forEach { document ->
                    val deposit = document.toObject(Deposit::class.java)
                    if (deposit != null) {
                        val calendar = Calendar.getInstance().apply { time = deposit.startDate.toDate() }
                        calendar.add(Calendar.MONTH, deposit.tenorMonths)
                        val maturityDate = com.google.firebase.Timestamp(calendar.time)

                        if (today.seconds >= maturityDate.seconds || deposit.tenorMonths == 0) {
                            Log.d("DepositMaturityWorker", "Deposit matured: depositId=${document.id}, tenorMonths=${deposit.tenorMonths}")
                            processDepositMaturity(document.id, deposit)
                        } else if (today.seconds >= deposit.nextInterestDate.seconds) {
                            Log.d("DepositMaturityWorker", "Processing interest for depositId=${document.id}")
                            processDepositInterest(document.id, deposit)
                        }
                    }
                }

                Log.d("DepositMaturityWorker", "Daily deposit check completed")
                Result.success()
            } catch (e: Exception) {
                Log.e("DepositMaturityWorker", "Error in daily deposit check: ${e.message}", e)
                Result.retry()
            }
        }
    }

    private suspend fun processDepositMaturity(depositId: String, deposit: Deposit) {
        try {
            val userSnapshot = usersCollection.whereEqualTo("email", deposit.userEmail).get().await()
            if (userSnapshot.isEmpty) {
                Log.e("DepositMaturityWorker", "User not found for deposit: email=${deposit.userEmail}")
                return
            }
            val userDoc = userSnapshot.documents[0]
            val user = userDoc.toObject(User::class.java) ?: return

            val totalAmountInvested = deposit.amount
            val monthlyInterestRate = deposit.interestRate / 100 / 12

            val calculatedInterest = if (deposit.tenorMonths == 0) {
                totalAmountInvested * monthlyInterestRate
            } else {
                if (deposit.isReinvest) {
                    var currentPrincipal = totalAmountInvested
                    var accumulatedInterest = 0.0
                    for (i in 1..deposit.tenorMonths) {
                        val interest = currentPrincipal * monthlyInterestRate
                        accumulatedInterest += interest
                        currentPrincipal += interest
                    }
                    accumulatedInterest
                } else {
                    totalAmountInvested * monthlyInterestRate * deposit.tenorMonths
                }
            }

            val finalAmountToReturn = totalAmountInvested + calculatedInterest
            val newBalance = user.balance + finalAmountToReturn

            val transaksi = Transaksi(
                userEmail = deposit.userEmail,
                type = "Deposit Maturity",
                recipient = "System",
                amount = finalAmountToReturn,
                timestamp = com.google.firebase.Timestamp.now(),
                status = "Completed",
                orderId = "${deposit.orderId}_maturity_${System.currentTimeMillis()}"
            )

            db.runTransaction { transaction ->
                transaction.update(userDoc.reference, "balance", newBalance)
                val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                transaction.set(transactionDocRef, transaksi)
                transaction.update(depositsCollection.document(depositId), "status", "Matured")
            }.await()

            Log.d("DepositMaturityWorker", "Deposit matured and cashed out: depositId=$depositId, amount=$finalAmountToReturn")
        } catch (e: Exception) {
            Log.e("DepositMaturityWorker", "Error processing maturity for depositId=$depositId: ${e.message}", e)
        }
    }

    private suspend fun processDepositInterest(depositId: String, deposit: Deposit) {
        try {
            val userSnapshot = usersCollection.whereEqualTo("email", deposit.userEmail).get().await()
            if (userSnapshot.isEmpty) {
                Log.e("DepositMaturityWorker", "User not found for deposit: email=${deposit.userEmail}")
                return
            }
            val userDoc = userSnapshot.documents[0]
            val user = userDoc.toObject(User::class.java) ?: return

            val monthlyInterest = deposit.amount * (deposit.interestRate / 100) / 12
            Log.d("DepositMaturityWorker", "Processing interest for depositId=$depositId, monthlyInterest=$monthlyInterest, isReinvest=${deposit.isReinvest}")

            if (deposit.isReinvest) {
                val newAmount = deposit.amount + monthlyInterest
                val newInterestRate = when {
                    newAmount >= 50_000_000 -> 3.0
                    newAmount >= 20_000_000 -> 2.5
                    else -> 2.0
                }
                depositsCollection.document(depositId).update(
                    mapOf(
                        "amount" to newAmount,
                        "interestRate" to newInterestRate
                    )
                ).await()
            } else {
                val newBalance = user.balance + monthlyInterest
                val transaksi = Transaksi(
                    userEmail = deposit.userEmail,
                    type = "Deposit Interest",
                    recipient = "System",
                    amount = monthlyInterest,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = "${deposit.orderId}_interest_${System.currentTimeMillis()}"
                )
                db.runTransaction { transaction ->
                    transaction.update(userDoc.reference, "balance", newBalance)
                    val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                    transaction.set(transactionDocRef, transaksi)
                }.await()
            }

            val calendar = Calendar.getInstance()
            calendar.time = deposit.nextInterestDate.toDate()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val newNextInterestDate = com.google.firebase.Timestamp(calendar.time)
            depositsCollection.document(depositId).update("nextInterestDate", newNextInterestDate).await()

            Log.d("DepositMaturityWorker", "Interest processed for depositId=$depositId")
        } catch (e: Exception) {
            Log.e("DepositMaturityWorker", "Error processing interest for depositId=$depositId: ${e.message}", e)
        }
    }
}