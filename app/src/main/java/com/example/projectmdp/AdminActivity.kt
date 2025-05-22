package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.projectmdp.databinding.ActivityAdminBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView
    var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainerView2) as NavHostFragment
        navController = navHostFragment.navController
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)
        userEmail = intent.getStringExtra("userEmail") ?: ""

        bottomNavigationView.setOnItemSelectedListener { item ->
            val bundle = Bundle().apply {
                userEmail?.let { putString("userEmail", it) }
            }
            when (item.itemId) {
                R.id.allUsersFragment2 -> {
                    navController.navigate(R.id.allUsersFragment2, bundle)
                    true
                }
                R.id.premiumUsersFragment2 -> {
                    navController.navigate(R.id.premiumUsersFragment2, bundle)
                    true
                }
                R.id.requestPremiumFragment -> {
                    navController.navigate(R.id.requestPremiumFragment, bundle)
                    true
                }
                R.id.adminProfileFragment2 ->{
                    navController.navigate(R.id.adminProfileFragment2, bundle)
                    true
                }
                else -> false
            }
        }
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
        if (savedInstanceState == null) {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            navController.navigate(R.id.allUsersFragment2, bundle)
            bottomNavigationView.selectedItemId = R.id.allUsersFragment2
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView2, fragment)
            .commit()
    }
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}