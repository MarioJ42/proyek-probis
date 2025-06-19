package com.example.projectmdp

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentRegisterBinding
import com.google.android.material.textfield.TextInputEditText

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
            // Baris ini akan selalu dipanggil sekarang, baik sukses maupun gagal
            binding.progressBar.visibility = View.GONE

            when (success) {
                true -> {
                    Toast.makeText(requireContext(), "Registration successful! Please log in.", Toast.LENGTH_LONG).show()
                    val email = binding.etEmail.text.toString().trim()
                    val bundle = Bundle().apply { putString("userEmail", email) }
                    findNavController().navigate(R.id.action_registerFragment_to_loginFragment, bundle)
                    viewModel.clearLoginResult()
                }
                false -> {
                    Toast.makeText(requireContext(), "Email already registered.", Toast.LENGTH_LONG).show()
                }
                null -> {
                    // Sekarang Toast ini akan muncul jika ada error jaringan atau masalah lain
                    Toast.makeText(requireContext(), "Registration failed. An unexpected error occurred.", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            // 1. Ambil semua data dari UI
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val pin = binding.etPin.text.toString()
            val phone = binding.etNotelp.text.toString()
            // Pastikan EditText dengan id etReferralCode ada di file XML Anda
            val referralCode = binding.etReferralCode.text.toString().trim()

            // 2. Lakukan validasi dengan mengirim semua data ke fungsi helper
            if (!validateInput(fullName, email, password, pin, phone)) {
                return@setOnClickListener
            }

            // 3. Jika validasi lolos, tampilkan loading dan panggil ViewModel
            binding.progressBar.visibility = View.VISIBLE
            viewModel.register(email, fullName, password, pin, phone, referralCode)
        }

        binding.tvBackToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    /**
     * Fungsi helper untuk validasi semua input sebelum dikirim ke ViewModel.
     */
    private fun validateInput(fullName: String, email: String, passwordText: String, pin: String, phone: String): Boolean {
        when {
            fullName.isEmpty() -> {
                Toast.makeText(requireContext(), "Full name is required.", Toast.LENGTH_SHORT).show()
                return false
            }
            email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(requireContext(), "Please enter a valid email.", Toast.LENGTH_SHORT).show()
                return false
            }
            passwordText.isEmpty() || passwordText.length < 6 -> {
                Toast.makeText(requireContext(), "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                return false
            }
            pin.isEmpty() || pin.length != 6 || !pin.all { it.isDigit() } -> {
                Toast.makeText(requireContext(), "PIN must be exactly 6 digits.", Toast.LENGTH_SHORT).show()
                return false
            }
            phone.isEmpty() || phone.length != 12 || !phone.all { it.isDigit() } -> {
                Toast.makeText(requireContext(), "Phone number must be exactly 12 digits and contain only numbers.", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Panggil clearLoginResult jika ada di ViewModel Anda untuk menghindari trigger berulang
        viewModel.clearLoginResult()
    }
}