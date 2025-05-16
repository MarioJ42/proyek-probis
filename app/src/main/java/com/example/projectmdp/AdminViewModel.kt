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

class AdminViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> get() = _users

    private val _premiumUsers = MutableLiveData<List<User>>()
    val premiumUsers: LiveData<List<User>> get() = _premiumUsers

    fun fetchAllUsersExceptAdmin(adminEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.get().await()
                val users = snapshot.documents
                    .mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    .filter { it.email != adminEmail }
                _users.postValue(users)
                Log.d("AdminViewModel", "Fetched ${users.size} users")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching users: ${e.message}")
                _users.postValue(emptyList())
            }
        }
    }

    fun fetchPremiumUsersExceptAdmin(adminEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.whereEqualTo("premium", true).get().await()
                val premiumUsers = snapshot.documents
                    .mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    .filter { it.email != adminEmail }
                _premiumUsers.postValue(premiumUsers)
                Log.d("AdminViewModel", "Fetched ${premiumUsers.size} premium users")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching premium users: ${e.message}")
                _premiumUsers.postValue(emptyList())
            }
        }
    }

    fun updateUserStatus(userId: String, newStatus: String, adminEmail: String) {
        viewModelScope.launch {
            try {
                usersCollection.document(userId).update("status", newStatus).await()
                Log.d("AdminViewModel", "Updated user $userId status to $newStatus")
                fetchAllUsersExceptAdmin(adminEmail)
                fetchPremiumUsersExceptAdmin(adminEmail)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error updating user status: ${e.message}")
            }
        }
    }
}

class AdminViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}