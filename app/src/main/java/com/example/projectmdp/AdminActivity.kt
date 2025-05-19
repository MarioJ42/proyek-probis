package com.example.projectmdp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.projectmdp.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var userEmail: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userEmail = intent.getStringExtra("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            finish()
            return
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all_users -> {
                    loadFragment(AllUsersFragment.newInstance(userEmail))
                    true
                }
                R.id.nav_premium_users -> {
                    loadFragment(PremiumUsersFragment.newInstance(userEmail))
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            loadFragment(AllUsersFragment.newInstance(userEmail))
            binding.bottomNavigation.selectedItemId = R.id.nav_all_users
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}