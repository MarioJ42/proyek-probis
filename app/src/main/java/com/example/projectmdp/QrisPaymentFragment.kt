package com.example.projectmdp

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentQrisPaymentBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class QrisPaymentFragment : Fragment() {
    private var _binding: FragmentQrisPaymentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var selectedImageUri: Uri? = null
    private var orderId: String? = null
    private var amount: Double? = null
    private var qrString: String? = null
    private var originalQrUrl: String? = null
    private var userEmail: String? = null
    private var isProcessingPayment = false

    private val clipboardManager by lazy {
        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                selectedImageUri = it
                binding.qrImageView.setImageURI(it)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.VISIBLE }
                        processImageForQrCode(it)
                    } finally {
                        withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.GONE }
                    }
                }
            }
        } else {
            Toast.makeText(context, "Failed to pick image", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanQrLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val qrContent = barcodes[0].rawValue
                        if (qrContent != null) {
                            binding.qrUrlEditText.setText(qrContent)
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.VISIBLE }
                                    extractOrderIdAndAmount(qrContent)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "QR code scanned successfully", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.GONE }
                                }
                            }
                        } else {
                            Toast.makeText(context, "No QR code detected", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No QR code detected", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "QR scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("QrisPayment", "QR scan error: ${e.message}", e)
                }
        } else {
            Toast.makeText(context, "QR scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userEmail = arguments?.getString("userEmail")?.lowercase()
        if (userEmail != null) {
            viewModel.setUserEmail(userEmail!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrisPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        viewModel.userEmail.observe(viewLifecycleOwner) { email ->
            if (email != null) {
                userEmail = email
                viewModel.fetchUser(email)
            } else {
                Log.e("QrisPayment", "userEmail is null")
                Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_qrisPaymentFragment_to_loginFragment)
            }
        }

        binding.btnValidatePayment.isEnabled = false
        binding.statusTextView.text = "Status: Scan or enter QR code"
        binding.amountTextView.visibility = View.GONE

        binding.btnUseQrLink.setOnClickListener {
            if (!binding.btnUseQrLink.isEnabled) return@setOnClickListener
            val url = binding.qrUrlEditText.text.toString().trim()
            when {
                url.isEmpty() -> {
                    Toast.makeText(context, "Please enter a QR URL", Toast.LENGTH_SHORT).show()
                }
                !(url.startsWith("http://") || url.startsWith("https://")) -> {
                    Toast.makeText(
                        context,
                        "Please enter a valid URL starting with http:// or https://",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                !url.contains("/qris/") || !url.contains("/qr-code") -> {
                    Toast.makeText(
                        context,
                        "Invalid QR URL. Must contain '/qris/' and '/qr-code'.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    binding.btnUseQrLink.isEnabled = false
                    binding.loadingProgressBar.visibility = View.VISIBLE
                    originalQrUrl = url
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            extractOrderIdAndAmount(url)
                            downloadImageFromUrl(url)
                        } finally {
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    binding.btnUseQrLink.isEnabled = true
                                    binding.loadingProgressBar.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        }

        binding.btnScanQr.setOnClickListener {
            scanQrLauncher.launch(null)
        }

        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickImageLauncher.launch(intent)
        }

        binding.qrImageView.setOnClickListener {
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item.coerceToText(requireContext())?.toString()
                    if (text.isNullOrEmpty()) {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    } else if (!(text.startsWith("http://") || text.startsWith("https://"))) {
                        Toast.makeText(
                            context,
                            "Clipboard contains an invalid URL. Please paste a URL starting with http:// or https://",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (!text.contains("/qris/") || !text.contains("/qr-code")) {
                        Toast.makeText(
                            context,
                            "Invalid QR URL in clipboard. Must contain '/qris/' and '/qr-code'.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        binding.qrUrlEditText.setText(text)
                        binding.loadingProgressBar.visibility = View.VISIBLE
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                extractOrderIdAndAmount(text)
                                withContext(Dispatchers.Main) {
                                    if (isAdded) {
                                        Toast.makeText(context, "URL pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    if (isAdded) {
                                        binding.loadingProgressBar.visibility = View.GONE
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnValidatePayment.setOnClickListener {
            if (isProcessingPayment) {
                Toast.makeText(context, "Payment is already being processed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount == null || qrString == null || orderId == null) {
                Toast.makeText(context, "Please scan or enter a valid QRIS code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = viewModel.user.value
            if (user == null) {
                Toast.makeText(context, "User not found. Please log in.", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_qrisPaymentFragment_to_loginFragment)
                return@setOnClickListener
            }

            if (user.balance < amount!!) {
                Toast.makeText(context, "Insufficient balance", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isProcessingPayment = true
            binding.btnValidatePayment.isEnabled = false
            binding.loadingProgressBar.visibility = View.VISIBLE

            val payment = QrisPayment(
                orderId = orderId!!,
                userEmail = user.email,
                qrString = qrString!!,
                amount = amount!!,
                status = "pending",
                timestamp = Timestamp.now()
            )

            viewLifecycleOwner.lifecycleScope.launch {
                saveToFirestore(payment)
                val bundle = Bundle().apply {
                    putString("userEmail", user.email)
                    putString("orderId", orderId)
                    putFloat("amount", amount!!.toFloat())
                    putString("qrString", qrString)
                    putString("transferType", "qrisPayment")
                }
                Log.d("QrisPayment", "Navigating to PinVerificationFragment with amount=${amount!!.toFloat()}")
                findNavController().navigate(R.id.action_qrisPaymentFragment_to_pinVerificationFragment, bundle)
                isProcessingPayment = false
                binding.btnValidatePayment.isEnabled = true
                binding.loadingProgressBar.visibility = View.GONE
            }
        }

        setFragmentResultListener("pin_verification_result") { _, bundle ->
            val success = bundle.getBoolean("success", false)
            val errorMessage = bundle.getString("errorMessage")
            if (success) {
                Log.d("QrisPayment", "PIN verification successful, processing QRIS payment")
                userEmail?.let { email ->
                    orderId?.let { oid ->
                        amount?.let { amt ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    viewModel.processQrisPayment(email, oid, amt)
                                    withContext(Dispatchers.Main) {
                                        if (isAdded) {
                                            Toast.makeText(context, "Payment successful", Toast.LENGTH_SHORT).show()
                                            val db = FirebaseFirestore.getInstance()
                                            db.collection("qris_payments").document(oid)
                                                .update("status", "settlement")
                                                .addOnSuccessListener {
                                                    Log.d("QrisPayment", "Firestore payment status updated to settlement for orderId=$oid")
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("QrisPayment", "Failed to update Firestore payment status: ${e.message}")
                                                }
                                            findNavController().popBackStack(R.id.homeFragment, false)
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        if (isAdded) {
                                            Toast.makeText(context, "Payment failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            resetUI()
                                        }
                                    }
                                    Log.e("QrisPayment", "Payment processing error: ${e.message}", e)
                                }
                            }
                        } ?: run {
                            Log.e("QrisPayment", "Amount is null after PIN verification")
                            Toast.makeText(context, "Payment failed: Amount not found", Toast.LENGTH_LONG).show()
                            resetUI()
                        }
                    } ?: run {
                        Log.e("QrisPayment", "Order ID is null after PIN verification")
                        Toast.makeText(context, "Payment failed: Order ID not found", Toast.LENGTH_LONG).show()
                        resetUI()
                    }
                } ?: run {
                    Log.e("QrisPayment", "userEmail is null after PIN verification")
                    Toast.makeText(context, "Payment failed: User not found", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_qrisPaymentFragment_to_loginFragment)
                }
            } else {
                Log.e("QrisPayment", "PIN verification failed: $errorMessage")
                Toast.makeText(context, "Payment failed: $errorMessage", Toast.LENGTH_LONG).show()
                resetUI()
            }
        }

        viewModel.simulationResult.observe(viewLifecycleOwner) { result ->
            binding.loadingProgressBar.visibility = View.GONE
            binding.btnValidatePayment.isEnabled = true
            isProcessingPayment = false

            when (result) {
                is SimulationResult.Success -> {
                    Log.d("QrisPayment", "Simulation successful: ${result.message}")
                    Toast.makeText(context, "Payment successful: ${result.message}", Toast.LENGTH_SHORT).show()
                    userEmail?.let { email ->
                        val bundle = Bundle().apply { putString("userEmail", email) }
                        findNavController().navigate(R.id.action_qrisPaymentFragment_to_homeFragment, bundle)
                    } ?: findNavController().navigate(R.id.action_qrisPaymentFragment_to_loginFragment)
                }
                is SimulationResult.Failure -> {
                    Log.e("QrisPayment", "Simulation failed: ${result.message}")
                    Toast.makeText(context, "Payment failed: ${result.message}", Toast.LENGTH_LONG).show()
                    resetUI()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        userEmail?.let { email ->
            viewModel.fetchUser(email)
        }
    }

    private fun generateUniqueOrderId(): String {
        return "ORDER-${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
    }

    private fun resetUI() {
        orderId = null
        amount = null
        qrString = null
        binding.statusTextView.text = "Status: Scan or enter QR code"
        binding.amountTextView.visibility = View.GONE
        binding.btnValidatePayment.isEnabled = false
        isProcessingPayment = false
        binding.btnValidatePayment.isEnabled = true
        binding.loadingProgressBar.visibility = View.GONE
    }

    private suspend fun extractOrderIdAndAmount(qrContent: String) {
        try {
            Log.d("QrisPayment", "QR Content: $qrContent")
            qrString = qrContent
            val normalizedQrContent = qrContent.split("#")[0].trimEnd('/')

            if (qrContent.startsWith("http")) {
                val pattern = Pattern.compile("/qris/([\\w-]+)/qr-code")
                val matcher = pattern.matcher(qrContent)
                if (matcher.find()) {
                    orderId = matcher.group(1)
                    Log.d("QrisPayment", "Extracted orderId from URL: $orderId")
                    verifyQrisPayment(orderId!!)
                } else {
                    val db = FirebaseFirestore.getInstance()
                    userEmail?.let { email ->
                        var snapshot = db.collection("qris_payments")
                            .whereEqualTo("userEmail", email)
                            .whereEqualTo("qrString", normalizedQrContent)
                            .get()
                            .await()
                        if (snapshot.isEmpty) {
                            snapshot = db.collection("qris_payments")
                                .whereEqualTo("userEmail", email)
                                .whereEqualTo("qrString", qrContent)
                                .get()
                                .await()
                        }
                        if (!snapshot.isEmpty) {
                            val payment = snapshot.documents[0].toObject<QrisPayment>()
                            orderId = payment?.orderId
                            amount = payment?.amount
                            qrString = payment?.qrString
                            Log.d("QrisPayment", "Fetched from Firestore: orderId=$orderId, amount=$amount")
                            if (orderId != null) {
                                verifyQrisPayment(orderId!!)
                            } else {
                                withContext(Dispatchers.Main) {
                                    if (isAdded) {
                                        Toast.makeText(context, "Invalid QR: No order ID found in Firestore", Toast.LENGTH_LONG).show()
                                        binding.amountTextView.visibility = View.GONE
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    Toast.makeText(context, "No matching QRIS payment found", Toast.LENGTH_LONG).show()
                                    binding.amountTextView.visibility = View.GONE
                                }
                            }
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(context, "User email not found", Toast.LENGTH_LONG).show()
                                binding.amountTextView.visibility = View.GONE
                            }
                        }
                    }
                }
            } else if (qrContent.startsWith("000201")) {
                amount = parseQrisAmount(qrContent) ?: 10000.0
                if (amount == 0.0) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(context, "Invalid QRIS amount", Toast.LENGTH_LONG).show()
                            binding.amountTextView.visibility = View.GONE
                        }
                    }
                    return
                }
                orderId = generateUniqueOrderId()
                Log.d("QrisPayment", "Generated orderId=$orderId, amount=$amount")

                userEmail?.let { email ->
                    val db = FirebaseFirestore.getInstance()
                    val recentPayments = db.collection("qris_payments")
                        .whereEqualTo("userEmail", email)
                        .whereEqualTo("qrString", qrContent)
                        .whereGreaterThan("timestamp", Timestamp(Timestamp.now().seconds - 3600, Timestamp.now().nanoseconds))
                        .get()
                        .await()
                    if (!recentPayments.isEmpty) {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(context, "This QR code was recently used. Please scan a new code.", Toast.LENGTH_LONG).show()
                                binding.amountTextView.visibility = View.GONE
                            }
                        }
                        return
                    }

                    val payment = QrisPayment(
                        orderId = orderId!!,
                        userEmail = email,
                        qrString = qrContent,
                        amount = amount!!,
                        status = "pending",
                        timestamp = Timestamp.now()
                    )
                    saveToFirestore(payment)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(context, "User email not found", Toast.LENGTH_LONG).show()
                            binding.amountTextView.visibility = View.GONE
                        }
                    }
                    return
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        binding.statusTextView.text = "QR Valid: Order $orderId, Amount $amount"
                        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                            .format(amount)
                            .replace("IDR", "Rp ")
                        binding.amountTextView.text = "Amount: $formattedAmount"
                        binding.amountTextView.visibility = View.VISIBLE
                        binding.btnValidatePayment.isEnabled = true
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Unsupported QR format: $qrContent", Toast.LENGTH_LONG).show()
                        binding.amountTextView.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    Toast.makeText(context, "Error parsing QR: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.amountTextView.visibility = View.GONE
                }
            }
            Log.e("QrisPayment", "QR parsing error: ${e.message}", e)
        }
    }

    private fun verifyQrisPayment(orderId: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("QrisPayment", "Calling verifyQrisPayment for orderId: $orderId")
                val response = App.api.verifyQrisPayment(orderId)
                Log.d("QrisPayment", "Verify QRIS Response: $response")
                if (response.success && response.status == "pending") {
                    amount = response.amount?.toDouble() ?: 10000.0
                    if (amount == 0.0) {
                        withContext(Dispatchers.Main) {
                            if (isAdded && view != null) {
                                Toast.makeText(context, "Invalid amount from API", Toast.LENGTH_LONG).show()
                                this@QrisPaymentFragment.orderId = null
                                this@QrisPaymentFragment.amount = null
                                binding.statusTextView.text = "Status: Invalid QR"
                                binding.amountTextView.visibility = View.GONE
                                binding.btnValidatePayment.isEnabled = false
                            }
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            binding.statusTextView.text = "QR Valid: Order $orderId, Amount $amount"
                            val formattedAmount = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                                .format(amount)
                                .replace("IDR", "Rp ")
                            binding.amountTextView.text = "Amount: $formattedAmount"
                            binding.amountTextView.visibility = View.VISIBLE
                            binding.btnValidatePayment.isEnabled = true
                        }
                    }
                } else {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("qris_payments").document(orderId)
                        .update("status", response.status)
                        .await()
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            val message = when (response.status) {
                                "settlement" -> "This QRIS transaction has already been completed."
                                "expired" -> "This QRIS transaction has expired. Please generate a new QR code."
                                "unknown" -> "Transaction status unknown, please try again later."
                                else -> "Invalid QR: ${response.message}"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            this@QrisPaymentFragment.orderId = null
                            this@QrisPaymentFragment.amount = null
                            binding.statusTextView.text = "Status: Invalid QR"
                            binding.amountTextView.visibility = View.GONE
                            binding.btnValidatePayment.isEnabled = false
                        }
                    }
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No response body"
                Log.e("QrisPayment", "HTTP Error ${e.code()} for orderId=$orderId: $errorBody")
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        val message = if (e.code() == 404) {
                            "QRIS order not found. Ensure the server is running and the QR code is valid."
                        } else {
                            "Verification failed: HTTP ${e.code()}."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        this@QrisPaymentFragment.orderId = null
                        this@QrisPaymentFragment.amount = null
                        binding.statusTextView.text = "Status: Verification failed"
                        binding.amountTextView.visibility = View.GONE
                        binding.btnValidatePayment.isEnabled = false
                    }
                }
            } catch (e: Exception) {
                Log.e("QrisPayment", "Verification Error for orderId=$orderId: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        val errorMsg = when (e) {
                            is SecurityException -> "Permission denied. Please grant internet access."
                            else -> "Failed to verify QR: ${e.message}"
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        this@QrisPaymentFragment.orderId = null
                        this@QrisPaymentFragment.amount = null
                        binding.statusTextView.text = "Status: Verification failed"
                        binding.amountTextView.visibility = View.GONE
                        binding.btnValidatePayment.isEnabled = false
                    }
                }
            }
        }
    }

    private fun parseQrisAmount(qrContent: String): Double? {
        try {
            var remaining = qrContent
            while (remaining.isNotEmpty()) {
                if (remaining.length < 4) break
                val tag = remaining.substring(0, 2)
                val length = remaining.substring(2, 4).toIntOrNull() ?: break
                if (remaining.length < 4 + length) break
                val value = remaining.substring(4, 4 + length)
                remaining = if (remaining.length > 4 + length) remaining.substring(4 + length) else ""

                if (tag == "54") {
                    return value.toDoubleOrNull() ?: value.toIntOrNull()?.toDouble()
                }
            }
            return null
        } catch (e: Exception) {
            Log.e("QrisPayment", "Failed to parse QRIS Amount: ${e.message}")
            return null
        }
    }

    private fun processImageForQrCode(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val qrContent = barcodes[0].rawValue
                        if (qrContent != null) {
                            Log.d("QrisPayment", "Scanned QR Content from Image: $qrContent")
                            binding.qrUrlEditText.setText(qrContent)
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    extractOrderIdAndAmount(qrContent)
                                    uploadImageToImgur(uri)
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        if (isAdded) {
                                            binding.loadingProgressBar.visibility = View.GONE
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "No QR code detected in image", Toast.LENGTH_SHORT).show()
                            binding.amountTextView.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(context, "No QR code detected in image", Toast.LENGTH_SHORT).show()
                        binding.amountTextView.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("QrisPayment", "Image processing error: ${e.message}", e)
                    binding.amountTextView.visibility = View.GONE
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("QrisPayment", "Image processing error: ${e.message}", e)
            binding.amountTextView.visibility = View.GONE
        }
    }

    private fun downloadImageFromUrl(url: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = Picasso.get().load(url).get()
                if (bitmap != null) {
                    val file = File(requireContext().cacheDir, "qr_image.jpg")
                    FileOutputStream(file).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                    }
                    val uri = Uri.fromFile(file)
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            selectedImageUri = uri
                            binding.qrImageView.setImageURI(uri)
                            binding.loadingProgressBar.visibility = View.VISIBLE
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    processImageForQrCode(uri)
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        if (isAdded) {
                                            binding.loadingProgressBar.visibility = View.GONE
                                        }
                                    }
                                }
                            }
                            Toast.makeText(context, "QR image downloaded", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(context, "Failed to decode image", Toast.LENGTH_SHORT).show()
                            binding.amountTextView.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Failed to download QR: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.amountTextView.visibility = View.GONE
                    }
                }
                Log.e("QrisPayment", "Download error: ${e.message}", e)
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File {
        return requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            val file = File(requireContext().cacheDir, "qris_image_temp.jpg")
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            file
        } ?: throw IllegalStateException("Could not create file from URI")
    }

    private fun uploadImageToImgur(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = getFileFromUri(uri)
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

                val response = ImgurClient.apiService.uploadImage("Client-ID b1108bcbfd4178d", body)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (response.success) {
                            response.data?.link?.let { imageUrl ->
                                binding.statusTextView.text = "QR Uploaded: $imageUrl"
                                Toast.makeText(context, "QR image uploaded to Imgur", Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(context, "Failed to get Imgur URL", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Failed to upload to Imgur", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Imgur upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.e("QrisPayment", "Imgur upload error: ${e.message}", e)
            }
        }
    }

    private suspend fun saveToFirestore(payment: QrisPayment) {
        val db = FirebaseFirestore.getInstance()
        Log.d("QrisPayment", "Saving to Firestore: orderId=${payment.orderId}, amount=${payment.amount}")

        try {
            db.collection("qris_payments")
                .document(payment.orderId)
                .set(payment)
                .await()
            Log.d("Firestore", "Payment saved successfully with ID: ${payment.orderId}")
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    Toast.makeText(context, "Payment saved to Firestore", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error saving payment: ${e.message}", e)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    Toast.makeText(context, "Failed to save payment: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.btnValidatePayment.isEnabled = true
                    isProcessingPayment = false
                    binding.amountTextView.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}