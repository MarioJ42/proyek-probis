package com.example.projectmdp

import android.os.Bundle
import android.util.Log
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
        val transferTypes = arrayOf("To User", "Bank Transfer")
        val transferTypeAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_item, transferTypes)
        binding.transferTypeAutoCompleteTextView.setAdapter(transferTypeAdapter)

        if (isPremium) {
            binding.transferTypeDropdown.visibility = View.VISIBLE

            binding.transferTypeAutoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
                when (transferTypes[position]) {
                    "To User" -> {
                        binding.toUserFormCard.visibility = View.VISIBLE
                        binding.bankTransferFormCard.visibility = View.GONE
                        Log.d("TransferFragment", "Selected: To User")
                    }
                    "Bank Transfer" -> {
                        binding.toUserFormCard.visibility = View.GONE
                        binding.bankTransferFormCard.visibility = View.VISIBLE
                        Log.d("TransferFragment", "Selected: Bank Transfer")

                        binding.addBankAccountButton.visibility = View.VISIBLE
                        binding.bankAccountDropdown.visibility = View.VISIBLE
                        binding.tilBankAmount.visibility = View.VISIBLE
                        binding.bankTransferButton.visibility = View.VISIBLE

                        viewModel.bankAccounts.observe(viewLifecycleOwner) { bankAccounts ->
                            Log.d("TransferFragment", "Bank accounts observed: count=${bankAccounts.size}")
                            if (bankAccounts.isNotEmpty()) {
                                val accountOptions = bankAccounts.map { "${it.bankName} - ${it.accountNumber} (${it.accountHolderName})" }
                                val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_item, accountOptions)
                                binding.bankAccountAutoCompleteTextView.setAdapter(spinnerAdapter)
                                Log.d("TransferFragment", "Showing bank account dropdown with ${accountOptions.size} options")
                            } else {
                                binding.bankAccountAutoCompleteTextView.setAdapter(null)
                                binding.bankAccountAutoCompleteTextView.setText("", false)
                                Log.d("TransferFragment", "No bank accounts, dropdown cleared")
                            }
                        }
                    }
                    else -> {
                        binding.toUserFormCard.visibility = View.VISIBLE
                        binding.bankTransferFormCard.visibility = View.GONE
                        Log.d("TransferFragment", "Default: To User (unexpected selection)")
                    }
                }
            }

            binding.transferTypeAutoCompleteTextView.setText(transferTypes[0], false)
            binding.toUserFormCard.visibility = View.VISIBLE
            binding.bankTransferFormCard.visibility = View.GONE

        } else {
            binding.transferTypeDropdown.visibility = View.GONE
            binding.toUserFormCard.visibility = View.VISIBLE
            binding.bankTransferFormCard.visibility = View.GONE
            Log.d("TransferFragment", "Non-premium user, showing To User form only")
        }

        binding.addBankAccountButton.setOnClickListener {
            Log.d("TransferFragment", "Add Bank Account button clicked")
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_transferFragment_to_bankAccountFragment, bundle)
        }

        binding.bankTransferButton.setOnClickListener {
            val bankAccounts = viewModel.bankAccounts.value ?: emptyList()
            val selectedAccountText = binding.bankAccountAutoCompleteTextView.text.toString()

            if (bankAccounts.isEmpty() || selectedAccountText.isEmpty()) {
                Toast.makeText(context, "Please select a bank account first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedAccount = bankAccounts.find {
                "${it.bankName} - ${it.accountNumber} (${it.accountHolderName})" == selectedAccountText
            }

            if (selectedAccount == null) {
                Toast.makeText(context, "Invalid bank account selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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