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

data class PremiumRequest(
    val id: String = "",
    val userEmail: String = "",
    val ktpPhoto: String = "",
    val premium: Boolean = false,
    val requestPremium: Boolean = false
)

class AdminViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val premiumCollection = db.collection("premium")
    private val _selectedUser = MutableLiveData<User?>()
    val selectedUser: LiveData<User?> get() = _selectedUser
    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> get() = _users

    private val _premiumUsers = MutableLiveData<List<User>>()
    val premiumUsers: LiveData<List<User>> get() = _premiumUsers
    private val _selectedPremiumRequest = MutableLiveData<Premium?>()
    val selectedPremiumRequest: LiveData<Premium?> get() = _selectedPremiumRequest
    private val _premiumRequests = MutableLiveData<List<PremiumRequest>>()
    val premiumRequests: LiveData<List<PremiumRequest>> get() = _premiumRequests

    private val _updatePremiumError = MutableLiveData<String?>()
    val updatePremiumError: LiveData<String?> get() = _updatePremiumError

    private val _userCache = mutableMapOf<String, User?>()

    fun fetchAllUsersExceptAdmin(adminEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = usersCollection.get().await()
                val users = snapshot.documents
                    .mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    .filter { it.email != adminEmail }
                users.forEach { user -> _userCache[user.email] = user }
                _users.postValue(users)
                Log.d("AdminViewModel", "Fetched ${users.size} users")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching users: ${e.message}")
                _users.postValue(emptyList())
                _updatePremiumError.postValue("Failed to fetch users: ${e.message}") // Post error
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
                premiumUsers.forEach { user -> _userCache[user.email] = user }
                _premiumUsers.postValue(premiumUsers)
                Log.d("AdminViewModel", "Fetched ${premiumUsers.size} premium users")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching premium users: ${e.message}")
                _premiumUsers.postValue(emptyList())
                _updatePremiumError.postValue("Failed to fetch premium users: ${e.message}") // Post error
            }
        }
    }

    fun fetchPremiumRequestsExceptAdmin(adminEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = premiumCollection.whereEqualTo("requestPremium", true).get().await()
                val premiumRequests = snapshot.documents
                    .mapNotNull { it.toObject(PremiumRequest::class.java)?.copy(id = it.id) }
                    .filter { it.userEmail != adminEmail }
                _premiumRequests.postValue(premiumRequests)
                Log.d("AdminViewModel", "Fetched ${premiumRequests.size} premium requests")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching premium requests: ${e.message}")
                _premiumRequests.postValue(emptyList())
                _updatePremiumError.postValue("Failed to fetch premium requests: ${e.message}") // Post error
            }
        }
    }

    fun acceptPremiumRequest(requestId: String, adminEmail: String) {
        viewModelScope.launch {
            try {
                val snapshot = premiumCollection.document(requestId).get().await()
                val request = snapshot.toObject(PremiumRequest::class.java)
                if (request != null) {
                    premiumCollection.document(requestId).update(
                        mapOf(
                            "premium" to true,
                            "requestPremium" to false
                        )
                    ).await()
                    val userSnapshot = usersCollection.whereEqualTo("email", request.userEmail).get().await()
                    if (!userSnapshot.isEmpty()) {
                        usersCollection.document(userSnapshot.documents[0].id).update("premium", true).await()
                    }
                    Log.d("AdminViewModel", "Accepted premium request for ${request.userEmail}")
                    fetchPremiumRequestsExceptAdmin(adminEmail)
                    _updatePremiumError.postValue(null) // Clear error on success
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error accepting premium request: ${e.message}", e)
                _updatePremiumError.postValue("Failed to accept request: ${e.message}") // Post error
            }
        }
    }

    fun rejectPremiumRequest(requestId: String, adminEmail: String) {
        viewModelScope.launch {
            try {
                premiumCollection.document(requestId).update("requestPremium", false).await()
                Log.d("AdminViewModel", "Rejected premium request for document $requestId")
                fetchPremiumRequestsExceptAdmin(adminEmail)
                _updatePremiumError.postValue(null) // Clear error on success
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error rejecting premium request: ${e.message}", e)
                _updatePremiumError.postValue("Failed to reject request: ${e.message}") // Post error
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
                _updatePremiumError.postValue(null) // Clear error on success
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error updating user status: ${e.message}", e)
                _updatePremiumError.postValue("Failed to update user status: ${e.message}") // Post error
            }
        }
    }


    fun clearUpdatePremiumError() {
        _updatePremiumError.postValue(null)
    }
    fun getUserByEmail(email: String) {
        if (_userCache[email] != null) {
            _selectedUser.postValue(_userCache[email])
        } else {
            viewModelScope.launch {
                try {
                    val snapshot = usersCollection.whereEqualTo("email", email).get().await()
                    val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)?.copy(id = snapshot.documents[0].id)
                    _userCache[email] = user
                    _selectedUser.postValue(user)
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error fetching user by email $email: ${e.message}")
                    _selectedUser.postValue(null)
                }
            }
        }
    }
    fun getUserrequestByEmail(email: String){
        viewModelScope.launch {
            try {
                val snapshot = premiumCollection.whereEqualTo("userEmail", email).get().await()
                val premiumuser = snapshot.documents.firstOrNull()?.toObject(Premium::class.java)
                    ?.copy(id = snapshot.documents[0].id)
                _selectedPremiumRequest.postValue(premiumuser)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching premium request by email $email: ${e.message}")
                _selectedPremiumRequest.postValue(null)
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