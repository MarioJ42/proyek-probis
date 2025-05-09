package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentTopUpBinding

class TopUpFragment : Fragment() {
    private var _binding: FragmentTopUpBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_topUpFragment_to_homeFragment, bundle)
        }

        binding.btnTopUp.setOnClickListener {
            val amount = binding.inputTopUpAmount.text.toString().toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(requireContext(), "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.topUp(userEmail, amount)
            Toast.makeText(requireContext(), "Top-up successful", Toast.LENGTH_SHORT).show()
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_topUpFragment_to_homeFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}