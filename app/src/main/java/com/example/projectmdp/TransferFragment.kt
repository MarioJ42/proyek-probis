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
        inflater: LayoutInflater,
        container: ViewGroup?,
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
            findNavController().navigateUp()
            return
        }

        // Fetch user to check premium status
        viewModel.fetchUser(userEmail)
        viewModel.user.observe(viewLifecycleOwner) { user ->
            binding.bankTransferLayout.visibility = if (user?.premium == true) View.VISIBLE else View.GONE
        }

        // Setup spinner for transfer types
        val transferOptions = arrayOf("To User", "To Bank")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, transferOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.transferTypeSpinner.adapter = adapter

        // Back button
        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_transferFragment_to_homeFragment, bundle)
        }

        // Transfer button
        binding.btnTransfer.setOnClickListener {
            val transferType = binding.transferTypeSpinner.selectedItem.toString()
            val recipient = binding.inputRecipient.text.toString().trim()
            val amount = binding.inputAmount.text.toString().toDoubleOrNull()

            if (recipient.isEmpty() || amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Please fill in all fields with valid values", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
                putString("recipient", recipient)
                putFloat("amount", amount.toFloat()) // Ubah dari putDouble ke putFloat
                putString("transferType", transferType)
            }
            findNavController().navigate(R.id.action_transferFragment_to_pinVerificationFragment, bundle)
        }

        // Bank Transfer button
        binding.btnBankTransfer.setOnClickListener {
            val bankAccount = binding.inputBankAccount.text.toString().trim()
            val amount = binding.inputBankAmount.text.toString().toDoubleOrNull()

            if (bankAccount.isEmpty() || amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Please fill in all fields with valid values", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
                putString("bankAccount", bankAccount)
                putFloat("amount", amount.toFloat()) // Ubah dari putDouble ke putFloat
                putString("transferType", "To Bank")
            }
            findNavController().navigate(R.id.action_transferFragment_to_pinVerificationFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}