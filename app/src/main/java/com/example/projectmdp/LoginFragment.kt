package com.example.projectmdp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

            when (result) {
                true -> {
                    val email = binding.inputEmail.text.toString().trim()
                    viewModel.user.observe(viewLifecycleOwner) { user ->
                        if (user != null && findNavController().currentDestination?.id == R.id.loginFragment) {
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
//                                    intent.putExtra("userFullname", )
                                    startActivity(intent)
                                    requireActivity().finish()
                                } else {
                                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment, bundle)
                                }
                                viewModel.clearLoginResult()
                            } catch (e: IllegalArgumentException) {
                                Toast.makeText(requireContext(), "Already logged in", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                false -> {
                    Toast.makeText(requireContext(), "Invalid email or password", Toast.LENGTH_SHORT).show()
                    viewModel.clearLoginResult()
                }
                null -> {
                    Toast.makeText(requireContext(), "Login failed. Please try again.", Toast.LENGTH_SHORT).show()
                    viewModel.clearLoginResult()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}