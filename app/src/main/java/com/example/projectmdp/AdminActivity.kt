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
                R.id.nav_all_users -> {
                    navController.navigate(R.id.nav_all_users, bundle)
                    true
                }
                R.id.nav_premium_users -> {
                    navController.navigate(R.id.nav_premium_users, bundle)
                    true
                }
                R.id.nav_request_premium -> {
                    navController.navigate(R.id.nav_request_premium, bundle)
                    true
                }
                R.id.nav_admin_profile ->{
                    navController.navigate(R.id.nav_admin_profile, bundle)
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
//        if (savedInstanceState == null) {
//            loadFragment(AllUsersFragment.newInstance(userEmail.toString()))
//            binding.bottomNavigation.selectedItemId = R.id.nav_all_users
//        }
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