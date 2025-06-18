package com.example.projectmdp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentBankAccountBinding

class BankAccountFragment : Fragment() {
    private var _binding: FragmentBankAccountBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var isFormatting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBankAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        viewModel.fetchUser(userEmail)
        viewModel.fetchBankAccounts(userEmail)

        val bankOptions = arrayOf("BCA", "Mandiri", "Danamon", "Permata", "BNI")
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_item, bankOptions)
        binding.bankNameAutoCompleteTextView.setAdapter(adapter)

        binding.bankNameAutoCompleteTextView.setText(bankOptions[0], false)
        updateAccountNumberHint(bankOptions[0])

        binding.bankNameAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedBank = bankOptions[position]
            updateAccountNumberHint(selectedBank)
        }

        binding.accountNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                val selectedBank = binding.bankNameAutoCompleteTextView.text.toString()
                val maxDigits = if (selectedBank == "Mandiri") 13 else 10
                val digitsOnly = s.toString().replace(Regex("[^0-9]"), "")
                val limitedDigits = if (digitsOnly.length > maxDigits) digitsOnly.substring(0, maxDigits) else digitsOnly
                val formatted = limitedDigits.chunked(4).joinToString(" ")
                binding.accountNumber.setText(formatted)
                binding.accountNumber.setSelection(formatted.length)

                isFormatting = false
            }
        })

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.saveButton.setOnClickListener {
            val bankName = binding.bankNameAutoCompleteTextView.text.toString()
            val accountNumber = binding.accountNumber.text.toString().replace(" ", "")
            val accountHolderName = binding.accountHolderName.text.toString().trim()

            if (bankName.isEmpty()) {
                Toast.makeText(context, "Please select a bank", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (accountHolderName.isEmpty()) {
                Toast.makeText(context, "Please fill the account holder name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (accountNumber.isEmpty()) {
                Toast.makeText(context, "Please fill the account number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requiredDigits = if (bankName == "Mandiri") 13 else 10
            if (accountNumber.length != requiredDigits) {
                Toast.makeText(context, "Account number must be $requiredDigits digits for $bankName", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (bankName == "Mandiri" && !accountNumber.startsWith("1") && !accountNumber.startsWith("9")) {
                Toast.makeText(context, "Mandiri account number must start with 1 or 9", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveBankAccount(userEmail, bankName, accountNumber, accountHolderName) { error ->
                if (error == null) {
                    Toast.makeText(context, "Bank account saved successfully", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(context, "Failed to save bank account: $error", Toast.LENGTH_LONG).show()
                    Log.e("BankAccountFragment", "Save bank account error: $error")
                }
            }
        }
    }

    private fun updateAccountNumberHint(bankName: String) {
        val hint = when (bankName) {
            "Mandiri" -> "Enter 13-digit account number (starts with 1 or 9)"
            else -> "Enter 10-digit account number"
        }
        binding.accountNumber.hint = hint
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}