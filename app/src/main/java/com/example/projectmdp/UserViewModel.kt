package com.example.projectmdp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projectmdp.network.SimulateQrisRequest
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Calendar
import java.util.UUID

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

sealed class DepositResult {
    data class Success(val message: String) : DepositResult()
    data class Failure(val message: String) : DepositResult()
}

data class Deposit(
    val userEmail: String = "",
    val amount: Double = 0.0,
    val interestRate: Double = 0.0,
    val tenorMonths: Int = 0,
    val isReinvest: Boolean = false,
    val startDate: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val nextInterestDate: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val status: String = "Active",
    val orderId: String = ""
)

class UserViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val premiumCollection = db.collection("premium")
    private val transactionsCollection = db.collection("transactions")
    private val qrisPaymentsCollection = db.collection("qris_payments")
    private val depositsCollection = db.collection("deposits")
    private val bankAccountsCollection = db.collection("bank_accounts")
    private val referralCodesCollection = db.collection("referralCodes")
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

    private val _depositResult = MutableLiveData<DepositResult>()
    val depositResult: LiveData<DepositResult> get() = _depositResult

    private val _bankAccounts = MutableLiveData<List<BankAccount>>()
    val bankAccounts: LiveData<List<BankAccount>> get() = _bankAccounts

    private val _registrationResult = MutableLiveData<Result<String>?>()
    val registrationResult: LiveData<Result<String>?> get() = _registrationResult

    private val _referralCount = MutableLiveData<Int>()
    val referralCount: LiveData<Int> get() = _referralCount

    private var userBalanceListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        createAdminUser()
    }

    fun clearRegistrationResult() {
        _registrationResult.postValue(null)
    }

    fun fetchReferralUsageCount(referralCode: String){
        if (referralCode.isEmpty()) {
            _referralCount.postValue(0)
            return
        }
        viewModelScope.launch {
            try{
                val snapshot = usersCollection
                    .whereEqualTo("redeemedReferralCode", referralCode)
                    .get()
                    .await()

                val count = snapshot.size()
                _referralCount.postValue(count)
                Log.d("UserViewModel", "Referral code '$referralCode' has been used $count time(s).")
            } catch (e:Exception){
                Log.e("UserViewModel", "Error fetching referral for code $referralCode: ${e.message}", e)
            }
        }
    }

    fun setUserEmail(email: String) {
        Log.d("UserViewModel", "Setting user email: $email")
        _userEmail.postValue(email.lowercase())
        fetchUser(email.lowercase())
        fetchPremiumStatus(email.lowercase())
        setupRealtimeBalanceListener(email.lowercase())
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
                    setupRealtimeBalanceListener("william@gmail.com")
                } else {
                    Log.d("UserViewModel", "Admin user already exists: william@gmail.com")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Failed to create admin user: ${e.message}", e)
            }
        }
    }

    fun saverememberme(content: RememberUser) {
        viewModelScope.launch {
            MyApplication.db.RememberedUserDAO().insertUser(content)
        }
    }

    fun getRememberMe(onResult: (RememberUser?) -> Unit) {
        viewModelScope.launch {
            val user = MyApplication.db.RememberedUserDAO().getUser(0)
            onResult(user)
        }
    }

    fun clearRememberMe() {
        viewModelScope.launch {
            MyApplication.db.RememberedUserDAO().clearRememberedUser()
            userBalanceListener?.remove()
            userBalanceListener = null
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("email", email.lowercase()).get().await()
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
                    setupRealtimeBalanceListener(email.lowercase())
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
        if (_user.value?.email == email.lowercase() && _user.value != null) {
            Log.d("UserViewModel", "User already fetched for email=$email, using cached data")
            return
        }
        viewModelScope.launch {
            try {
                val normalizedEmail = email.lowercase()
                val snapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val user = doc.toObject(User::class.java)?.copy(id = doc.id)
                    if (user != null) {
                        _user.postValue(user)
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

    private fun setupRealtimeBalanceListener(email: String) {
        userBalanceListener?.remove()
        userBalanceListener = null

        val normalizedEmail = email.lowercase()
        userBalanceListener = usersCollection.whereEqualTo("email", normalizedEmail)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("UserViewModel", "Listen failed for balance.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val userDoc = snapshots.documents[0]
                    val user = userDoc.toObject(User::class.java)?.copy(id = userDoc.id)
                    user?.let {
                        _balance.postValue(it.balance)
                        _user.postValue(it)
                        Log.d("UserViewModel", "Real-time balance update for ${it.email}: ${it.balance}")
                    }
                } else {
                    Log.d("UserViewModel", "No user document found for real-time balance listener for email: $normalizedEmail")
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        userBalanceListener?.remove()
        userBalanceListener = null
        Log.d("UserViewModel", "Real-time balance listener removed on ViewModel cleared.")
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

    fun register(email: String, fullName: String, password: String, pin: String, phone: String, referralCode: String) {
        viewModelScope.launch {
            try {
                val normalizedEmail = email.lowercase()
                val snapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (!snapshot.isEmpty) {
                    Log.w("UserViewModel", "Registration failed: Email already exists.")
                    _loginResult.postValue(false)
                    return@launch
                }

                if (referralCode.isNotBlank()) {
                    performRegistrationWithReward(fullName, normalizedEmail, password, pin, phone, referralCode)
                } else {
                    performNormalRegistration(fullName, normalizedEmail, password, pin, phone)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "An unexpected error occurred during registration: ${e.message}", e)
                _loginResult.postValue(null)
            }
        }
    }

    private suspend fun performNormalRegistration(fullName: String, email: String, password: String, pin: String, phone: String) {
        try {
            val uniqueCode = generateUniqueReferralCode()
            val userDocRef = usersCollection.document()
            val user = User(
                id = userDocRef.id,
                fullName = fullName, email = email, password = password, pin = pin, phone = phone,
                referralCode = uniqueCode
            )
            val referralCodeRef = db.collection("referralCodes").document(uniqueCode)
            db.runBatch { batch ->
                batch.set(userDocRef, user)
                batch.set(referralCodeRef, mapOf("userId" to userDocRef.id))
            }.await()
            Log.d("UserViewModel", "Normal registration successful for $email")
            _loginResult.postValue(true)
            setupRealtimeBalanceListener(email)
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error in performNormalRegistration: ${e.message}", e)
            _loginResult.postValue(null)
        }
    }

    private suspend fun performRegistrationWithReward(fullName: String, email: String, password: String, pin: String, phone: String, redeemedCode: String) {
        try {
            val referrerCodeDoc = db.collection("referralCodes").document(redeemedCode).get().await()
            val referrerId = referrerCodeDoc.getString("userId")

            if (referrerCodeDoc.exists() && referrerId != null) {
                val newUniqueCode = generateUniqueReferralCode()

                // Menjalankan semua operasi database dalam satu transaction
                db.runTransaction { transaction ->
                    val referrerRef = usersCollection.document(referrerId)
                    val referrerSnapshot = transaction.get(referrerRef)
                    // Ambil data lengkap dari referrer untuk mendapatkan emailnya
                    val referrerUser = referrerSnapshot.toObject(User::class.java)

                    // Pastikan data referrer ada sebelum melanjutkan
                    if (referrerUser != null) {
                        val currentBalance = referrerUser.balance
                        // 1. Update saldo si referrer
                        transaction.update(referrerRef, "balance", currentBalance + 10000.0)

                        // 2. Buat data untuk user baru yang mendaftar
                        val newUserRef = usersCollection.document()
                        val newUser = User(
                            id = newUserRef.id,
                            fullName = fullName, email = email, password = password, pin = pin, phone = phone,
                            referralCode = newUniqueCode,
                            redeemedReferralCode = redeemedCode
                        )
                        // Simpan data user baru
                        transaction.set(newUserRef, newUser)

                        // 3. Buat kode referral untuk user baru
                        val referralCodeRef = db.collection("referralCodes").document(newUniqueCode)
                        transaction.set(referralCodeRef, mapOf("userId" to newUserRef.id))

                        // --- INI KODE TAMBAHAN YANG BARU ---
                        // 4. Buat dan catat transaksi bonus untuk si referrer
                        val bonusTransaction = Transaksi(
                            userEmail = referrerUser.email, // Dicatat atas nama PEMBERI referal
                            type = "Referral Bonus",
                            recipient = "From new user: $email", // Deskripsi asal bonus
                            amount = 10000.0,
                            timestamp = com.google.firebase.Timestamp.now(),
                            status = "Completed",
                            orderId = "REF-${newUserRef.id}" // ID unik untuk transaksi ini
                        )
                        // Simpan transaksi bonus ke koleksi 'transactions'
                        transaction.set(transactionsCollection.document(), bonusTransaction)
                        // --- AKHIR DARI KODE TAMBAHAN ---
                    }
                }.await() // Menunggu hingga semua operasi di dalam transaction selesai

                Log.d("UserViewModel", "Registration with referral successful for $email")
                _loginResult.postValue(true)
                setupRealtimeBalanceListener(email)
            } else {
                Log.w("UserViewModel", "Invalid referral code '$redeemedCode'. Proceeding with normal registration.")
                performNormalRegistration(fullName, email, password, pin, phone)
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error in performRegistrationWithReward: ${e.message}", e)
            _loginResult.postValue(null)
        }
    }

    private suspend fun generateUniqueReferralCode(): String {
        val referralCodesCollection = db.collection("referralCodes")
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        var newCode: String
        var isUnique: Boolean
        do {
            newCode = (1..6).map { chars.random() }.joinToString("")
            val doc = referralCodesCollection.document(newCode).get().await()
            isUnique = !doc.exists()
        } while (!isUnique)
        return newCode
    }

    fun updateUserProfile(email: String, fullName: String, newEmail: String, photoUrl: String = "", phone: String = "") {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Updating profile: email=$email, fullName=$fullName, newEmail=$newEmail, photoUrl=$photoUrl, phone=$phone")
                val normalizedEmail = email.lowercase()
                val normalizedNewEmail = newEmail.lowercase()
                val snapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()

                if (snapshot.isEmpty) {
                    Log.e("UserViewModel", "User not found for profile update: email=$normalizedEmail")
                    return@launch
                }

                val docId = snapshot.documents[0].id
                Log.d("UserViewModel", "Found user document: docId=$docId")

                val currentUser = snapshot.documents[0].toObject(User::class.java)
                val updatedPhotoUrl = if (photoUrl.isEmpty() && currentUser?.photoUrl != null) currentUser.photoUrl else photoUrl
                val updatedPhone = if (phone.isEmpty() && currentUser?.phone != null) currentUser.phone else phone

                if (normalizedNewEmail != normalizedEmail) {
                    val emailSnapshot = usersCollection.whereEqualTo("email", normalizedNewEmail).get().await()
                    if (!emailSnapshot.isEmpty && emailSnapshot.documents[0].id != docId) {
                        Log.e("UserViewModel", "New email already exists: $normalizedNewEmail")
                        throw IllegalArgumentException("Email already in use")
                    }
                }

                val updates = mutableMapOf<String, Any>(
                    "fullName" to fullName.trim(),
                    "email" to normalizedNewEmail,
                    "photoUrl" to updatedPhotoUrl,
                    "phone" to updatedPhone.trim()
                ).filterValues { it.toString().isNotEmpty() }

                Log.d("UserViewModel", "Firestore update map: $updates")

                usersCollection.document(docId).update(updates).await()
                Log.d("UserViewModel", "Firestore update successful for docId=$docId")

                _userEmail.postValue(normalizedNewEmail)
                setupRealtimeBalanceListener(normalizedNewEmail)
                Log.d("UserViewModel", "Updated profile: email=$email to newEmail=$normalizedNewEmail")
            } catch (e: Exception) {
                Log.e("UserViewModel", "Update profile error for email=$email: ${e.message}", e)
            }
        }
    }

    fun topUpBalance(email: String, amount: Double, orderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (amount == 0.0) {
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
                if (amount < 0 && user.balance + amount < 0) {
                    _topUpResult.postValue(TopUpResult.Failure("Insufficient balance"))
                    Log.e("UserViewModel", "Insufficient balance: balance=${user.balance}, amount=$amount")
                    return@launch
                }
                Log.d(
                    "UserViewModel",
                    "Processing top-up: orderId=$orderId, amount=$amount, email=$normalizedEmail"
                )
                val transaksi = Transaksi(
                    userEmail = normalizedEmail,
                    type = if (amount > 0) "TopUp Saldo" else "Premium Deduction",
                    recipient = "System",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = orderId
                )
                db.runTransaction { transaction ->
                    val newBalance = user.balance + amount
                    transaction.update(userDoc.reference, "balance", newBalance)
                    val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                    transaction.set(transactionDocRef, transaksi)
                }.await()
                val newBalance = user.balance + amount
                _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                _topUpResult.postValue(TopUpResult.Success("Balance update successful"))
                Log.d("UserViewModel", "Top-up completed: orderId=$orderId, newBalance=$newBalance")
            } catch (e: Exception) {
                _topUpResult.postValue(TopUpResult.Failure("Balance update failed: ${e.message}"))
                Log.e("UserViewModel", "Top-up error: orderId=$orderId, error=${e.message}")
            }
        }
    }

    fun processQrisPayment(email: String, orderId: String, amount: Double) {
        if (isOrderSimulated(orderId)) {
            _paymentResult.postValue(PaymentResult.Failure("Payment already processed"))
            Log.d("UserViewModel", "Skipping duplicate QRIS payment for orderId=$orderId")
            return
        }
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
                val transaksi = Transaksi(
                    userEmail = normalizedEmail,
                    type = "QRIS Payment",
                    recipient = "Merchant",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = orderId
                )
                db.runTransaction { transaction ->
                    transaction.update(userDoc.reference, "balance", newBalance)
                    val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                    transaction.set(transactionDocRef, transaksi)
                }.await()

                simulatedOrders.add(orderId)
                _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                _paymentResult.postValue(PaymentResult.Success(amount))
                Log.d("UserViewModel", "QRIS payment completed: orderId=$orderId, newBalance=$newBalance")
            } catch (e: Exception) {
                _paymentResult.postValue(PaymentResult.Failure("Payment failed: ${e.message}"))
                Log.e("UserViewModel", "QRIS payment error: orderId=$orderId, error=${e.message}", e)
            }
        }
    }

    fun transfer(fromEmail: String, toIdentifier: String, amount: Double, identifierType: String, onResult: (String?) -> Unit) {
        Log.d("UserViewModel", "Starting transfer: from=$fromEmail, to=$toIdentifier, amount=$amount, type=$identifierType")
        viewModelScope.launch {
            try {
                val query = when (identifierType) {
                    "Email" -> usersCollection.whereEqualTo("email", toIdentifier.lowercase())
                    "Phone Number" -> usersCollection.whereEqualTo("phone", toIdentifier)
                    else -> throw IllegalArgumentException("Invalid identifier type")
                }
                val toSnapshot = query.get().await()

                val fromSnapshot = usersCollection.whereEqualTo("email", fromEmail.lowercase()).get().await()
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

                val timestamp = com.google.firebase.Timestamp.now()

                // Transaksi untuk pengirim
                val senderTransaction = Transaksi(
                    userEmail = fromEmail.lowercase(),
                    type = "Transfer",
                    recipient = toIdentifier.lowercase(),
                    amount = amount,
                    timestamp = timestamp,
                    status = "Completed"
                )

                // Transaksi untuk penerima
                val receiverTransaction = Transaksi(
                    userEmail = recipient.email.lowercase(),
                    type = "Receive",
                    recipient = "MySelf",
                    amount = amount,
                    timestamp = timestamp,
                    status = "Completed"
                )

                db.runTransaction { transaction ->
                    transaction.update(fromDoc.reference, "balance", sender.balance - amount)
                    transaction.update(toDoc.reference, "balance", recipient.balance + amount)

                    val senderRef = transactionsCollection.document(UUID.randomUUID().toString())
                    val receiverRef = transactionsCollection.document(UUID.randomUUID().toString())

                    transaction.set(senderRef, senderTransaction)
                    transaction.set(receiverRef, receiverTransaction)
                }.await()

                Log.d("UserViewModel", "Transfer successful and both transactions recorded")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Transfer failed: ${e.message}", e)
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

                val transaksi = Transaksi(
                    userEmail = userEmail.lowercase(),
                    type = "Bank Transfer",
                    recipient = bankAccount,
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed"
                )

                db.runTransaction { transaction ->
                    transaction.update(userDoc.reference, "balance", user.balance - amount)
                    val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                    transaction.set(transactionDocRef, transaksi)
                }.await()

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
        val normalizedTransaction = transaksi.copy(userEmail = transaksi.userEmail.lowercase())
        try {
            val docRef = transactionsCollection.document(UUID.randomUUID().toString())
            docRef.set(normalizedTransaction).await()
            Log.d("UserViewModel", "Transaction logged: ${docRef.id}, $normalizedTransaction")
        } catch (e: Exception) {
            Log.e("UserViewModel", "Failed to log transaction for email=${normalizedTransaction.userEmail}: ${e.message}", e)
            throw e
        }
    }

    fun setPremiumStatus(email: String) {
        viewModelScope.launch {
            try {
                val snapshot = premiumCollection.whereEqualTo("userEmail", email.lowercase()).get().await()
                if (!snapshot.isEmpty()) {
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

    fun updatePremiumStatus(email: String, ktpUrl: String, requestPremium: Boolean) {
        viewModelScope.launch {
            try {
                val normalizedEmail = email.lowercase()
                val snapshot = premiumCollection.whereEqualTo("userEmail", normalizedEmail).get().await()
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    premiumCollection.document(docId).update(
                        mapOf(
                            "ktpPhoto" to ktpUrl,
                            "requestPremium" to requestPremium,
                            "premium" to false
                        )
                    ).await()
                    Log.d("UserViewModel", "Updated premium request for email=$normalizedEmail, ktpUrl=$ktpUrl, requestPremium=$requestPremium")
                    fetchPremiumStatus(normalizedEmail)
                } else {
                    val newPremium = Premium(
                        userEmail = normalizedEmail,
                        ktpPhoto = ktpUrl,
                        requestPremium = requestPremium,
                        premium = false
                    )
                    val docRef = premiumCollection.add(newPremium).await()
                    Log.d("UserViewModel", "Created premium request for email=$normalizedEmail, docId=${docRef.id}")
                    fetchPremiumStatus(normalizedEmail)
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error updating premium status for email=$email: ${e.message}", e)
                _updatePremiumError.postValue("Failed to update premium status: ${e.message}")
            }
        }
    }

    fun simulateQrisPayment(orderId: String, amount: Double, userEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (simulatedOrders.contains(orderId)) {
                    _simulationResult.postValue(SimulationResult.Success("Payment already processed"))
                    Log.d("UserViewModel", "Skipping duplicate simulation for orderId=$orderId")
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
                } else {
                    _simulationResult.postValue(SimulationResult.Failure(response.message ?: "Simulation failed"))
                    Log.e("UserViewModel", "QRIS simulation failed: orderId=$orderId, message=${response.message}")
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Unknown error"
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

    fun createOrUpdateDeposit(email: String, amount: Double, tenorMonths: Int, isReinvest: Boolean, orderId: String, customInterestRate: Double? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (amount < 100_000) {
                    _depositResult.postValue(DepositResult.Failure("Minimum deposit is Rp100,000"))
                    Log.e("UserViewModel", "Invalid deposit amount: $amount, orderId=$orderId")
                    return@launch
                }
                val normalizedEmail = email.lowercase()
                val userSnapshot = usersCollection.whereEqualTo("email", normalizedEmail).get().await()
                if (userSnapshot.isEmpty) {
                    _depositResult.postValue(DepositResult.Failure("User not found"))
                    Log.e("UserViewModel", "User not found for deposit: email=$normalizedEmail")
                    return@launch
                }
                val userDoc = userSnapshot.documents[0]
                val user = userDoc.toObject(User::class.java)
                if (user == null) {
                    _depositResult.postValue(DepositResult.Failure("Failed to parse user data"))
                    Log.e("UserViewModel", "Failed to parse user data for email=$normalizedEmail")
                    return@launch
                }
                if (user.balance < amount) {
                    _depositResult.postValue(DepositResult.Failure("Insufficient balance"))
                    Log.e("UserViewModel", "Insufficient balance: balance=${user.balance}, amount=$amount")
                    return@launch
                }

                val depositSnapshot = depositsCollection
                    .whereEqualTo("userEmail", normalizedEmail)
                    .whereEqualTo("status", "Active")
                    .get()
                    .await()

                val transaksi = Transaksi(
                    userEmail = normalizedEmail,
                    type = "Deposito",
                    recipient = "Bank",
                    amount = amount,
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = "Completed",
                    orderId = orderId
                )

                val newBalance = user.balance - amount
                db.runTransaction { transaction ->
                    transaction.update(userDoc.reference, "balance", newBalance)
                    val transactionDocRef = transactionsCollection.document(UUID.randomUUID().toString())
                    transaction.set(transactionDocRef, transaksi)
                }.await()

                val startDate = com.google.firebase.Timestamp.now()
                val nextInterestDate = if (tenorMonths == 0) {
                    com.google.firebase.Timestamp.now()
                } else {
                    val calendar = Calendar.getInstance()
                    calendar.time = startDate.toDate()
                    calendar.add(Calendar.DAY_OF_MONTH, 30)
                    com.google.firebase.Timestamp(calendar.time)
                }

                val calculatedInterestRate = customInterestRate ?: when {
                    amount >= 50_000_000 -> 3.0
                    amount >= 20_000_000 -> 2.5
                    else -> 2.0
                }

                if (depositSnapshot.isEmpty) {
                    val deposit = Deposit(
                        userEmail = normalizedEmail,
                        amount = amount,
                        interestRate = calculatedInterestRate,
                        tenorMonths = tenorMonths,
                        isReinvest = isReinvest,
                        startDate = startDate,
                        nextInterestDate = nextInterestDate,
                        status = "Active",
                        orderId = orderId
                    )
                    depositsCollection.add(deposit).await()
                    Log.d("UserViewModel", "New deposit created: orderId=$orderId, amount=$amount, interestRate=$calculatedInterestRate, tenorMonths=$tenorMonths")
                } else {
                    val depositDoc = depositSnapshot.documents[0]
                    val existingDeposit = depositDoc.toObject(Deposit::class.java)
                    if (existingDeposit == null) {
                        _depositResult.postValue(DepositResult.Failure("Failed to parse deposit data"))
                        Log.e("UserViewModel", "Failed to parse deposit data for email=$normalizedEmail")
                        return@launch
                    }
                    val totalAmount = existingDeposit.amount + amount
                    val newInterestRate = customInterestRate ?: when {
                        totalAmount >= 50_000_000 -> 3.0
                        totalAmount >= 20_000_000 -> 2.5
                        else -> 2.0
                    }

                    val updates = mutableMapOf<String, Any>(
                        "amount" to totalAmount,
                        "interestRate" to newInterestRate,
                        "isReinvest" to isReinvest,
                        "nextInterestDate" to nextInterestDate,
                        "orderId" to orderId,
                        "tenorMonths" to tenorMonths
                    )

                    depositsCollection.document(depositDoc.id).update(updates).await()
                    Log.d("UserViewModel", "Deposit updated: orderId=$orderId, totalAmount=$totalAmount, newInterestRate=$newInterestRate, tenorMonths=$tenorMonths")
                }

                _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                _depositResult.postValue(DepositResult.Success("Deposit created/updated successfully"))
                Log.d("UserViewModel", "Deposito processed: orderId=$orderId, newBalance=$newBalance")
            } catch (e: Exception) {
                _depositResult.postValue(DepositResult.Failure("Deposit processing failed: ${e.message}"))
                Log.e("UserViewModel", "Deposito error: orderId=$orderId, error=${e.message}", e)
            }
        }
    }

    fun processDepositInterest(depositId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val depositSnapshot = depositsCollection.document(depositId).get().await()
                val deposit = depositSnapshot.toObject(Deposit::class.java)
                if (deposit == null || deposit.status != "Active") {
                    Log.e("UserViewModel", "Invalid or inactive deposit: depositId=$depositId")
                    return@launch
                }

                val userSnapshot = usersCollection.whereEqualTo("email", deposit.userEmail).get().await()
                if (userSnapshot.isEmpty) {
                    Log.e("UserViewModel", "User not found for deposit: email=${deposit.userEmail}")
                    return@launch
                }
                val userDoc = userSnapshot.documents[0]
                val user = userDoc.toObject(User::class.java) ?: return@launch

                val calendar = Calendar.getInstance().apply { time = deposit.startDate.toDate() }
                calendar.add(Calendar.MONTH, deposit.tenorMonths)
                val maturityDate = Timestamp(calendar.time)
                val today = Timestamp.now()

                if (today.seconds >= maturityDate.seconds || deposit.tenorMonths == 0) {
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
                        transaction.update(depositSnapshot.reference, "status", "Matured")
                    }.await()

                    _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                    Log.d("UserViewModel", "Deposit matured and cashed out: depositId=$depositId, amount=$finalAmountToReturn")
                } else {
                    val monthlyInterest = deposit.amount * (deposit.interestRate / 100) / 12
                    Log.d("UserViewModel", "Processing interest for depositId=$deposit.id, monthlyInterest=$monthlyInterest, isReinvest=${deposit.isReinvest}")

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
                        _user.postValue(user.copy(id = userDoc.id, balance = newBalance))
                    }

                    val calendar = Calendar.getInstance()
                    calendar.time = deposit.nextInterestDate.toDate()
                    calendar.add(Calendar.DAY_OF_MONTH, 30)
                    val newNextInterestDate = com.google.firebase.Timestamp(calendar.time)
                    depositsCollection.document(depositId).update("nextInterestDate", newNextInterestDate).await()
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error processing interest for depositId=$depositId: ${e.message}", e)
            }
        }
    }

    fun clearUpdatePremiumError() {
        _updatePremiumError.postValue(null)
    }

    fun fetchBankAccounts(userEmail: String) {
        viewModelScope.launch {
            try {
                val normalizedEmail = userEmail.lowercase()
                val snapshot = bankAccountsCollection.whereEqualTo("userEmail", normalizedEmail).get().await()
                val accounts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(BankAccount::class.java)?.copy(id = doc.id)
                }
                _bankAccounts.postValue(accounts)
                Log.d("UserViewModel", "Fetched ${accounts.size} bank accounts for email=$normalizedEmail")
            } catch (e: Exception) {
                Log.e("UserViewModel", "Fetch bank accounts error for email=$userEmail: ${e.message}", e)
                _bankAccounts.postValue(emptyList())
            }
        }
    }

    fun saveBankAccount(userEmail: String, bankName: String, accountNumber: String, accountHolderName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val normalizedEmail = userEmail.lowercase()
                val bankAccount = BankAccount(
                    userEmail = normalizedEmail,
                    bankName = bankName.trim(),
                    accountNumber = accountNumber.trim(),
                    accountHolderName = accountHolderName.trim()
                )
                val docRef = bankAccountsCollection.add(bankAccount).await()
                _bankAccounts.postValue((_bankAccounts.value ?: emptyList()) + bankAccount.copy(id = docRef.id))
                Log.d("UserViewModel", "Bank account saved for email=$normalizedEmail, docId=${docRef.id}")
                onResult(null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Save bank account error for email=$userEmail: ${e.message}", e)
                onResult(e.message)
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