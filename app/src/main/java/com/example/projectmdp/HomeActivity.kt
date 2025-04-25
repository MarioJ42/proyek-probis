package com.example.projectmdp

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)

        // Initialize database
        db = AppDatabase.getDatabase(this)

        // Get user email from intent
        val userEmail = intent.getStringExtra("USER_EMAIL")
        println("DEBUG: Received email in HomeActivity: $userEmail")

        if (userEmail == null) {
            println("DEBUG: Email is null, redirecting to login")
            startActivity(Intent(this, loginActivity::class.java))
            finish()
            return
        }

        // Fetch user data and update UI
        CoroutineScope(Dispatchers.IO).launch {
            val user = db.userDao().getUserByEmail(userEmail)
            println("DEBUG: Fetched user from database: $user")
            withContext(Dispatchers.Main) {
                if (user != null) {
                    // Update username TextView
                    val usernameTextView = findViewById<TextView>(R.id.username)
                    usernameTextView.text = "Hello, ${user.fullName}!"
                    println("DEBUG: Updated username to: ${usernameTextView.text}")

                    // Update balance TextView
                    val balanceTextView = findViewById<TextView>(R.id.balanceTextView)
                    val formattedBalance = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                        .format(user.balance)
                        .replace("IDR", "Rp ")
                    balanceTextView.text = formattedBalance
                    println("DEBUG: Updated balance to: $formattedBalance")
                } else {
                    println("DEBUG: User not found, redirecting to login")
                    startActivity(Intent(this@HomeActivity, loginActivity::class.java))
                    finish()
                }
            }
        }

        // Set up bottom navigation
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("USER_EMAIL", userEmail)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // Handle Top Up button click
        findViewById<LinearLayout>(R.id.topUp).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val user = db.userDao().getUserByEmail(userEmail)
                if (user != null) {
                    val newBalance = user.balance + 1000000.0
                    db.userDao().updateBalance(userEmail, newBalance)
                    withContext(Dispatchers.Main) {
                        val balanceTextView = findViewById<TextView>(R.id.balanceTextView)
                        val formattedBalance = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                            .format(newBalance)
                            .replace("IDR", "Rp ")
                        balanceTextView.text = formattedBalance
                        println("DEBUG: Updated balance after top-up: $formattedBalance")
                    }
                }
            }
        }
    }
}