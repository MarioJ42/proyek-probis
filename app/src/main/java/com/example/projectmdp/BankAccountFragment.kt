package com.example.projectmdp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

        // Setup Bank Name Dropdown
        val bankOptions = arrayOf("BCA", "Mandiri", "Danamon", "Permata", "BNI")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, bankOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.bankNameSpinner.adapter = adapter

        // Setup Account Number Formatting
        binding.accountNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                // Remove all non-digit characters
                val digitsOnly = s.toString().replace(Regex("[^0-9]"), "")
                // Limit to 16 digits
                val limitedDigits = if (digitsOnly.length > 16) digitsOnly.substring(0, 16) else digitsOnly
                // Format with spaces every 4 digits
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
            val bankName = binding.bankNameSpinner.selectedItem.toString()
            val accountNumber = binding.accountNumber.text.toString().replace(" ", "")
            val accountHolderName = binding.accountHolderName.text.toString().trim()

            if (bankName.isEmpty() || accountNumber.isEmpty() || accountHolderName.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (accountNumber.length != 16) {
                Toast.makeText(context, "Account number must be exactly 16 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveBankAccount(userEmail, bankName, accountNumber, accountHolderName) { error ->
                if (error == null) {
                    Toast.makeText(context, "Bank account saved successfully", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(context, "Failed to save bank account: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}