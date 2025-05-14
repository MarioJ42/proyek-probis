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
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.button.setOnClickListener {
            val email = binding.editTextTextEmailAddress.text.toString()
            val fullName = binding.editTextText.text.toString()
            val password = binding.editTextTextPassword.text.toString()
            val pin = binding.editTextTextPin?.text.toString() // Add PIN input

            if (email.isEmpty() || fullName.isEmpty() || password.isEmpty() || pin.isEmpty() || pin.length != 6) {
                Toast.makeText(requireContext(), "Please fill in all fields with a 6-digit PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(email, fullName, password, pin) // Update register call
            Toast.makeText(requireContext(), "Registration successful", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}