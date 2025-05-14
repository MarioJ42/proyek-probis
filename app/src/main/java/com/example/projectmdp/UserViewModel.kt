package com.example.projectmdp

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class UserViewModel(private val repository: UserDao) : ViewModel() {
    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> get() = _user

    private val _balance = MutableLiveData<Double>()
    val balance: LiveData<Double> get() = _balance

    private val _loginResult = MutableLiveData<Boolean>()
    val loginResult: LiveData<Boolean> get() = _loginResult

    fun login(email: String, password: String) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user != null && user.password == password) {
                _user.postValue(user)
                _balance.postValue(user.balance)
                _loginResult.postValue(true)
            } else {
                _loginResult.postValue(false)
            }
        }
    }

    fun fetchUser(email: String) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            _user.postValue(user)
            user?.let {
                _balance.postValue(it.balance)
                Log.d("UserViewModel", "fetchUser: Email = $email, Balance = ${it.balance}")
            } ?: Log.e("UserViewModel", "fetchUser: User not found for email = $email")
        }
    }

    fun register(email: String, fullName: String, password: String, pin: String) {
        viewModelScope.launch {
            val user = User(fullName = fullName, email = email, password = password, pin = pin)
            repository.insertUser(user)
        }
    }

    fun updateUserProfile(email: String, fullName: String, newEmail: String) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user != null) {
                val updatedUser = user.copy(fullName = fullName, email = newEmail)
                repository.updateUser(updatedUser)
                fetchUser(newEmail)
            }
        }
    }

    fun topUp(email: String, amount: Double) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user != null) {
                val updatedUser = user.copy(balance = user.balance + amount)
                repository.updateUser(updatedUser)
                fetchUser(email)
            }
        }
    }

    fun transfer(fromEmail: String, toEmail: String, amount: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.transfer(fromEmail, toEmail, amount)
                fetchUser(fromEmail)
                Log.d("UserViewModel", "Transfer: New sender balance = ${_user.value?.balance}")
                onResult(null)
            } catch (e: IllegalArgumentException) {
                Log.e("UserViewModel", "Transfer failed: ${e.message}")
                onResult(e.message)
            }
        }
    }

    fun transferToBank(fromEmail: String, bankAccount: String, amount: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val user = repository.getUserByEmail(fromEmail)
                if (user == null) {
                    throw IllegalArgumentException("User not found")
                }
                if (user.balance < amount) {
                    throw IllegalArgumentException("Insufficient balance")
                }
                val updatedUser = user.copy(balance = user.balance - amount)
                repository.updateUser(updatedUser)
                fetchUser(fromEmail)
                Log.d("UserViewModel", "TransferToBank: New balance = ${_user.value?.balance}")
                onResult(null)
            } catch (e: IllegalArgumentException) {
                Log.e("UserViewModel", "TransferToBank failed: ${e.message}")
                onResult(e.message)
            }
        }
    }

    fun setPremiumStatus(email: String) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user != null) {
                val updatedUser = user.copy(premium = true)
                repository.updateUser(updatedUser)
                fetchUser(email)
            }
        }
    }
}

class UserViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            val repository = AppDatabase.getInstance(context).userDao()
            @Suppress("UNCHECKED_CAST")
            return UserViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}