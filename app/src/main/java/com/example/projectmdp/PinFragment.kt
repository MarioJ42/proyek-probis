package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentPinBinding
import com.example.projectmdp.databinding.FragmentPinVerificationBinding
import kotlinx.coroutines.launch
import kotlin.getValue


class PinFragment : Fragment() {
    private var _binding: FragmentPinBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private val pinList = mutableListOf<TextView>()
    private var pin = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pinList.add(binding.pinDot1)
        pinList.add(binding.pinDot2)
        pinList.add(binding.pinDot3)
        pinList.add(binding.pinDot4)
        pinList.add(binding.pinDot5)
        pinList.add(binding.pinDot6)
        setupKeypad()
        val userEmail = arguments?.getString("userEmail")?.lowercase() ?: ""
        val bundle = Bundle().apply { putString("userEmail", userEmail) }
    }
    private fun setupKeypad() {
        binding.key1.setOnClickListener { addPinDigit("1") }
        binding.key2.setOnClickListener { addPinDigit("2") }
        binding.key3.setOnClickListener { addPinDigit("3") }
        binding.key4.setOnClickListener { addPinDigit("4") }
        binding.key5.setOnClickListener { addPinDigit("5") }
        binding.key6.setOnClickListener { addPinDigit("6") }
        binding.key7.setOnClickListener { addPinDigit("7") }
        binding.key8.setOnClickListener { addPinDigit("8") }
        binding.key9.setOnClickListener { addPinDigit("9") }
        binding.key0.setOnClickListener { addPinDigit("0") }
        binding.keyDelete.setOnClickListener { removePinDigit() }
        binding.keyConfirm.setOnClickListener { verifyPin() }
    }
    private fun addPinDigit(digit: String) {
        if (pin.length < 6) {
            pin += digit
            updatePinDisplay()
        }
    }

    private fun removePinDigit() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            updatePinDisplay()
        }
    }

    private fun updatePinDisplay() {
        for (i in pinList.indices) {
            pinList[i].text = if (i < pin.length) "•" else ""
        }
    }
    private fun verifyPin() {
        if (pin.length != 6) {
            Toast.makeText(requireContext(), "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = arguments?.getString("userEmail")?.lowercase() ?: ""
        val bundle = Bundle().apply { putString("userEmail", userEmail) }
        viewModel.fetchUser(userEmail)
        viewModel.user.observe(viewLifecycleOwner) { user ->

            if (user?.pin != pin) {
                Log.d("PinVerification", "Invalid PIN entered")
                Toast.makeText(requireContext(), "Invalid PIN", Toast.LENGTH_SHORT).show()
                return@observe
            }else{
                Toast.makeText(requireContext(), "Successfull", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_pinFragment_to_homeFragment, bundle)
            }
        }
    }
}