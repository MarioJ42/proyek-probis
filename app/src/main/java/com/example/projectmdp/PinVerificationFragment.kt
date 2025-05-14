package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentPinVerificationBinding

class PinVerificationFragment : Fragment() {
    private var _binding: FragmentPinVerificationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        val recipient = arguments?.getString("recipient") ?: ""
        val amount = arguments?.getFloat("amount")?.toDouble() ?: 0.0
        val transferType = arguments?.getString("transferType") ?: ""
        val bankAccount = arguments?.getString("bankAccount") ?: ""

        Log.d("PinVerificationFragment", "Received: userEmail=$userEmail, recipient=$recipient, amount=$amount, transferType=$transferType, bankAccount=$bankAccount")

        if (userEmail.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnForgotPin.setOnClickListener {
            Toast.makeText(requireContext(), "Forgot PIN feature not implemented", Toast.LENGTH_SHORT).show()
        }

        binding.btnConfirmPin.setOnClickListener {
            val enteredPin = binding.inputPin.text.toString().trim()
            if (enteredPin.length != 6) {
                Toast.makeText(requireContext(), "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.fetchUser(userEmail)
            viewModel.user.observe(viewLifecycleOwner) { user ->
                if (user == null) {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    return@observe
                }
                if (user.pin != enteredPin) {
                    Toast.makeText(requireContext(), "Invalid PIN", Toast.LENGTH_SHORT).show()
                    return@observe
                }

                when (transferType) {
                    "To User" -> {
                        viewModel.transfer(userEmail, recipient, amount) { errorMessage ->
                            handleTransferResult(errorMessage, userEmail)
                        }
                    }
                    "To Bank" -> {
                        viewModel.transferToBank(userEmail, bankAccount, amount) { errorMessage ->
                            handleTransferResult(errorMessage, userEmail)
                        }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Invalid transfer type", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleTransferResult(errorMessage: String?, userEmail: String) {
        if (errorMessage != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Transfer successful", Toast.LENGTH_SHORT).show()
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            Log.d("PinVerificationFragment", "Navigating to HomeFragment with userEmail: $userEmail")
            findNavController().navigate(R.id.action_pinVerificationFragment_to_homeFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}