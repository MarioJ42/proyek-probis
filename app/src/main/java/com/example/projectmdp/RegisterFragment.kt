package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loginResult.observe(viewLifecycleOwner) { success ->
            binding.progressBar.visibility = View.GONE
            when (success) {
                true -> {
                    Toast.makeText(requireContext(), "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
                    val email = binding.etEmail.text.toString().trim()
                    val bundle = Bundle().apply { putString("userEmail", email) }
                    findNavController().navigate(R.id.action_registerFragment_to_loginFragment, bundle)
                    viewModel.clearLoginResult()
                }
                false -> Toast.makeText(requireContext(), "Email already registered. Please use a different email.", Toast.LENGTH_SHORT).show()
                null -> Toast.makeText(requireContext(), "Registration failed. Check your network and try again.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val pin = binding.etPin.text.toString()
            val phone = binding.etNotelp.text.toString()

            // Enhanced validation with specific error messages
            when {
                fullName.isEmpty() -> {
                    Toast.makeText(requireContext(), "Full name is required.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    Toast.makeText(requireContext(), "Please enter a valid email.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                password.isEmpty() || password.length < 6 -> {
                    Toast.makeText(requireContext(), "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pin.isEmpty() || pin.length != 6 || !pin.all { it.isDigit() } -> {
                    Toast.makeText(requireContext(), "PIN must be exactly 6 digits.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                phone.isEmpty() || phone.length < 10 || !phone.all { it.isDigit() } -> {
                    Toast.makeText(requireContext(), "Phone number must be at least 10 digits and contain only numbers.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            binding.progressBar.visibility = View.VISIBLE
            viewModel.register(email, fullName, password, pin, phone)
        }

        binding.tvBackToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearLoginResult()
    }
}