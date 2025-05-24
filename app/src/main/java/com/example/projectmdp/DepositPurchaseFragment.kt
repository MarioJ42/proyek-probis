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
import java.util.UUID

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

            setupSpinner()
            setupListeners()
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
        }
    }

    private fun setupSpinner() {
        val options = arrayOf("Putar Kembali Bunga", "Cairkan ke Saldo")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.interestOptionSpinner.adapter = adapter
    }

    private fun setupListeners() {
        binding.confirmButton.setOnClickListener {
            val amount = binding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 5_000_000) {
                Toast.makeText(context, "Jumlah minimum Rp5.000.000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showPasswordConfirmationDialog { isVerified ->
                if (!isVerified) {
                    Toast.makeText(context, "Kata sandi salah, pembelian dibatalkan", Toast.LENGTH_LONG).show()
                    return@showPasswordConfirmationDialog
                }

                val interestOption = binding.interestOptionSpinner.selectedItem.toString()
                val isReinvest = interestOption == "Putar Kembali Bunga"
                val orderId = UUID.randomUUID().toString()
                viewModel.createOrUpdateDeposit(userEmail, amount, 12, isReinvest, orderId)
            }
        }

        // Update deposit result observer
        viewModel.depositResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is DepositResult.Success -> {
                    Toast.makeText(context, "Deposito berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    val bundle = Bundle().apply { putString("userEmail", userEmail) }
                    findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
                }
                is DepositResult.Failure -> {
                    Toast.makeText(context, "Gagal: ${result.message}", Toast.LENGTH_LONG).show()
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