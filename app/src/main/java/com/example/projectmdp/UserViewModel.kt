package com.example.projectmdp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

class UserViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val premiumCollection = db.collection("premium")
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
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
                Log.d("UserViewModel", "Login query for email: $email, snapshot size: ${snapshot.size()}")
                if (snapshot.isEmpty) {
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = snapshot.documents[0].toObject(User::class.java)?.copy(id = snapshot.documents[0].id)
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
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val user = doc.toObject(User::class.java)?.copy(id = doc.id)
                    if (user != null) {
                        _user.postValue(user)
                        _userEmail.postValue(email.lowercase())
                        _balance.postValue(user.balance)
                        Log.d("UserViewModel", "Fetched user: email=$email, balance=${user.balance}, photoUrl=${user.photoUrl}")
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
                fetchPremiumStatus(email.lowercase())
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

    fun updatePremiumStatus(email: String, ktpPhoto: String, requestPremium: Boolean) {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Updating premium status: email=$email, ktpPhoto=$ktpPhoto, requestPremium=$requestPremium")

                // Validate inputs
                if (email.isBlank()) {
                    Log.e("UserViewModel", "Email cannot be blank")
                    _updatePremiumError.postValue("Email cannot be blank")
                    return@launch
                }
                if (ktpPhoto.isBlank()) {
                    Log.e("UserViewModel", "KTP photo URL cannot be blank")
                    _updatePremiumError.postValue("KTP photo URL cannot be blank")
                    return@launch
                }

                val snapshot = premiumCollection.whereEqualTo("userEmail", email.lowercase()).get().await()
                Log.d("UserViewModel", "Premium snapshot size for email=$email: ${snapshot.size()}")

                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    val currentData = snapshot.documents[0].toObject(Premium::class.java)
                    Log.d("UserViewModel", "Current premium data: $currentData")

                    val updates = hashMapOf<String, Any>(
                        "ktpPhoto" to ktpPhoto,
                        "requestPremium" to requestPremium,
                        "userEmail" to email.lowercase() // Ensure consistency
                    )
                    Log.d("UserViewModel", "Updating Firestore with: $updates for docId=$docId")
                    premiumCollection.document(docId).update(updates).await()
                    Log.d("UserViewModel", "Firestore update successful for email=$email, docId=$docId")
                    fetchPremiumStatus(email.lowercase())
                } else {
                    Log.d("UserViewModel", "No existing premium entry found, creating new one for email=$email")
                    val newPremium = Premium(
                        userEmail = email.lowercase(),
                        ktpPhoto = ktpPhoto,
                        requestPremium = requestPremium
                    )
                    val docRef = premiumCollection.add(newPremium).await()
                    Log.d("UserViewModel", "Created new premium entry for email=$email, docId=${docRef.id}")
                    fetchPremiumStatus(email.lowercase())
                }
                _updatePremiumError.postValue(null) // Clear any previous errors
            } catch (e: Exception) {
                Log.e("UserViewModel", "Update premium status error for email=$email: ${e.message}", e)
                _updatePremiumError.postValue("Failed to update premium status: ${e.message}")
                throw e // Rethrow to catch in GetPremiumFragment
            }
        }
    }

    fun topUp(email: String, amount: Double) {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Starting top-up: email=$email, amount=$amount")
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
                Log.d("UserViewModel", "User snapshot size for email=$email: ${snapshot.size()}")
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    val currentBalance = snapshot.documents[0].toObject(User::class.java)?.balance ?: 0.0
                    Log.d("UserViewModel", "Current balance for email=$email: $currentBalance")
                    val newBalance = currentBalance + amount
                    Log.d("UserViewModel", "New balance will be: $newBalance")
                    usersCollection.document(docId).update("balance", newBalance).await()
                    Log.d("UserViewModel", "Balance updated in Firestore for email=$email, docId=$docId")

                    val transaksi = Transaksi(
                        userEmail = email.lowercase(),
                        type = "TopUp Saldo",
                        recipient = "System",
                        amount = amount,
                        timestamp = com.google.firebase.Timestamp.now(),
                        status = "Completed"
                    )
                    logTransaction(transaksi)

                    fetchUser(email.lowercase())
                    Log.d("UserViewModel", "Top-up successful: email=$email, amount=$amount, new balance=${_user.value?.balance}")
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

                fetchUser(fromEmail.lowercase())
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

                fetchUser(userEmail.lowercase())
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

    fun simulateQrisPayment(orderId: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("UserViewModel", "Starting simulateQrisPayment for orderId: $orderId, amount: $amount")

                val user = user.value ?: throw Exception("User not found")
                val balance = user.balance
                if (balance < amount) {
                    _simulationResult.postValue(SimulationResult.Failure("Insufficient balance"))
                    return@launch
                }

                val paymentData = hashMapOf(
                    "orderId" to orderId,
                    "userEmail" to user.email.lowercase(),
                    "amount" to amount,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "pending"
                )
                qrisPaymentsCollection.add(paymentData).await()

                withContext(Dispatchers.Default) {
                    kotlinx.coroutines.delay(3000)
                }

                val snapshot = qrisPaymentsCollection.whereEqualTo("orderId", orderId).get().await()
                if (snapshot.isEmpty) {
                    _simulationResult.postValue(SimulationResult.Failure("Payment not found"))
                    return@launch
                }

                val doc = snapshot.documents[0]
                qrisPaymentsCollection.document(doc.id).update("status", "completed").await()

                val userSnapshot = usersCollection.whereEqualTo("email", user.email.lowercase()).get().await()
                if (userSnapshot.isEmpty) {
                    _simulationResult.postValue(SimulationResult.Failure("User not found"))
                    return@launch
                }

                val userDoc = userSnapshot.documents[0]
                usersCollection.document(userDoc.id).update("balance", balance - amount).await()

                val transaksi = Transaksi(
                    userEmail = user.email.lowercase(),
                    type = "QRIS Payment",
                    recipient = "Merchant",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )
                logTransaction(transaksi)

                fetchUser(user.email.lowercase())
                _simulationResult.postValue(SimulationResult.Success("Payment completed successfully"))
                Log.d("UserViewModel", "QRIS payment simulation successful for orderId: $orderId")
            } catch (e: Exception) {
                Log.e("UserViewModel", "QRIS payment simulation failed for orderId: $orderId, error: ${e.message}", e)
                _simulationResult.postValue(SimulationResult.Failure("Payment failed: ${e.message}"))
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