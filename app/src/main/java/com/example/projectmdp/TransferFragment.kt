package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentTransferBinding

class TransferFragment : Fragment() {
    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }

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
        Log.d("TransferFragment", "User email: $userEmail")

        // Fetch user and bank accounts
        viewModel.fetchUser(userEmail)
        viewModel.fetchBankAccounts(userEmail)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                Log.d("TransferFragment", "User fetched: isPremium=${user.premium}")
                setupTransferOptions(user.premium, userEmail)
            } else {
                Log.e("TransferFragment", "User fetch failed for email=$userEmail")
            }
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupTransferOptions(isPremium: Boolean, userEmail: String) {
        if (isPremium) {
            binding.transferTypeSpinner.visibility = View.VISIBLE
            val options = arrayOf("To User", "Bank Transfer")
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.transferTypeSpinner.adapter = adapter

            binding.transferTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    when (options[position]) {
                        "To User" -> {
                            binding.toUserForm.visibility = View.VISIBLE
                            binding.bankTransferForm.visibility = View.GONE
                            Log.d("TransferFragment", "Selected: To User")
                        }
                        "Bank Transfer" -> {
                            binding.toUserForm.visibility = View.GONE
                            binding.bankTransferForm.visibility = View.VISIBLE
                            Log.d("TransferFragment", "Selected: Bank Transfer")

                            // Always show Add Bank Account button and transfer form
                            binding.addBankAccountButton.visibility = View.VISIBLE
                            binding.bankAccountSpinner.visibility = View.VISIBLE
                            binding.tilBankAmount.visibility = View.VISIBLE
                            binding.bankTransferButton.visibility = View.VISIBLE

                            // Observe bank accounts to populate the spinner
                            viewModel.bankAccounts.observe(viewLifecycleOwner) { bankAccounts ->
                                Log.d("TransferFragment", "Bank accounts observed: count=${bankAccounts.size}")
                                if (bankAccounts.isNotEmpty()) {
                                    // Populate spinner with bank accounts
                                    val accountOptions = bankAccounts.map { "${it.bankName} - ${it.accountNumber} (${it.accountHolderName})" }
                                    val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, accountOptions)
                                    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                    binding.bankAccountSpinner.adapter = spinnerAdapter
                                    Log.d("TransferFragment", "Showing bank account spinner with ${accountOptions.size} options")
                                } else {
                                    // Clear spinner if no accounts
                                    binding.bankAccountSpinner.adapter = null
                                    Log.d("TransferFragment", "No bank accounts, spinner cleared")
                                }
                            }
                        }
                        else -> {
                            binding.toUserForm.visibility = View.VISIBLE
                            binding.bankTransferForm.visibility = View.GONE
                            Log.d("TransferFragment", "Default: To User")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    binding.toUserForm.visibility = View.VISIBLE
                    binding.bankTransferForm.visibility = View.GONE
                    Log.d("TransferFragment", "Nothing selected, defaulting to To User")
                }
            }

            // Default to To User
            binding.transferTypeSpinner.setSelection(0)

            // Add Bank Account Button
            binding.addBankAccountButton.setOnClickListener {
                Log.d("TransferFragment", "Add Bank Account button clicked")
                val bundle = Bundle().apply {
                    putString("userEmail", userEmail)
                }
                findNavController().navigate(R.id.action_transferFragment_to_bankAccountFragment, bundle)
            }

            // Bank Transfer Button
            binding.bankTransferButton.setOnClickListener {
                val selectedPosition = binding.bankAccountSpinner.selectedItemPosition
                val bankAccounts = viewModel.bankAccounts.value ?: emptyList()
                if (bankAccounts.isEmpty()) {
                    Toast.makeText(context, "Please add a bank account first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (selectedPosition >= 0 && selectedPosition < bankAccounts.size) {
                    val selectedAccount = bankAccounts[selectedPosition]
                    val amount = binding.bankAmount.text.toString().toDoubleOrNull() ?: 0.0
                    if (amount <= 0) {
                        Toast.makeText(context, "Please fill the amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val bundle = Bundle().apply {
                        putString("userEmail", userEmail)
                        putString("bankAccount", selectedAccount.accountNumber)
                        putFloat("amount", amount.toFloat())
                        putString("transferType", "bankTransfer")
                    }
                    findNavController().navigate(R.id.action_transferFragment_to_pinVerificationFragment, bundle)
                    Log.d("TransferFragment", "Bank Transfer initiated with account: ${selectedAccount.accountNumber}")
                } else {
                    Toast.makeText(context, "Please select a bank account", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.transferTypeSpinner.visibility = View.GONE
            binding.toUserForm.visibility = View.VISIBLE
            binding.bankTransferForm.visibility = View.GONE
            Log.d("TransferFragment", "Non-premium user, showing To User form only")
        }

        binding.transferButton.setOnClickListener {
            val recipientEmail = binding.recipientEmail.text.toString().trim()
            val amount = binding.amount.text.toString().toDoubleOrNull() ?: 0.0
            if (recipientEmail.isEmpty() || amount <= 0) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
                putString("recipient", recipientEmail)
                putFloat("amount", amount.toFloat())
                putString("transferType", "toUser")
            }
            findNavController().navigate(R.id.action_transferFragment_to_pinVerificationFragment, bundle)
            Log.d("TransferFragment", "To User Transfer initiated: recipient=$recipientEmail, amount=$amount")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}