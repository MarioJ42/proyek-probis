package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentTransferBinding

class TransferFragment : Fragment() {
    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.fetchUser(userEmail)
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user?.premium == true) {
                binding.bankTransferLayout.visibility = View.VISIBLE
            } else {
                binding.bankTransferLayout.visibility = View.GONE
            }
        }

        val transferOptions = arrayOf("To User", "To Bank")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, transferOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.transferTypeSpinner.adapter = adapter

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_transferFragment_to_homeFragment, bundle)
        }

        binding.btnTransfer.setOnClickListener {
            val transferType = binding.transferTypeSpinner.selectedItem.toString()
            val recipient = binding.inputRecipient.text.toString()
            val amount = binding.inputAmount.text.toString().toDoubleOrNull()
            val bankAccount = binding.inputBankAccount.text.toString()

            when (transferType) {
                "To User" -> {
                    if (recipient.isEmpty() || amount == null) {
                        Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.transfer(userEmail, recipient, amount) { errorMessage ->
                        if (errorMessage != null) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Transfer successful", Toast.LENGTH_SHORT).show()
                            val bundle = Bundle().apply {
                                putString("userEmail", userEmail)
                            }
                            findNavController().navigate(R.id.action_transferFragment_to_homeFragment, bundle)
                        }
                    }
                }
                "To Bank" -> {
                    if (bankAccount.isEmpty() || amount == null) {
                        Toast.makeText(requireContext(), "Please fill in all fields for bank transfer", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.transferToBank(userEmail, bankAccount, amount) { errorMessage ->
                        if (errorMessage != null) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Bank transfer successful", Toast.LENGTH_SHORT).show()
                            val bundle = Bundle().apply {
                                putString("userEmail", userEmail)
                            }
                            findNavController().navigate(R.id.action_transferFragment_to_homeFragment, bundle)
                        }
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