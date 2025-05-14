package com.example.projectmdp

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HistoryFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""

        // Example: Add a click listener to navigate (e.g., logout to LoginFragment)
        view.findViewById<View>(R.id.logoutButton)?.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_historyFragment_to_loginFragment, bundle)
        }

        // Navigate to HomeFragment (e.g., back button)
        view.findViewById<View>(R.id.backButton)?.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_historyFragment_to_homeFragment, bundle)
        }
    }
}