package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory()}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            return
        }

        viewModel.fetchUser(userEmail)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.editTextText3.setText(user.fullName)
                binding.editTextText2.setText(user.email)

                binding.editTextText.setText("081234567890") // Placeholder
                binding.buttonPremium.isEnabled = !user.premium // Disable if already premium
                binding.buttonPremium.text = if (user.premium) "Premium Activated" else "Get Premium"
            } else {
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }
        }

        binding.button2.setOnClickListener {
            val newFullName = binding.editTextText3.text.toString()
            val newEmail = binding.editTextText2.text.toString()
            val newPhone = binding.editTextText.text.toString()

            if (newFullName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateUserProfile(userEmail, newFullName, newEmail)
            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
        }

        binding.button6.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonPremium.setOnClickListener {
            viewModel.setPremiumStatus(userEmail)
            Toast.makeText(requireContext(), "Premium status activated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}