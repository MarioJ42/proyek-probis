package com.example.projectmdp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> get() = _user

    private val _balance = MutableLiveData<Double>()
    val balance: LiveData<Double> get() = _balance

    private val _loginResult = MutableLiveData<Boolean?>()
    val loginResult: LiveData<Boolean?> get() = _loginResult

    private val _topUpResult = MutableLiveData<Boolean>()
    val topUpResult: LiveData<Boolean> get() = _topUpResult

    init {
        // Create admin user if not exists
        createAdminUser()
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
                        password = "william",
                        pin = "123456",
                        role = 1,
                        status = "active"
                    )
                    usersCollection.add(adminUser).await()
                    Log.d("UserViewModel", "Admin user created: william@gmail.com")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Failed to create admin user: ${e.message}")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (snapshot.isEmpty) {
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = snapshot.documents[0].toObject(User::class.java)
                if (user?.password == password) {
                    _user.postValue(user)
                    _balance.postValue(user.balance)
                    _loginResult.postValue(true)
                } else {
                    _loginResult.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Login error: ${e.message}")
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
                    val user = snapshot.documents[0].toObject(User::class.java)?.copy(id = snapshot.documents[0].id)
                    _user.postValue(user)
                    user?.let {
                        _balance.postValue(it.balance)
                        Log.d("UserViewModel", "fetchUser: Email = $email, Balance = ${it.balance}")
                    }
                } else {
                    Log.e("UserViewModel", "fetchUser: User not found for email = $email")
                    _user.postValue(null)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "fetchUser error: ${e.message}")
                _user.postValue(null)
            }
        }
    }

    fun register(email: String, fullName: String, password: String, pin: String, phone: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    Log.d("UserViewModel", "Email already exists: $email")
                    _loginResult.postValue(false)
                    return@launch
                }
                val user = User(
                    id = "",
                    fullName = fullName,
                    email = email,
                    password = password,
                    pin = pin,
                    role = 0,
                    status = "active",
                    phone = phone
                )
                val docRef = usersCollection.add(user).await()
                Log.d("UserViewModel", "User registered: $email")
                _loginResult.postValue(true)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Registration error: ${e.message}")
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
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Update profile error: ${e.message}")
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
                    _topUpResult.postValue(true)
                } else {
                    Log.e("UserViewModel", "Top-up failed: User not found for email = $email")
                    _topUpResult.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Top-up error: ${e.message}")
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
                    throw IllegalArgumentException("Sender or recipient not found")
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
                Log.d("UserViewModel", "Transfer successful: New sender balance = ${_user.value?.balance}")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Transfer failed: ${e.message}")
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
                Log.d("UserViewModel", "Bank transfer successful: New balance = ${_user.value?.balance}")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Bank transfer failed: ${e.message}")
                onResult(e.message)
            }
        }
    }

    fun logTransaction(transaksi: Transaksi) {
        val normalizedTransaksi = transaksi.copy(userEmail = transaksi.userEmail.lowercase())
        db.collection("transactions")
            .add(normalizedTransaksi)
            .addOnSuccessListener { Log.d("UserViewModel", "Transaction logged: ${normalizedTransaksi}") }
            .addOnFailureListener { e -> Log.e("UserViewModel", "Failed to log transaction: ${e.message}") }
    }

    fun setPremiumStatus(email: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    usersCollection.document(docId).update("premium", true).await()
                    fetchUser(email)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Set premium error: ${e.message}")
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