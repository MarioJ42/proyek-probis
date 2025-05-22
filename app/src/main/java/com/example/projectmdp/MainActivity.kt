package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView
    var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottomNavigation)

        // Default setup with NavController
        bottomNavigationView.setupWithNavController(navController)

        // Override item selection to pass userEmail
        bottomNavigationView.setOnItemSelectedListener { item ->
            val bundle = Bundle().apply {
                userEmail?.let { putString("userEmail", it) }
            }
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.homeFragment, bundle)
                    true
                }
                R.id.profileFragment -> {
                    navController.navigate(R.id.profileFragment, bundle)
                    true
                }
                else -> false
            }
        }

        // Show/hide BottomNavigationView based on the current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("Navigation", "Navigated to destination: ${destination.id}, label: ${destination.label}")
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment -> {
                    bottomNavigationView.visibility = View.GONE
                }
                else -> {
                    bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }

        // Listen for login success to set userEmail
        navController.addOnDestinationChangedListener { _, destination, args ->
            when (destination.id) {
                R.id.homeFragment, R.id.loginFragment, R.id.registerFragment -> {
                    userEmail = args?.getString("userEmail")
                    Log.d("MainActivity", "User email set: $userEmail")
                }
            }
        }
    }

    // Handle back navigation
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}