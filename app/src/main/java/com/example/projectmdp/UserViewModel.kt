package com.example.projectmdp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projectmdp.network.SimulateQrisRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

sealed class PaymentResult {
    data class Success(val amount: Double) : PaymentResult()
    data class Failure(val message: String) : PaymentResult()
}

sealed class SimulationResult {
    data class Success(val message: String) : SimulationResult()
    data class Failure(val message: String) : SimulationResult()
}

class UserViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val transactionsCollection = db.collection("transactions")
    private val qrisPaymentsCollection = db.collection("qris_payments")

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> get() = _user

    private val _userEmail = MutableLiveData<String?>()
    val userEmail: LiveData<String?> get() = _userEmail

    private val _balance = MutableLiveData<Double>()
    val balance: LiveData<Double> get() = _balance

    private val _loginResult = MutableLiveData<Boolean?>()
    val loginResult: LiveData<Boolean?> get() = _loginResult

    private val _topUpResult = MutableLiveData<Boolean>()
    val topUpResult: LiveData<Boolean> get() = _topUpResult

    private val _paymentResult = MutableLiveData<PaymentResult>()
    val paymentResult: LiveData<PaymentResult> get() = _paymentResult

    private val _simulationResult = MutableLiveData<SimulationResult>()
    val simulationResult: LiveData<SimulationResult> get() = _simulationResult

    init {
        createAdminUser()
    }

    fun setUserEmail(email: String) {
        _userEmail.value = email
        fetchUser(email)
    }

    private fun createAdminUser() {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", "william@gmail.com").get().await()
                if (snapshot.isEmpty) {
                    val adminUser = User(
                        id = "",
                        fullName = "William",
                        email = "william@gmail.com",
                        pin = "123456",
                        role = 1,
                        status = "active"
                    )
                    usersCollection.add(adminUser).await()
                    Log.d("UserViewModel", "Admin user created: william@gmail.com")
                    fetchUser("william@gmail.com")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Failed to create admin user: ${e.message}", e)
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                Log.d("UserViewModel", "Login query for email: $email, snapshot size: ${snapshot.size()}")
                if (snapshot.isEmpty) {
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = snapshot.documents[0].toObject(User::class.java)?.copy(id = snapshot.documents[0].id)
                if (user?.password == password) {
                    _user.postValue(user)
                    _userEmail.postValue(email)
                    _balance.postValue(user.balance)
                    _loginResult.postValue(true)
                } else {
                    _loginResult.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Login error for email $email: ${e.message}", e)
                _loginResult.postValue(null)
            }
        }
    }

    fun clearLoginResult() {
        _loginResult.postValue(null)
    }

    fun fetchUser(email: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val user = doc.toObject(User::class.java)?.copy(id = doc.id)
                    if (user != null) {
                        _user.postValue(user)
                        _userEmail.postValue(email)
                        _balance.postValue(user.balance)
                        Log.d("UserViewModel", "Fetched user: email=$email, balance=${user.balance}")
                    } else {
                        Log.e("UserViewModel", "Failed to parse user for email=$email")
                        _user.postValue(null)
                        _userEmail.postValue(null)
                    }
                } else {
                    Log.e("UserViewModel", "No user found for email=$email")
                    _user.postValue(null)
                    _userEmail.postValue(null)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Fetch user error for email=$email: ${e.message}", e)
                _user.postValue(null)
                _userEmail.postValue(null)
            }
        }
    }

    fun register(email: String, fullName: String, password: String, pin: String, phone: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
                if (!snapshot.isEmpty) {
                    Log.d("UserViewModel", "Email already exists: $email")
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = User(
                    id = "",
                    fullName = fullName,
                    email = email.lowercase(),
                    password = password,
                    pin = pin,
                    role = 0,
                    status = "active",
                    phone = phone
                )
                val docRef = usersCollection.add(user).await()
                Log.d("UserViewModel", "User registered: $email, document ID: ${docRef.id}")
                _loginResult.postValue(true)
                _userEmail.postValue(email.lowercase())
                fetchUser(email.lowercase())
            } catch (e: Exception) {
                Log.e("UserViewModel", "Registration error for email=$email: ${e.message}", e)
                _loginResult.postValue(null)
            }
        }
    }

    fun updateUserProfile(email: String, fullName: String, newEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    usersCollection.document(docId).update(
                        mapOf(
                            "fullName" to fullName,
                            "email" to newEmail
                        )
                    ).await()
                    fetchUser(newEmail)
                    Log.d("UserViewModel", "Updated profile: email=$email to newEmail=$newEmail")
                } else {
                    Log.e("UserViewModel", "User not found for profile update: email=$email")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Update profile error for email=$email: ${e.message}", e)
            }
        }
    }

    fun topUp(email: String, amount: Double) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    val currentBalance = snapshot.documents[0].toObject(User::class.java)?.balance ?: 0.0
                    usersCollection.document(docId).update("balance", currentBalance + amount).await()

                    val transaksi = Transaksi(
                        userEmail = email,
                        type = "TopUp Saldo",
                        recipient = "System",
                        amount = amount,
                        timestamp = com.google.firebase.Timestamp.now(),
                        status = "Completed"
                    )
                    logTransaction(transaksi)

                    fetchUser(email)
                    Log.d("UserViewModel", "Top-up successful: email=$email, amount=$amount")
                    _topUpResult.postValue(true)
                } else {
                    Log.e("UserViewModel", "Top-up failed: User not found for email=$email")
                    _topUpResult.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Top-up error for email=$email: ${e.message}", e)
                _topUpResult.postValue(false)
            }
        }
    }

    fun transfer(fromEmail: String, toEmail: String, amount: Double, onResult: (String?) -> Unit) {
        Log.d("UserViewModel", "Starting transfer: from=$fromEmail, to=$toEmail, amount=$amount")
        viewModelScope.launch {
            try {
                val fromSnapshot = usersCollection.whereEqualTo("email", fromEmail).get().await()
                val toSnapshot = usersCollection.whereEqualTo("email", toEmail).get().await()

                if (fromSnapshot.isEmpty || toSnapshot.isEmpty) {
                    throw IllegalArgumentException("Sender or recipient not found")
                }

                val fromDoc = fromSnapshot.documents[0]
                val toDoc = toSnapshot.documents[0]
                val sender = fromDoc.toObject(User::class.java)
                val recipient = toDoc.toObject(User::class.java)

                if (sender == null || recipient == null) {
                    throw IllegalArgumentException("Sender or recipient data invalid")
                }

                if (sender.balance < amount) {
                    throw IllegalArgumentException("Insufficient balance")
                }

                db.runTransaction { transaction ->
                    transaction.update(fromDoc.reference, "balance", sender.balance - amount)
                    transaction.update(toDoc.reference, "balance", recipient.balance + amount)
                }.await()

                val transaksi = Transaksi(
                    userEmail = fromEmail,
                    type = "Transfer",
                    recipient = toEmail,
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )
                logTransaction(transaksi)

                fetchUser(fromEmail)
                Log.d("UserViewModel", "Transfer successful: New sender balance=${_user.value?.balance}")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Transfer failed from $fromEmail to $toEmail: ${e.message}", e)
                onResult(e.message)
            }
        }
    }

    fun transferToBank(userEmail: String, bankAccount: String, amount: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val userSnapshot = usersCollection.whereEqualTo("email", userEmail).get().await()
                if (userSnapshot.isEmpty) {
                    throw IllegalArgumentException("User not found")
                }

                val userDoc = userSnapshot.documents[0]
                val user = userDoc.toObject(User::class.java)
                if (user == null || user.balance < amount) {
                    throw IllegalArgumentException("Insufficient balance")
                }

                db.runTransaction { transaction ->
                    transaction.update(userDoc.reference, "balance", user.balance - amount)
                }.await()

                val transaksi = Transaksi(
                    userEmail = userEmail,
                    type = "Bank Transfer",
                    recipient = bankAccount,
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )
                logTransaction(transaksi)

                fetchUser(userEmail)
                Log.d("UserViewModel", "Bank transfer successful: New balance=${_user.value?.balance}")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Bank transfer failed for $userEmail: ${e.message}", e)
                onResult(e.message)
            }
        }
    }

    suspend fun logTransaction(transaksi: Transaksi) {
        val normalizedTransaksi = transaksi.copy(userEmail = transaksi.userEmail.lowercase())
        try {
            transactionsCollection.add(normalizedTransaksi).await()
            Log.d("UserViewModel", "Transaction logged: $normalizedTransaksi")
        } catch (e: Exception) {
            Log.e("UserViewModel", "Failed to log transaction: ${e.message}", e)
            throw e
        }
    }

    fun setPremiumStatus(email: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    usersCollection.document(docId).update("premium", true).await()
                    fetchUser(email)
                    Log.d("UserViewModel", "Set premium status for email=$email")
                } else {
                    Log.e("UserViewModel", "User not found for premium status: email=$email")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Set premium error for email=$email: ${e.message}", e)
            }
        }
    }

    fun simulateQrisPayment(orderId: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("UserViewModel", "Starting simulateQrisPayment for orderId: $orderId, amount: $amount")

                val user = user.value ?: throw Exception("User not found")
                val balance = user.balance
                Log.d("UserViewModel", "User balance: $balance")

                if (balance < amount) {
                    Log.e("UserViewModel", "Insufficient balance: $balance < $amount")
                    _simulationResult.postValue(SimulationResult.Failure("Insufficient balance: $balance < $amount"))
                    return@launch
                }

                val qrisSnapshot = qrisPaymentsCollection.document(orderId).get().await()
                if (qrisSnapshot.exists()) {
                    val qrisStatus = qrisSnapshot.getString("status") ?: "unknown"
                    val firestoreAmount = qrisSnapshot.getDouble("amount") ?: 0.0
                    Log.d("UserViewModel", "Firestore QRIS status for orderId $orderId: $qrisStatus, amount: $firestoreAmount")

                    if (qrisStatus != "pending") {
                        Log.e("UserViewModel", "Invalid QRIS status: $qrisStatus")
                        _simulationResult.postValue(SimulationResult.Failure("Firestore QRIS status invalid: $qrisStatus"))
                        return@launch
                    }

                    if (firestoreAmount == 0.0) {
                        Log.e("UserViewModel", "Invalid transaction amount in Firestore: $firestoreAmount")
                        _simulationResult.postValue(SimulationResult.Failure("Invalid transaction amount in Firestore"))
                        return@launch
                    }

                    if (Math.abs(firestoreAmount - amount) > 0.001) {
                        Log.e("UserViewModel", "Amount mismatch: Firestore=$firestoreAmount, Received=$amount")
                        _simulationResult.postValue(SimulationResult.Failure("Amount mismatch: expected $firestoreAmount, received $amount"))
                        return@launch
                    }

                    db.runTransaction { transaction ->
                        val userRef = usersCollection.document(user.id)
                        val paymentRef = qrisPaymentsCollection.document(orderId)
                        transaction.update(userRef, "balance", balance - amount)
                        transaction.update(paymentRef, "status", "settlement")
                    }.await()
                    Log.d("UserViewModel", "Balance updated to: ${balance - amount}, QRIS status set to settlement")

                    val transaksi = Transaksi(
                        userEmail = user.email,
                        type = "QRIS Payment",
                        recipient = "Merchant",
                        amount = amount,
                        timestamp = com.google.firebase.Timestamp.now(),
                        status = "Completed"
                    )
                    logTransaction(transaksi)

                    fetchUser(user.email)
                    Log.d("UserViewModel", "QRIS payment successful for orderId: $orderId, amount: $amount")
                    _simulationResult.postValue(SimulationResult.Success("Payment simulated successfully"))
                } else {
                    Log.e("UserViewModel", "No QRIS payment found in Firestore for orderId: $orderId")
                    _simulationResult.postValue(SimulationResult.Failure("QRIS payment not found in Firestore"))
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Simulation error for orderId=$orderId: ${e.message}", e)
                _simulationResult.postValue(SimulationResult.Failure("Simulation error: ${e.message}"))
            }
        }
    }
}

class UserViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}