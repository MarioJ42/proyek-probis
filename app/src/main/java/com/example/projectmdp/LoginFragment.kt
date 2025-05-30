package com.example.projectmdp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle // Import ini
import androidx.lifecycle.LifecycleOwner // Import ini
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null

    private val binding get() = _binding!!

    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private lateinit var db: AppDatabase
    private lateinit var dao: RememberedUserDAO
    var isAutoLogin = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())
        dao = db.RememberedUserDAO()

        viewModel.getRememberMe { rememberedUser ->
            if (_binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                rememberedUser?.let {
                    binding.inputEmail.setText(it.email)
                    binding.inputPassword.setText(it.password)
                    binding.checkboxRemember.isChecked = true
                    if (!isAutoLogin) {
                        isAutoLogin = true
                        viewModel.login(it.email, it.password)
                    }
                }
            }
        }

        binding.checkboxRemember.setOnCheckedChangeListener { _, isChecked ->
            // Pastikan binding tidak null sebelum mengakses view
            if (_binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val email = binding.inputEmail.text.toString().trim()
                val password = binding.inputPassword.text.toString().trim()

                if (isChecked) {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        viewModel.saverememberme(RememberUser(email = email, password = password))
                    } else {
                        Toast.makeText(requireContext(), "Fill email & password before checking Remember Me", Toast.LENGTH_SHORT).show()
                        binding.checkboxRemember.isChecked = false
                    }
                } else {
                    viewModel.clearRememberMe()
                }
            }
        }

        binding.button.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.login(email, password)
        }

        binding.registerText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe

            if (_binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                when (result) {
                    true -> {
                        val email = binding.inputEmail.text.toString().trim()
                        val password = binding.inputPassword.text.toString().trim()
                        val rememberMeChecked = binding.checkboxRemember.isChecked

                        if (rememberMeChecked) {
                            viewModel.saverememberme(RememberUser(email = email, password = password))
                        } else {
                            viewModel.clearRememberMe()
                        }

                        viewModel.user.observe(viewLifecycleOwner) { user ->
                            if (_binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && user != null && findNavController().currentDestination?.id == R.id.loginFragment) {
                                if (user.status == "inactive") {
                                    AlertDialog.Builder(requireContext())
                                        .setTitle("Account Inactive")
                                        .setMessage("Your account is currently inactive. Please contact support.")
                                        .setPositiveButton("OK") { _, _ -> }
                                        .show()
                                    viewModel.clearLoginResult()
                                    return@observe
                                }
                                val bundle = Bundle().apply { putString("userEmail", email) }
                                try {
                                    if (user.role == 1) {
                                        val intent = Intent(requireContext(), AdminActivity::class.java)
                                        intent.putExtra("userEmail", email)
                                        startActivity(intent)
                                        requireActivity().finish()
                                    } else {
                                        findNavController().navigate(R.id.action_loginFragment_to_pinFragment, bundle)
                                    }
                                    viewModel.clearLoginResult()
                                } catch (e: IllegalArgumentException) {
                                    Toast.makeText(requireContext(), "Already logged in", Toast.LENGTH_SHORT).show()
                                    Log.d("LoginFragment", "Navigation error: ${e.message}")
                                }
                            }
                        }
                    }
                    false -> {
                        Toast.makeText(requireContext(), "Invalid email or password", Toast.LENGTH_SHORT).show()
                        viewModel.clearLoginResult()
                    }
                    // 'null' di result ini juga bisa dihandle untuk kasus login gagal
                    else -> { // Menggabungkan null dan skenario lain yang tidak true/false
                        Toast.makeText(requireContext(), "Login failed. Please try again.", Toast.LENGTH_SHORT).show()
                        viewModel.clearLoginResult()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}