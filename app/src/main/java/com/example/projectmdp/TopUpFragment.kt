package com.example.projectmdp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentTopUpBinding
import com.example.projectmdp.network.TopUpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.util.concurrent.TimeoutException

class TopUpFragment : Fragment() {
    private var _binding: FragmentTopUpBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var orderId: String? = null
    private var amount: Double? = null
    private var snapToken: String? = null
    private var verifyJob: Job? = null
    private var isProcessingPayment = false
    private var hasSimulated = false
    private var progressDialog: AlertDialog? = null
    private lateinit var paymentScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TopUpFragment", "onCreate called")
        paymentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TopUpFragment", "onViewCreated called")

        val userEmail = arguments?.getString("userEmail")?.lowercase() ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_topUpFragment_to_loginFragment)
            return
        }

        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED

        progressDialog = AlertDialog.Builder(requireContext())
            .setMessage("Processing payment, please wait...")
            .setCancelable(false)
            .create()

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)

            webViewClient = object : WebViewClient() {
                private var lastProcessedUrl: String? = null
                private var lastRedirectTime: Long = 0
                private val debounceInterval = 2000L

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastRedirectTime < debounceInterval) {
                        Log.d("WebView", "Debouncing redirect URL: $url")
                        return true
                    }
                    lastRedirectTime = currentTime

                    if (url == lastProcessedUrl) {
                        Log.d("WebView", "Skipping duplicate redirect URL: $url")
                        return true
                    }
                    lastProcessedUrl = url
                    Log.d("WebView", "Redirect URL: $url")

                    when {
                        url.contains("success") || url.contains("finish") || url.contains("transaction_status=settlement") -> {
                            val localOrderId = orderId
                            val localAmount = amount
                            if (localOrderId != null && localAmount != null && !viewModel.isOrderSimulated(localOrderId)) {
                                Log.d(
                                    "TopUpFragment",
                                    "Payment successful, triggering simulation for orderId=$localOrderId"
                                )
                                verifyJob?.cancel()
                                simulatePayment(localOrderId, localAmount, userEmail)
                            }
                            return true
                        }

                        url.contains("unfinish") || url.contains("error") -> {
                            verifyJob?.cancel()
                            Toast.makeText(
                                requireContext(),
                                "Payment cancelled or failed",
                                Toast.LENGTH_SHORT
                            ).show()
                            resetUI(userEmail)
                            return true
                        }

                        else -> return false
                    }
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("WebView", "Page started: $url")
                    isProcessingPayment = true
                    if (isAdded) {
                        binding.loadingProgressBar.visibility = View.VISIBLE
                        progressDialog?.show()
                    }
                    view?.postDelayed({
                        if (isProcessingPayment) {
                            view.evaluateJavascript("javascript:void(0);", null)
                            Log.d("WebView", "Keep-alive ping sent to $url")
                        }
                    }, 30000)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Page finished loading: $url")
                    if (isAdded) {
                        binding.loadingProgressBar.visibility = View.GONE
                        progressDialog?.dismiss()
                    }
                    if (orderId != null && verifyJob == null && isProcessingPayment &&
                        !url?.contains("success")!! && !url.contains("finish") &&
                        !url.contains("transaction_status=settlement") && !url.contains("error") &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        verifyJob = paymentScope.launch {
                            verifyPaymentStatus(orderId!!, userEmail)
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e(
                        "WebView",
                        "Error loading URL: ${request?.url}, description: ${error?.description}, code: ${error?.errorCode}"
                    )
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                verifyJob?.cancel()
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to load payment page: ${error?.description}",
                                    Toast.LENGTH_LONG
                                ).show()
                                resetUI(userEmail)
                            }
                        }
                    }
                }
            }
        }

        binding.btnBack.setOnClickListener {
            if (isProcessingPayment || binding.loadingProgressBar.visibility == View.VISIBLE) {
                Toast.makeText(
                    requireContext(),
                    "Please wait, payment is processing",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
            findNavController().navigate(R.id.action_topUpFragment_to_homeFragment, bundle)
        }

        binding.btnTopUp.setOnClickListener {
            amount = binding.inputTopUpAmount.text.toString().toDoubleOrNull()
            val localAmount = amount
            if (localAmount == null || localAmount < 1000) {
                Toast.makeText(
                    requireContext(),
                    "Minimum top-up amount is 1000",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            binding.loadingProgressBar.visibility = View.VISIBLE
            binding.btnTopUp.isEnabled = false
            binding.webView.visibility = View.GONE
            isProcessingPayment = true
            hasSimulated = false
            progressDialog?.show()

            paymentScope.launch(Dispatchers.IO) {
                try {
                    val response = App.api.generateTopUpSnap(
                        TopUpRequest(
                            user_email = userEmail,
                            amount = localAmount.toInt()
                        )
                    )
                    Log.d(
                        "TopUpFragment",
                        "generateTopUpSnap response: success=${response.success}, amount=${response.amount}"
                    )
                    if (response.success && response.amount == localAmount.toInt()) {
                        orderId = response.order_id
                        snapToken = response.snap_token
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                binding.webView.visibility = View.VISIBLE
                                val snapUrl =
                                    "https://app.sandbox.midtrans.com/snap/v2/vtweb/${response.snap_token}"
                                Log.d("TopUpFragment", "Loading Snap URL: $snapUrl")
                                binding.webView.loadUrl(snapUrl)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    response.message ?: "Failed to initiate payment",
                                    Toast.LENGTH_LONG
                                ).show()
                                resetUI(userEmail)
                            }
                        }
                    }
                } catch (e: HttpException) {
                    val errorBody = e.response()?.errorBody()?.string() ?: "Unknown error"
                    Log.e("TopUpFragment", "HTTP Error ${e.code()}: $errorBody")
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "HTTP ${e.code()}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            resetUI(userEmail)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TopUpFragment", "Error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            resetUI(userEmail)
                        }
                    }
                }
            }
        }

        viewModel.topUpResult.observe(viewLifecycleOwner) { result ->
            if (isAdded) {
                binding.loadingProgressBar.visibility = View.GONE
                binding.btnTopUp.isEnabled = true
                isProcessingPayment = false
                progressDialog?.dismiss()
                when (result) {
                    is TopUpResult.Success -> {
                        Toast.makeText(
                            requireContext(),
                            "Top-up successful",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.webView.visibility = View.GONE
                        val bundle = Bundle().apply { putString("userEmail", userEmail) }
                        findNavController().navigate(R.id.action_topUpFragment_to_homeFragment, bundle)
                    }
                    is TopUpResult.Failure -> {
                        Toast.makeText(
                            requireContext(),
                            "Top-up failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI(userEmail)
                    }
                }
            }
        }

        viewModel.simulationResult.observe(viewLifecycleOwner) { result ->
            if (isAdded) {
                binding.loadingProgressBar.visibility = View.GONE
                binding.btnTopUp.isEnabled = true
                isProcessingPayment = false
                progressDialog?.dismiss()
                val localAmount = amount ?: 0.0
                when (result) {
                    is SimulationResult.Success -> {
                        Log.d("TopUpFragment", "Simulation success: ${result.message}")
                        orderId?.let {
                            viewModel.topUpBalance(userEmail, localAmount, it)
                        } ?: run {
                            Log.e("TopUpFragment", "Order ID is null during simulation success")
                            Toast.makeText(
                                requireContext(),
                                "Top-up failed: Order ID not found",
                                Toast.LENGTH_LONG
                            ).show()
                            resetUI(userEmail)
                        }
                    }

                    is SimulationResult.Failure -> {
                        Log.e("TopUpFragment", "Simulation failed: ${result.message}")
                        Toast.makeText(
                            requireContext(),
                            "Payment failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI(userEmail)
                    }
                }
            }
        }
    }

    private fun resetUI(userEmail: String) {
        if (isAdded && binding.webView.visibility == View.VISIBLE) {
            binding.loadingProgressBar.visibility = View.GONE
            binding.btnTopUp.isEnabled = true
            binding.webView.visibility = View.GONE
            binding.webView.stopLoading()
            binding.inputTopUpAmount.text?.clear()
            orderId = null
            amount = null
            snapToken = null
            isProcessingPayment = false
            hasSimulated = false
            verifyJob?.cancel()
            verifyJob = null
            progressDialog?.dismiss()
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun simulatePayment(orderId: String, amount: Double, userEmail: String) {
        if (viewModel.isOrderSimulated(orderId)) {
            Log.d("TopUpFragment", "Skipping redundant simulation for orderId=$orderId")
            return
        }
        hasSimulated = true
        paymentScope.launch(Dispatchers.IO) {
            try {
                val response = App.api.simulateQrisPayment(
                    com.example.projectmdp.network.SimulateQrisRequest(order_id = orderId, amount = amount)
                )
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (response.success && response.status == "settlement") {
                            viewModel.simulateQrisPayment(orderId, amount, userEmail)
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Simulation failed: ${response.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            resetUI(userEmail)
                        }
                    }
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Unknown error"
                Log.e("TopUpFragment", "Simulation error: HTTP ${e.code()} $errorBody")
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Simulation error: HTTP ${e.code()} ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI(userEmail)
                    }
                }
            } catch (e: Exception) {
                Log.e("TopUpFragment", "Simulation error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Simulation error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI(userEmail)
                    }
                }
            }
        }
    }

    private suspend fun verifyPaymentStatus(orderId: String, userEmail: String) {
        val maxAttempts = 20
        val delayBetweenAttempts = 3000L
        val timeoutMillis = 60000L

        try {
            withTimeout(timeoutMillis) {
                repeat(maxAttempts) { attempt ->
                    if (!isActive) {
                        Log.d("TopUpFragment", "Verification coroutine cancelled at attempt ${attempt + 1}")
                        return@withTimeout
                    }
                    try {
                        val response = App.api.verifyQrisPayment(orderId)
                        Log.d("TopUpFragment", "Verify attempt ${attempt + 1}: status=${response.status}, amount=${response.amount}")
                        if (response.success && (response.status == "settlement" || response.status == "capture")) {
                            withContext(Dispatchers.Main) {
                                if (isAdded && !viewModel.isOrderSimulated(orderId)) {
                                    Log.d("TopUpFragment", "Payment settled, triggering simulation for orderId=$orderId")
                                    simulatePayment(orderId, response.amount?.toDouble() ?: 0.0, userEmail)
                                }
                            }
                            return@withTimeout
                        } else if (!response.success) {
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    verifyJob?.cancel()
                                    Toast.makeText(
                                        requireContext(),
                                        "Verification failed: ${response.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    resetUI(userEmail)
                                }
                            }
                            return@withTimeout
                        }
                    } catch (e: Exception) {
                        Log.e("TopUpFragment", "Verification error on attempt ${attempt + 1}: ${e.message}", e)
                        if (attempt == maxAttempts - 1) {
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    verifyJob?.cancel()
                                    Toast.makeText(
                                        requireContext(),
                                        "Verification failed after $maxAttempts attempts: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    resetUI(userEmail)
                                }
                            }
                        }
                    }
                    delay(delayBetweenAttempts)
                }
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        verifyJob?.cancel()
                        Toast.makeText(
                            requireContext(),
                            "Payment verification failed after $maxAttempts attempts",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI(userEmail)
                    }
                }
            }
        } catch (e: TimeoutException) {
            Log.e("TopUpFragment", "Verification timed out after $timeoutMillis ms")
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    verifyJob?.cancel()
                    Toast.makeText(
                        requireContext(),
                        "Payment verification timed out. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    resetUI(userEmail)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("TopUpFragment", "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("TopUpFragment", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("TopUpFragment", "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("TopUpFragment", "onStop called")
    }

    override fun onDestroyView() {
        Log.d("TopUpFragment", "onDestroyView called")
        verifyJob?.cancel()
        binding.webView.stopLoading()
        binding.webView.destroy()
        progressDialog?.dismiss()
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        Log.d("TopUpFragment", "onDestroy called")
        paymentScope.cancel()
        super.onDestroy()
    }
}