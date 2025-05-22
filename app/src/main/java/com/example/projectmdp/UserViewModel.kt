package com.example.projectmdp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projectmdp.network.SimulateQrisRequest
import com.example.projectmdp.network.UpdateBalanceRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class PaymentResult {
    data class Success(val amount: Double) : PaymentResult()
    data class Failure(val message: String) : PaymentResult()
}

sealed class SimulationResult {
    data class Success(val message: String) : SimulationResult()
    data class Failure(val message: String) : SimulationResult()
}

sealed class TopUpResult {
    data class Success(val message: String) : TopUpResult()
    data class Failure(val message: String) : TopUpResult()
}

class UserViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val premiumCollection = db.collection("premium")
    private val transactionsCollection = db.collection("transactions")
    private val qrisPaymentsCollection = db.collection("qris_payments")
    private val simulatedOrders = mutableSetOf<String>()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> get() = _user

    private val _userEmail = MutableLiveData<String?>()
    val userEmail: LiveData<String?> get() = _userEmail

    private val _balance = MutableLiveData<Double>()
    val balance: LiveData<Double> get() = _balance

    private val _loginResult = MutableLiveData<Boolean?>()
    val loginResult: LiveData<Boolean?> get() = _loginResult

    private val _topUpResult = MutableLiveData<TopUpResult>()
    val topUpResult: LiveData<TopUpResult> get() = _topUpResult

    private val _paymentResult = MutableLiveData<PaymentResult>()
    val paymentResult: LiveData<PaymentResult> get() = _paymentResult

    private val _simulationResult = MutableLiveData<SimulationResult>()
    val simulationResult: LiveData<SimulationResult> get() = _simulationResult

    private val _premiumStatus = MutableLiveData<Premium?>()
    val premiumStatus: LiveData<Premium?> get() = _premiumStatus

    private val _updatePremiumError = MutableLiveData<String?>()
    val updatePremiumError: LiveData<String?> get() = _updatePremiumError

    init {
        createAdminUser()
    }

    fun setUserEmail(email: String) {
        Log.d("UserViewModel", "Setting user email: $email")
        _userEmail.value = email
        fetchUser(email)
        fetchPremiumStatus(email)
    }

    fun isOrderSimulated(orderId: String): Boolean {
        return simulatedOrders.contains(orderId)
    }

    private fun createAdminUser() {
        viewModelScope.launch {
            try {
                val snapshot =
                    usersCollection.whereEqualTo("email", "william@gmail.com").get().await()
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
                    fetchPremiumStatus("william@gmail.com")
                } else {
                    Log.d("UserViewModel", "Admin user already exists: william@gmail.com")
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
                Log.d(
                    "UserViewModel",
                    "Login query for email: $email, snapshot size: ${snapshot.size()}"
                )
                if (snapshot.isEmpty) {
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = snapshot.documents[0].toObject(User::class.java)
                    ?.copy(id = snapshot.documents[0].id)
                if (user?.password == password) {
                    _user.postValue(user)
                    _userEmail.postValue(email.lowercase())
                    _balance.postValue(user.balance)
                    _loginResult.postValue(true)
                    fetchPremiumStatus(email.lowercase())
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
                val normalizedEmail = email.lowercase()
                val snapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val user = doc.toObject(User::class.java)?.copy(id = doc.id)
                    if (user != null) {
                        _user.postValue(user)
                        _userEmail.postValue(normalizedEmail)
                        _balance.postValue(user.balance)
                        Log.d(
                            "UserViewModel",
                            "Fetched user: email=$normalizedEmail, balance=${user.balance}"
                        )
                    } else {
                        Log.e("UserViewModel", "Failed to parse user for email=$normalizedEmail")
                        _user.postValue(null)
                        _userEmail.postValue(null)
                    }
                } else {
                    Log.e("UserViewModel", "No user found for email=$normalizedEmail")
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

    fun fetchPremiumStatus(email: String) {
        viewModelScope.launch {
            try {
                val snapshot = premiumCollection.whereEqualTo("userEmail", email.lowercase()).get().await()
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val premiumData = doc.toObject(Premium::class.java)?.copy(id = doc.id)
                    _premiumStatus.postValue(premiumData)
                    Log.d("UserViewModel", "Fetched premium status: email=$email, premium=${premiumData?.premium}, requestPremium=${premiumData?.requestPremium}, ktpPhoto=${premiumData?.ktpPhoto}")
                } else {
                    val newPremium = Premium(userEmail = email.lowercase())
                    val docRef = premiumCollection.add(newPremium).await()
                    val createdPremium = newPremium.copy(id = docRef.id)
                    _premiumStatus.postValue(createdPremium)
                    Log.d("UserViewModel", "Created new premium entry for email=$email, docId=${docRef.id}")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Fetch premium status error for email=$email: ${e.message}", e)
                _premiumStatus.postValue(null)
            }
        }
    }

    fun register(email: String, fullName: String, password: String, pin: String, phone: String) {
        viewModelScope.launch {
            try {
                val normalizedEmail = email.lowercase()
                val snapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (!snapshot.isEmpty) {
                    Log.d("UserViewModel", "Email already exists: $normalizedEmail")
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = User(
                    id = "",
                    fullName = fullName,
                    email = normalizedEmail,
                    password = password,
                    pin = pin,
                    role = 0,
                    status = "active",
                    balance = 0.0
                )
                val docRef = usersCollection.add(user).await()
                Log.d(
                    "UserViewModel",
                    "User registered: $normalizedEmail, document ID: ${docRef.id}"
                )
                _loginResult.postValue(true)
                _userEmail.postValue(normalizedEmail)
                fetchUser(normalizedEmail)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Registration error for email=$email: ${e.message}", e)
                _loginResult.postValue(null)
            }
        }
    }

    fun updateUserProfile(email: String, fullName: String, newEmail: String, photoUrl: String = "", phone: String = "") {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Updating profile: email=$email, fullName=$fullName, newEmail=$newEmail, photoUrl=$photoUrl, phone=$phone")
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    Log.d("UserViewModel", "Found user document: docId=$docId")

                    val currentUser = snapshot.documents[0].toObject(User::class.java)
                    val updatedPhotoUrl = if (photoUrl.isEmpty() && currentUser?.photoUrl != null) currentUser.photoUrl else photoUrl
                    val updatedPhone = if (phone.isEmpty() && currentUser?.phone != null) currentUser.phone else phone

                    val updates = mapOf(
                        "fullName" to fullName,
                        "email" to newEmail.lowercase(),
                        "photoUrl" to updatedPhotoUrl,
                        "phone" to updatedPhone
                    )
                    Log.d("UserViewModel", "Firestore update map: $updates")

                    usersCollection.document(docId).update(updates).await()
                    Log.d("UserViewModel", "Firestore update successful for docId=$docId")
                    fetchUser(newEmail.lowercase())
                    Log.d("UserViewModel", "Updated profile: email=$email to newEmail=$newEmail")
                } else {
                    Log.e("UserViewModel", "User not found for profile update: email=$email")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Update profile error for email=$email: ${e.message}", e)
            }
        }
    }

    fun topUpBalance(email: String, amount: Double, orderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (amount <= 0) {
                    _topUpResult.postValue(TopUpResult.Failure("Invalid top-up amount"))
                    Log.e("UserViewModel", "Invalid top-up amount: $amount, orderId=$orderId")
                    return@launch
                }
                val normalizedEmail = email.lowercase()
                val userSnapshot =
                    usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (userSnapshot.isEmpty) {
                    _topUpResult.postValue(TopUpResult.Failure("User not found"))
                    Log.e("UserViewModel", "User not found for top-up: email=$normalizedEmail")
                    return@launch
                }
                val userDoc = userSnapshot.documents[0]
                val user = userDoc.toObject(User::class.java)
                if (user == null) {
                    _topUpResult.postValue(TopUpResult.Failure("Failed to parse user data"))
                    Log.e("UserViewModel", "Failed to parse user data for email=$normalizedEmail")
                    return@launch
                }
                Log.d(
                    "UserViewModel",
                    "Processing top-up: orderId=$orderId, amount=$amount, email=$normalizedEmail"
                )
                val transaksi = Transaksi(
                    userEmail = normalizedEmail,
                    type = "TopUp Saldo",
                    recipient = "System",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = orderId
                )
                transactionsCollection.add(transaksi).await()
                val newBalance = user.balance + amount
                usersCollection.document(userDoc.id).update("balance", newBalance).await()
                _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                _balance.postValue(newBalance)
                _topUpResult.postValue(TopUpResult.Success("Top-up successful"))
                Log.d("UserViewModel", "Top-up completed: orderId=$orderId, newBalance=$newBalance")
            } catch (e: Exception) {
                _topUpResult.postValue(TopUpResult.Failure("Top-up failed: ${e.message}"))
                Log.e("UserViewModel", "Top-up error: orderId=$orderId, error=${e.message}")
            }
        }
    }

    fun processQrisPayment(email: String, orderId: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (amount <= 0) {
                    _paymentResult.postValue(PaymentResult.Failure("Invalid payment amount"))
                    Log.e("UserViewModel", "Invalid payment amount: $amount, orderId=$orderId")
                    throw IllegalArgumentException("Invalid payment amount")
                }
                val normalizedEmail = email.lowercase()
                val userSnapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (userSnapshot.isEmpty) {
                    _paymentResult.postValue(PaymentResult.Failure("User not found"))
                    Log.e("UserViewModel", "User not found for payment: email=$normalizedEmail")
                    throw IllegalArgumentException("User not found")
                }
                val userDoc = userSnapshot.documents[0]
                val user = userDoc.toObject(User::class.java)
                if (user == null) {
                    _paymentResult.postValue(PaymentResult.Failure("Failed to parse user data"))
                    Log.e("UserViewModel", "Failed to parse user data for email=$normalizedEmail")
                    throw IllegalArgumentException("Failed to parse user data")
                }
                if (user.balance < amount) {
                    _paymentResult.postValue(PaymentResult.Failure("Insufficient balance"))
                    Log.e("UserViewModel", "Insufficient balance: balance=${user.balance}, amount=$amount")
                    throw IllegalArgumentException("Insufficient balance")
                }
                Log.d(
                    "UserViewModel",
                    "Processing QRIS payment: orderId=$orderId, amount=$amount, email=$normalizedEmail"
                )

                val newBalance = user.balance - amount
                usersCollection.document(userDoc.id).update("balance", newBalance).await()

                val transaksi = Transaksi(
                    userEmail = normalizedEmail,
                    type = "QRIS Payment",
                    recipient = "Merchant",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = orderId
                )
                logTransaction(transaksi)

                val response = try {
                    App.api.updateBalance(
                        UpdateBalanceRequest(
                            user_email = normalizedEmail,
                            amount = -amount.toInt(),
                            order_id = orderId
                        )
                    )
                } catch (e: HttpException) {
                    Log.e("UserViewModel", "Backend update failed with HTTP ${e.code()}: ${e.message()}")
                    usersCollection.document(userDoc.id).update("balance", user.balance).await()
                    _paymentResult.postValue(PaymentResult.Failure("Backend update failed: HTTP ${e.code()}"))
                    return@launch
                } catch (e: Exception) {
                    Log.e("UserViewModel", "Unexpected backend error: ${e.message}", e)
                    usersCollection.document(userDoc.id).update("balance", user.balance).await()
                    _paymentResult.postValue(PaymentResult.Failure("Backend update failed: ${e.message}"))
                    return@launch
                }

                if (!response.success) {
                    usersCollection.document(userDoc.id).update("balance", user.balance).await()
                    _paymentResult.postValue(PaymentResult.Failure("Backend update failed: ${response.message}"))
                    Log.e("UserViewModel", "Backend balance update failed: ${response.message}")
                    return@launch
                }

                _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                _balance.postValue(newBalance)
                _paymentResult.postValue(PaymentResult.Success(amount))
                Log.d("UserViewModel", "QRIS payment completed: orderId=$orderId, newBalance=$newBalance")

                fetchUser(normalizedEmail)
            } catch (e: Exception) {
                _paymentResult.postValue(PaymentResult.Failure("Payment failed: ${e.message}"))
                Log.e("UserViewModel", "QRIS payment error: orderId=$orderId, error=${e.message}", e)
            }
        }
    }

    fun transfer(fromEmail: String, toEmail: String, amount: Double, onResult: (String?) -> Unit) {
        Log.d("UserViewModel", "Starting transfer: from=$fromEmail, to=$toEmail, amount=$amount")
        viewModelScope.launch {
            try {
                val fromSnapshot = usersCollection.whereEqualTo("email", fromEmail.lowercase()).get().await()
                val toSnapshot = usersCollection.whereEqualTo("email", toEmail.lowercase()).get().await()

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
                    userEmail = fromEmail.lowercase(),
                    type = "Transfer",
                    recipient = toEmail.lowercase(),
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )
                logTransaction(transaksi)

                fetchUser(fromEmail)
                Log.d(
                    "UserViewModel",
                    "Transfer successful: New sender balance=${_user.value?.balance}"
                )
                onResult(null)
            } catch (e: Exception) {
                Log.e(
                    "UserViewModel",
                    "Transfer failed from $fromEmail to $toEmail: ${e.message}",
                    e
                )
                onResult(e.message)
            }
        }
    }

    fun transferToBank(
        userEmail: String,
        bankAccount: String,
        amount: Double,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userSnapshot = usersCollection.whereEqualTo("email", userEmail.lowercase()).get().await()
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
                    userEmail = userEmail.lowercase(),
                    type = "Bank Transfer",
                    recipient = bankAccount,
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )
                logTransaction(transaksi)

                fetchUser(userEmail)
                Log.d(
                    "UserViewModel",
                    "Bank transfer successful: New balance=${_user.value?.balance}"
                )
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
                val snapshot = premiumCollection.whereEqualTo("userEmail", email.lowercase()).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    premiumCollection.document(docId).update("premium", true).await()
                    fetchPremiumStatus(email.lowercase())
                    Log.d("UserViewModel", "Set premium status for email=$email")
                } else {
                    Log.e("UserViewModel", "Premium entry not found for email=$email")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Set premium error for email=$email: ${e.message}", e)
            }
        }
    }

    fun simulateQrisPayment(orderId: String, amount: Double, userEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (simulatedOrders.contains(orderId)) {
                    Log.d("UserViewModel", "Skipping duplicate simulation for orderId=$orderId")
                    _simulationResult.postValue(SimulationResult.Success("Payment already processed"))
                    return@launch
                }

                Log.d(
                    "UserViewModel",
                    "Starting simulateQrisPayment for orderId: $orderId, amount: $amount, userEmail: $userEmail"
                )

                if (amount <= 0) {
                    _simulationResult.postValue(SimulationResult.Failure("Invalid payment amount"))
                    Log.e("UserViewModel", "Invalid QRIS amount: $amount, orderId=$orderId")
                    return@launch
                }

                val verifyResponse = App.api.verifyQrisPayment(orderId)
                if (!verifyResponse.success) {
                    throw Exception("Payment verification failed: ${verifyResponse.message}")
                }

                Log.d("UserViewModel", "Simulating QRIS payment: orderId=$orderId, amount=$amount")
                val request = SimulateQrisRequest(order_id = orderId, amount = amount)
                val response = App.api.simulateQrisPayment(request)

                if (response.success && response.status == "settlement") {
                    simulatedOrders.add(orderId)
                    _simulationResult.postValue(SimulationResult.Success(response.message ?: "Payment successful"))
                    Log.d("UserViewModel", "QRIS payment simulated: orderId=$orderId")
                    fetchUser(userEmail)
                } else {
                    _simulationResult.postValue(SimulationResult.Failure(response.message ?: "Simulation failed"))
                    Log.e("UserViewModel", "QRIS simulation failed: orderId=$orderId, message=${response.message}")
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No response body"
                val message = when (e.code()) {
                    400 -> "Invalid QRIS request: $errorBody"
                    404 -> "QRIS order not found: $orderId"
                    else -> "HTTP error ${e.code()}: $errorBody"
                }
                _simulationResult.postValue(SimulationResult.Failure(message))
                Log.e("UserViewModel", "HTTP error in QRIS simulation: orderId=$orderId, code=${e.code()}, error=$errorBody")
            } catch (e: Exception) {
                _simulationResult.postValue(SimulationResult.Failure("Payment failed: ${e.message}"))
                Log.e("UserViewModel", "QRIS simulation error: orderId=$orderId, error=${e.message}")
            }
        }
    }

    fun clearUpdatePremiumError() {
        _updatePremiumError.postValue(null)
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