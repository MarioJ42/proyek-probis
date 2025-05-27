package com.example.projectmdp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentDepositPurchaseBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.UUID
import java.util.Locale

class DepositPurchaseFragment : Fragment() {
    private var _binding: FragmentDepositPurchaseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var userEmail: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDepositPurchaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_depositPurchaseFragment_to_loginFragment)
            return
        }

        showPasswordConfirmationDialog { isVerified ->
            if (!isVerified) {
                Toast.makeText(context, "Kata sandi salah, kembali ke investasi", Toast.LENGTH_LONG).show()
                val bundle = Bundle().apply { putString("userEmail", userEmail) }
                findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
                return@showPasswordConfirmationDialog
            }

            setupTenorSpinner()
            setupInterestOptionSpinner()
            setupListeners()
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
        }
    }

    private fun setupTenorSpinner() {
        val tenorOptions = arrayOf("1 bulan", "3 bulan", "6 bulan", "12 bulan")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tenorOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.tenorSpinner.adapter = adapter
    }

    private fun setupInterestOptionSpinner() {
        val options = arrayOf("Putar Kembali Bunga", "Cairkan ke Saldo")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.interestOptionSpinner.adapter = adapter
    }

    private fun setupListeners() {
        binding.simulateButton.setOnClickListener {
            val amount = binding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 5_000_000) {
                Toast.makeText(context, "Jumlah minimum Rp5.000.000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tenor = when (binding.tenorSpinner.selectedItem.toString()) {
                "1 bulan" -> 1
                "3 bulan" -> 3
                "6 bulan" -> 6
                "12 bulan" -> 12
                else -> 6
            }
            val interestRate = when {
                amount >= 50_000_000 -> 3.0
                amount >= 20_000_000 -> 2.5
                else -> 2.0
            }
            val isReinvest = binding.interestOptionSpinner.selectedItem.toString() == "Putar Kembali Bunga"

            val monthlyRate = interestRate / 100 / 12
            var totalInterest = 0.0
            var finalAmount = amount

            if (isReinvest) {
                var currentPrincipal = amount
                for (month in 1..tenor) {
                    val monthlyInterest = currentPrincipal * monthlyRate
                    totalInterest += monthlyInterest
                    currentPrincipal += monthlyInterest
                }
                finalAmount = currentPrincipal
            } else {
                val monthlyInterest = amount * monthlyRate
                totalInterest = monthlyInterest * tenor
                finalAmount = amount + totalInterest
            }

            val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            binding.resultTextView.text = "Suku Bunga: ${interestRate}%\nBunga: ${formatter.format(totalInterest)}\nTotal: ${formatter.format(finalAmount)}"
        }

        binding.confirmButton.setOnClickListener {
            val amount = binding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 5_000_000) {
                Toast.makeText(context, "Jumlah minimum Rp5.000.000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tenor = when (binding.tenorSpinner.selectedItem.toString()) {
                "1 bulan" -> 1
                "3 bulan" -> 3
                "6 bulan" -> 6
                "12 bulan" -> 12
                else -> 6
            }
            val interestOption = binding.interestOptionSpinner.selectedItem.toString()
            val isReinvest = interestOption == "Putar Kembali Bunga"
            val orderId = UUID.randomUUID().toString()

            showPasswordConfirmationDialog { isVerified ->
                if (!isVerified) {
                    Toast.makeText(context, "Kata sandi salah, pembelian dibatalkan", Toast.LENGTH_LONG).show()
                    return@showPasswordConfirmationDialog
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val userSnapshot = Firebase.firestore.collection("users")
                            .whereEqualTo("email", userEmail.lowercase())
                            .get()
                            .await()
                        if (userSnapshot.isEmpty) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Pengguna tidak ditemukan", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                        val user = userSnapshot.documents[0].toObject(User::class.java)
                        if (user?.balance ?: 0.0 < amount) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saldo tidak cukup", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                        viewModel.createOrUpdateDeposit(userEmail, amount, tenor, isReinvest, orderId)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Pembelian deposito berhasil", Toast.LENGTH_SHORT).show()
                            val bundle = Bundle().apply { putString("userEmail", userEmail) }
                            findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Gagal memproses: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showPasswordConfirmationDialog(onVerified: (Boolean) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_password_confirmation, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Kata Sandi")
            .setView(dialogView)
            .setPositiveButton("Konfirmasi") { _, _ ->
                val password = passwordInput.text.toString()
                verifyPassword(password, onVerified)
            }
            .setNegativeButton("Batal") { _, _ ->
                onVerified(false)
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun verifyPassword(password: String, onVerified: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = Firebase.firestore.collection("users")
                    .whereEqualTo("email", userEmail.lowercase())
                    .get()
                    .await()
                if (snapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Pengguna tidak ditemukan", Toast.LENGTH_LONG).show()
                        onVerified(false)
                    }
                    return@launch
                }
                val user = snapshot.documents[0].toObject(User::class.java)
                val isVerified = user?.password == password
                withContext(Dispatchers.Main) {
                    onVerified(isVerified)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal memverifikasi: ${e.message}", Toast.LENGTH_LONG).show()
                    onVerified(false)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}