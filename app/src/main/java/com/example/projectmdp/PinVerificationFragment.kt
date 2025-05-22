package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentPinVerificationBinding
import kotlinx.coroutines.launch

class PinVerificationFragment : Fragment() {
    private var _binding: FragmentPinVerificationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private val pinList = mutableListOf<TextView>()
    private var pin = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinVerificationBinding.inflate(inflater, container, false)
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
        val recipient = arguments?.getString("recipient")
        val bankAccount = arguments?.getString("bankAccount")
        val amount = arguments?.getFloat("amount") ?: 0.0f
        val transferType = arguments?.getString("transferType")
        val orderId = arguments?.getString("orderId")
        val qrString = arguments?.getString("qrString")

        Log.d("PinVerification", "Received arguments: userEmail=$userEmail, amount=$amount, orderId=$orderId, transferType=$transferType")
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
        val recipient = arguments?.getString("recipient")
        val bankAccount = arguments?.getString("bankAccount")
        val amount = arguments?.getFloat("amount") ?: 0.0f
        val transferType = arguments?.getString("transferType")
        val orderId = arguments?.getString("orderId")
        val qrString = arguments?.getString("qrString")

        viewModel.fetchUser(userEmail)
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user == null) {
                Log.e("PinVerification", "User not found for email: $userEmail")
                Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                setFragmentResult("pin_verification_result", Bundle().apply {
                    putBoolean("success", false)
                    putString("errorMessage", "User not found")
                })
                findNavController().navigateUp()
                return@observe
            }
            if (user.pin != pin) {
                Log.d("PinVerification", "Invalid PIN entered")
                Toast.makeText(requireContext(), "Invalid PIN", Toast.LENGTH_SHORT).show()
                setFragmentResult("pin_verification_result", Bundle().apply {
                    putBoolean("success", false)
                    putString("errorMessage", "Invalid PIN")
                })
                findNavController().navigateUp()
                return@observe
            }

            Log.d("PinVerification", "PIN verified, processing transferType: $transferType, amount=$amount")
            lifecycleScope.launch {
                when (transferType) {
                    "toUser" -> {
                        if (recipient != null) {
                            viewModel.transfer(userEmail, recipient, amount.toDouble()) { errorMessage ->
                                handleTransferResult(errorMessage, userEmail)
                            }
                        } else {
                            Log.e("PinVerification", "Recipient not provided for toUser transfer")
                            Toast.makeText(requireContext(), "Recipient not provided", Toast.LENGTH_SHORT).show()
                            setFragmentResult("pin_verification_result", Bundle().apply {
                                putBoolean("success", false)
                                putString("errorMessage", "Recipient not provided")
                            })
                            findNavController().navigateUp()
                        }
                    }
                    "bankTransfer" -> {
                        if (bankAccount != null) {
                            viewModel.transferToBank(userEmail, bankAccount, amount.toDouble()) { errorMessage ->
                                handleTransferResult(errorMessage, userEmail)
                            }
                        } else {
                            Log.e("PinVerification", "Bank account not provided for bankTransfer")
                            Toast.makeText(requireContext(), "Bank account not provided", Toast.LENGTH_SHORT).show()
                            setFragmentResult("pin_verification_result", Bundle().apply {
                                putBoolean("success", false)
                                putString("errorMessage", "Bank account not provided")
                            })
                            findNavController().navigateUp()
                        }
                    }
                    "qrisPayment" -> {
                        if (orderId != null && qrString != null) {
                            Log.d("PinVerification", "PIN verified for QRIS payment: orderId=$orderId, amount=$amount")
                            setFragmentResult("pin_verification_result", Bundle().apply {
                                putBoolean("success", true)
                            })
                            findNavController().navigateUp()
                        } else {
                            Log.e("PinVerification", "Invalid QRIS payment data: orderId=$orderId, qrString=$qrString")
                            Toast.makeText(requireContext(), "Invalid QRIS payment data", Toast.LENGTH_SHORT).show()
                            setFragmentResult("pin_verification_result", Bundle().apply {
                                putBoolean("success", false)
                                putString("errorMessage", "Invalid QRIS payment data")
                            })
                            findNavController().navigateUp()
                        }
                    }
                    else -> {
                        Log.e("PinVerification", "Invalid transfer type: $transferType")
                        Toast.makeText(requireContext(), "Invalid transfer type", Toast.LENGTH_SHORT).show()
                        setFragmentResult("pin_verification_result", Bundle().apply {
                            putBoolean("success", false)
                            putString("errorMessage", "Invalid transfer type")
                        })
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun handleTransferResult(errorMessage: String?, userEmail: String) {
        if (errorMessage == null) {
            Log.d("PinVerification", "Transfer successful, navigating to HomeFragment")
            Toast.makeText(requireContext(), "Transfer successful", Toast.LENGTH_SHORT).show()
            setFragmentResult("pin_verification_result", Bundle().apply {
                putBoolean("success", true)
            })
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_pinVerificationFragment_to_homeFragment, bundle)
        } else {
            Log.e("PinVerification", "Transfer failed: $errorMessage")
            Toast.makeText(requireContext(), "Transfer failed: $errorMessage", Toast.LENGTH_SHORT).show()
            setFragmentResult("pin_verification_result", Bundle().apply {
                putBoolean("success", false)
                putString("errorMessage", errorMessage)
            })
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}