package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentQrisPaymentBinding

class QrisPaymentFragment : Fragment() {
    private var _binding: FragmentQrisPaymentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrisPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_qrisPaymentFragment_to_loginFragment)
            return
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_qrisPaymentFragment_to_homeFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}