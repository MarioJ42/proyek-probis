package com.example.projectmdp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentInvestasiTabunganBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class InvestasiTabunganFragment : Fragment() {
    private var _binding: FragmentInvestasiTabunganBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var userEmail: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvestasiTabunganBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userEmail = arguments?.getString("userEmail") ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_investasiTabunganFragment_to_loginFragment)
            return
        }

        showPasswordConfirmationDialog { isVerified ->
            if (!isVerified) {
                Toast.makeText(context, "Kata sandi salah, kembali ke beranda", Toast.LENGTH_LONG).show()
                val bundle = Bundle().apply { putString("userEmail", userEmail) }
                findNavController().navigate(R.id.action_investasiTabunganFragment_to_homeFragment, bundle)
                return@showPasswordConfirmationDialog
            }

            fetchDepositData()
            setupListeners()
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_investasiTabunganFragment_to_homeFragment, bundle)
        }
    }

    private fun fetchDepositData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = Firebase.firestore.collection("deposits")
                    .whereEqualTo("userEmail", userEmail.lowercase())
                    .whereEqualTo("status", "Active")
                    .get()
                    .await()
                withContext(Dispatchers.Main) {
                    if (snapshot.isEmpty) {
                        binding.balanceTextView.text = "Rp0"
                        binding.interestRateTextView.text = "0%"
                        binding.nextInterestDateTextView.text = "Belum ada deposito"
                    } else {
                        val deposit = snapshot.documents[0].toObject(Deposit::class.java)
                        if (deposit != null) {
                            val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                            binding.balanceTextView.text = formatter.format(deposit.amount)
                            binding.interestRateTextView.text = "${deposit.interestRate}%"
                            val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                            binding.nextInterestDateTextView.text = dateFormat.format(deposit.nextInterestDate.toDate())
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal memuat data deposito: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("InvestasiTabunganFragment", "Error fetching deposit: ${e.message}", e)
            }
        }
    }

    private fun setupListeners() {
        binding.buyDepositButton.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_investasiTabunganFragment_to_depositPurchaseFragment, bundle)
        }

        binding.withdrawButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snapshot = Firebase.firestore.collection("deposits")
                        .whereEqualTo("userEmail", userEmail.lowercase())
                        .whereEqualTo("status", "Active")
                        .get()
                        .await()
                    if (snapshot.isEmpty) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Tidak ada deposito aktif", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    val deposit = snapshot.documents[0].toObject(Deposit::class.java)
                    if (deposit == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Data deposito tidak valid", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val currentDate = Calendar.getInstance().time
                    val maturityDate = deposit.nextInterestDate.toDate()
                    val calendar = Calendar.getInstance()
                    calendar.time = maturityDate
                    calendar.add(Calendar.DAY_OF_MONTH, -7)
                    val sevenDaysBefore = calendar.time
                    calendar.add(Calendar.DAY_OF_MONTH, 14)
                    val sevenDaysAfter = calendar.time

                    val penalty = if (currentDate.before(sevenDaysBefore) || currentDate.after(sevenDaysAfter)) {
                        deposit.amount * 0.02
                    } else {
                        0.0
                    }
                    val withdrawableAmount = deposit.amount - penalty

                    withContext(Dispatchers.Main) {
                        showWithdrawDialog(deposit.amount, withdrawableAmount, penalty, deposit.orderId)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showWithdrawDialog(totalAmount: Double, withdrawableAmount: Double, penalty: Double, orderId: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_withdraw, null)
        val amountInput = dialogView.findViewById<EditText>(R.id.withdrawAmountInput)
        val penaltyText = dialogView.findViewById<TextView>(R.id.penaltyText)
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

        penaltyText.text = if (penalty > 0) "Potongan 2%: ${formatter.format(penalty)}" else "Tanpa potongan"
        amountInput.hint = "Maksimum: ${formatter.format(withdrawableAmount)}"

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Tarik Saldo Deposito")
            .setView(dialogView)
            .setPositiveButton("Konfirmasi") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0 || amount > withdrawableAmount) {
                    Toast.makeText(context, "Jumlah penarikan tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                showPasswordConfirmationDialog { isVerified ->
                    if (!isVerified) {
                        Toast.makeText(context, "Kata sandi salah, penarikan dibatalkan", Toast.LENGTH_LONG).show()
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
                            val userDoc = userSnapshot.documents[0]
                            val user = userDoc.toObject(User::class.java)
                            val depositSnapshot = Firebase.firestore.collection("deposits")
                                .whereEqualTo("orderId", orderId)
                                .get()
                                .await()
                            val depositDoc = depositSnapshot.documents[0]

                            val newBalance = (user?.balance ?: 0.0) + amount
                            val newDepositAmount = totalAmount - amount

                            userDoc.reference.update("balance", newBalance).await()

                            val tolerance = 0.01
                            if (abs(amount - withdrawableAmount) < tolerance) {
                                depositDoc.reference.update(
                                    mapOf(
                                        "amount" to 0.0,
                                        "status" to "Withdrawn"
                                    )
                                ).await()
                            } else {
                                depositDoc.reference.update("amount", newDepositAmount).await()
                            }

                            val transaction = Transaksi(
                                userEmail = userEmail.lowercase(),
                                type = "Withdrawal",
                                recipient = null,
                                amount = amount,
                                timestamp = Timestamp.now(),
                                status = "Completed",
                                orderId = null
                            )
                            Firebase.firestore.collection("transactions")
                                .add(transaction)
                                .await()

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Penarikan berhasil", Toast.LENGTH_SHORT).show()
                                fetchDepositData()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Gagal menarik: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .setCancelable(false)
            .create()

        dialog.show()
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