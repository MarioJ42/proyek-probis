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

    private var pinAttemptCount = 0
    private val maxPinAttempts = 3

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

        showPinVerificationDialog { isVerified ->
            if (!isVerified) {
                Toast.makeText(context, "PIN salah, kembali ke beranda", Toast.LENGTH_LONG).show()
                val bundle = Bundle().apply { putString("userEmail", userEmail) }
                findNavController().navigate(R.id.action_investasiTabunganFragment_to_homeFragment, bundle)
                return@showPinVerificationDialog
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
                            val dateFormat = SimpleDateFormat("dd MMMMyyyy", Locale("id", "ID"))
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


    }

    /*
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

                showPinVerificationDialog { isVerified ->
                    if (!isVerified) {
                        Toast.makeText(context, "PIN salah, penarikan dibatalkan", Toast.LENGTH_LONG).show()
                        return@showPinVerificationDialog
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
    */

    private fun showPinVerificationDialog(onVerified: (Boolean) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.fragment_pin_verification, null)
        val pinDots = listOf(
            dialogView.findViewById<TextView>(R.id.pinDot1),
            dialogView.findViewById<TextView>(R.id.pinDot2),
            dialogView.findViewById<TextView>(R.id.pinDot3),
            dialogView.findViewById<TextView>(R.id.pinDot4),
            dialogView.findViewById<TextView>(R.id.pinDot5),
            dialogView.findViewById<TextView>(R.id.pinDot6)
        )
        val pinBuilder = StringBuilder()

        val keyButtons = mapOf(
            R.id.key0 to "0", R.id.key1 to "1", R.id.key2 to "2", R.id.key3 to "3",
            R.id.key4 to "4", R.id.key5 to "5", R.id.key6 to "6", R.id.key7 to "7",
            R.id.key8 to "8", R.id.key9 to "9"
        )
        keyButtons.forEach { (id, digit) ->
            dialogView.findViewById<View>(id).setOnClickListener {
                if (pinBuilder.length < 6) {
                    pinBuilder.append(digit)
                    pinDots[pinBuilder.length - 1].text = "●"
                }
            }
        }

        dialogView.findViewById<View>(R.id.keyDelete).setOnClickListener {
            if (pinBuilder.isNotEmpty()) {
                pinDots[pinBuilder.length - 1].text = ""
                pinBuilder.deleteCharAt(pinBuilder.length - 1)
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi PIN")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<View>(R.id.keyConfirm).setOnClickListener {
            if (pinBuilder.length == 6) {
                verifyPin(pinBuilder.toString()) { isVerified ->
                    if (isVerified) {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                        onVerified(true)
                    } else {
                        pinAttemptCount++
                        val attemptsLeft = maxPinAttempts - pinAttemptCount
                        if (attemptsLeft > 0) {
                            Toast.makeText(context, "PIN salah. $attemptsLeft percobaan tersisa.", Toast.LENGTH_SHORT).show()
                            pinBuilder.clear()
                            pinDots.forEach { it.text = "" }
                        } else {
                            Toast.makeText(context, "Percobaan PIN maksimum tercapai. Kembali.", Toast.LENGTH_LONG).show()
                            if (dialog.isShowing) {
                                dialog.dismiss()
                            }
                            onVerified(false)
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Masukkan PIN 6 digit", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun verifyPin(pin: String, onVerified: (Boolean) -> Unit) {
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
                val isVerified = user?.pin == pin
                withContext(Dispatchers.Main) {
                    onVerified(isVerified)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal memverifikasi PIN: ${e.message}", Toast.LENGTH_LONG).show()
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