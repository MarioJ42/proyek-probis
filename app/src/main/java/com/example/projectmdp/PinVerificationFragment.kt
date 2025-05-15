package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentPinVerificationBinding
import kotlinx.coroutines.launch

class PinVerificationFragment : Fragment() {
    private var _binding: FragmentPinVerificationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        val recipient = arguments?.getString("recipient")
        val bankAccount = arguments?.getString("bankAccount")
        val amount = arguments?.getFloat("amount")?.toDouble() ?: 0.0
        val transferType = arguments?.getString("transferType")

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

                lifecycleScope.launch {
                    when (transferType) {
                        "toUser" -> {
                            if (recipient != null) {
                                viewModel.transfer(userEmail, recipient, amount) { errorMessage ->
                                    handleTransferResult(errorMessage, userEmail)
                                }
                            }
                        }
                        "bankTransfer" -> {
                            if (bankAccount != null) {
                                viewModel.transferToBank(userEmail, bankAccount, amount) { errorMessage ->
                                    handleTransferResult(errorMessage, userEmail)
                                }
                            }
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Invalid transfer type", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun handleTransferResult(errorMessage: String?, userEmail: String) {
        if (errorMessage == null) {
            Toast.makeText(requireContext(), "Transfer successful", Toast.LENGTH_SHORT).show()
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_pinVerificationFragment_to_homeFragment, bundle)
        } else {
            Toast.makeText(requireContext(), "Transfer failed: $errorMessage", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}