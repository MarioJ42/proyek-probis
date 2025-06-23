package com.example.projectmdp

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentDepositPurchaseBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.UUID
import java.util.Locale
import java.util.Calendar
import java.text.DecimalFormat
import java.util.Currency

class DepositPurchaseFragment : Fragment() {
    private var _binding: FragmentDepositPurchaseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var userEmail: String = ""

    private var pinAttemptCount = 0
    private val maxPinAttempts = 3

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

        showPinVerificationDialog { isVerified ->
            if (!isVerified) {
                Toast.makeText(context, "PIN salah, kembali ke investasi", Toast.LENGTH_LONG).show()
                val bundle = Bundle().apply { putString("userEmail", userEmail) }
                findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
                return@showPinVerificationDialog
            }

            setupTenorSpinner()
            setupInterestOptionSpinner()
            setupAmountFormatting(binding.amountInput)
            setupListeners()
        }

        binding.btnBack.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_depositPurchaseFragment_to_investasiTabunganFragment, bundle)
        }
    }

    private fun setupTenorSpinner() {
        val tenorOptions = arrayOf("1 bulan", "3 bulan", "6 bulan", "12 bulan", "Test (15 detik)")
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
            val amountText = binding.amountInput.text.toString().replace("Rp ", "").replace(".", "")
            val amount = amountText.toDoubleOrNull() ?: 0.0
            if (amount < 100_000) {
                Toast.makeText(context, "Jumlah minimum Rp100.000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedTenorString = binding.tenorSpinner.selectedItem.toString()
            val tenor = if (selectedTenorString == "Test (15 detik)") {
                0
            } else {
                when (selectedTenorString) {
                    "1 bulan" -> 1
                    "3 bulan" -> 3
                    "6 bulan" -> 6
                    "12 bulan" -> 12
                    else -> 6
                }
            }
            val isTestTenor = selectedTenorString == "Test (15 detik)"

            val interestRate = when {
                amount >= 50_000_000 -> 3.0
                amount >= 20_000_000 -> 2.5
                else -> 2.0
            }
            val isReinvest = binding.interestOptionSpinner.selectedItem.toString() == "Putar Kembali Bunga"

            val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            formatter.currency = Currency.getInstance("IDR")

            if (!isTestTenor) {
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
                binding.resultTextView.text = "Suku Bunga: ${interestRate}%\nBunga: ${formatter.format(totalInterest)}\nTotal: ${formatter.format(finalAmount)}"
            } else {
                val monthlyRateTest = interestRate / 100 / 12
                val monthlyInterestTest = amount * monthlyRateTest
                val finalAmountTest = amount + monthlyInterestTest
                binding.resultTextView.text = "Tenor Test (15 detik)\nSuku Bunga: ${interestRate}%\nBunga Estimasi (1 bulan): ${formatter.format(monthlyInterestTest)}\nTotal Estimasi (1 bulan): ${formatter.format(finalAmountTest)}"
            }
        }

        binding.confirmButton.setOnClickListener {
            val amountText = binding.amountInput.text.toString().replace("Rp ", "").replace(".", "")
            val amount = amountText.toDoubleOrNull() ?: 0.0
            if (amount < 100_000) {
                Toast.makeText(context, "Jumlah minimum Rp100.000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedTenorString = binding.tenorSpinner.selectedItem.toString()
            val tenor = if (selectedTenorString == "Test (15 detik)") {
                0
            } else {
                when (selectedTenorString) {
                    "1 bulan" -> 1
                    "3 bulan" -> 3
                    "6 bulan" -> 6
                    "12 bulan" -> 12
                    else -> 6
                }
            }
            val isTestTenor = selectedTenorString == "Test (15 detik)"

            val interestOption = binding.interestOptionSpinner.selectedItem.toString()
            val isReinvest = interestOption == "Putar Kembali Bunga"
            val orderId = UUID.randomUUID().toString()

            pinAttemptCount = 0
            showPinVerificationDialog { isVerified ->
                if (!isVerified) {
                    Toast.makeText(context, "PIN salah, pembelian dibatalkan", Toast.LENGTH_LONG).show()
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
                        val user = userSnapshot.documents[0].toObject(User::class.java)
                        if (user?.balance ?: 0.0 < amount) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saldo tidak cukup", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }

                        val interestRateForDeposit = when {
                            amount >= 50_000_000 -> 3.0
                            amount >= 20_000_000 -> 2.5
                            else -> 2.0
                        }

                        viewModel.createOrUpdateDeposit(userEmail, amount, tenor, isReinvest, orderId, interestRateForDeposit)

                        if (isTestTenor) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Deposito test dibuat. Akan cair dalam 15 detik dengan bunga biasa.", Toast.LENGTH_LONG).show()
                            }
                            launch {
                                kotlinx.coroutines.delay(15000L)
                                val depositSnapshot = Firebase.firestore.collection("deposits")
                                    .whereEqualTo("userEmail", userEmail.lowercase())
                                    .whereEqualTo("orderId", orderId)
                                    .whereEqualTo("status", "Active")
                                    .get()
                                    .await()

                                if (!depositSnapshot.isEmpty) {
                                    val testDepositDoc = depositSnapshot.documents[0]
                                    val testDeposit = testDepositDoc.toObject(Deposit::class.java)
                                    if (testDeposit != null) {
                                        Log.d("DepositPurchaseFragment", "Processing test deposit maturity for ${testDepositDoc.id}")

                                        viewModel.processDepositInterest(testDepositDoc.id)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Deposito test telah cair dengan bunga!", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Log.e("DepositPurchaseFragment", "Failed to parse test deposit data.")
                                    }
                                } else {
                                    Log.e("DepositPurchaseFragment", "Test deposit not found or not active for immediate maturity.")
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (!isTestTenor) {
                                Toast.makeText(context, "Pembelian deposito berhasil", Toast.LENGTH_SHORT).show()
                            }
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



    private fun setupAmountFormatting(editText: EditText) {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID")) as DecimalFormat
        val symbols = formatter.decimalFormatSymbols
        val groupSeparator = symbols.groupingSeparator

        editText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            private var isDeleting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (count > 0 && after == 0) {
                    isDeleting = true
                } else {
                    isDeleting = false
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.toString() == current) {
                    return
                }

                editText.removeTextChangedListener(this)

                var cleanString = s.toString().replace("Rp", "").replace(groupSeparator.toString(), "").trim()

                if (cleanString.isEmpty()) {
                    current = ""
                    editText.setText("")
                    editText.addTextChangedListener(this)
                    return
                }

                if (cleanString.startsWith(groupSeparator)) {
                    cleanString = "0$cleanString"
                }

                val parsed = try {
                    cleanString.toLong()
                } catch (e: NumberFormatException) {
                    0L
                }

                val formattedText = formatter.format(parsed)
                val newText = "Rp $formattedText"

                current = newText
                editText.setText(newText)

                val selectionIndex = if (isDeleting) {
                    editText.selectionStart.coerceAtMost(newText.length)
                } else {
                    newText.length
                }
                Selection.setSelection(editText.text, selectionIndex)


                editText.addTextChangedListener(this)

                if (editText.text.isEmpty() || !editText.text.startsWith("Rp ")) {
                    editText.setText("Rp ")
                    Selection.setSelection(editText.text, editText.text.length)
                }
            }
        })

        if (editText.text.isEmpty()) {
            editText.setText("Rp ")
            Selection.setSelection(editText.text, editText.text.length)
        }
    }

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