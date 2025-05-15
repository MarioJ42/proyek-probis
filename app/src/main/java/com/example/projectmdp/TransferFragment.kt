package com.example.projectmdp

import android.os.Bundle
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
        viewModel.fetchUser(userEmail)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                setupTransferOptions(user.premium, userEmail)
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
                        }
                        "Bank Transfer" -> {
                            binding.toUserForm.visibility = View.GONE
                            binding.bankTransferForm.visibility = View.VISIBLE
                        }
                        else -> {
                            binding.toUserForm.visibility = View.VISIBLE
                            binding.bankTransferForm.visibility = View.GONE
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    binding.toUserForm.visibility = View.VISIBLE
                    binding.bankTransferForm.visibility = View.GONE
                }
            }

            // Default to To User
            binding.transferTypeSpinner.setSelection(0)
        } else {
            binding.transferTypeSpinner.visibility = View.GONE
            binding.toUserForm.visibility = View.VISIBLE
            binding.bankTransferForm.visibility = View.GONE
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
        }

        binding.bankTransferButton.setOnClickListener {
            val bankAccount = binding.bankAccountNumber.text.toString().trim()
            val amount = binding.bankAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (bankAccount.isEmpty() || amount <= 0) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
                putString("bankAccount", bankAccount)
                putFloat("amount", amount.toFloat())
                putString("transferType", "bankTransfer")
            }
            findNavController().navigate(R.id.action_transferFragment_to_pinVerificationFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}