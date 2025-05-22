package com.example.projectmdp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentGetPremiumBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class GetPremiumFragment : Fragment() {
    private var _binding: FragmentGetPremiumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var selectedKtpUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d("GetPremiumFragment", "Storage permission granted")
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickKtpImageLauncher.launch(intent)
        } else {
            Log.e("GetPremiumFragment", "Storage permission denied")
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickKtpImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedKtpUri = uri
                Log.d("GetPremiumFragment", "KTP image selected: $uri")
                binding.ktpImageView.setImageURI(uri)
            } ?: run {
                Log.e("GetPremiumFragment", "Failed to pick KTP image: No URI returned")
                Toast.makeText(context, "Failed to pick KTP image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("GetPremiumFragment", "KTP image picker failed: resultCode=${result.resultCode}")
            Toast.makeText(context, "KTP image picker failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = arguments?.getString("userEmail")?.lowercase()
        if (userEmail != null) {
            viewModel.setUserEmail(userEmail)
        } else {
            Log.e("GetPremiumFragment", "No user email provided in arguments")
            Toast.makeText(context, "User email not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGetPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                Log.d("GetPremiumFragment", "User data received: email=${user.email}, balance=${user.balance}")
            } else {
                Log.e("GetPremiumFragment", "User data is null")
                Toast.makeText(context, "User data not found", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }

        viewModel.updatePremiumError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Log.e("GetPremiumFragment", "Update premium error: $error")
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                viewModel.clearUpdatePremiumError()
            }
        }

        viewModel.topUpResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is TopUpResult.Success -> {
                    Log.d("GetPremiumFragment", "Balance deduction successful: ${result.message}")
                    Toast.makeText(context, "Balance deducted successfully", Toast.LENGTH_SHORT).show()
                }
                is TopUpResult.Failure -> {
                    Log.e("GetPremiumFragment", "Balance deduction failed: ${result.message}")
                    Toast.makeText(context, "Balance deduction failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
                null -> {
                    Log.d("GetPremiumFragment", "Top-up result is null")
                }
            }
        }

        binding.btnUploadKtp.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        binding.btnSubmitPremium.setOnClickListener {
            if (selectedKtpUri == null) {
                Toast.makeText(context, "Please upload your KTP photo first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.VISIBLE }

                    // Step 1: Upload KTP photo to Imgur
                    val ktpUrl = uploadKtpPhotoToImgur(selectedKtpUri!!)
                    Log.d("GetPremiumFragment", "KTP URL after upload: $ktpUrl")

                    // Step 2: Check user and balance
                    val user = viewModel.user.value
                    if (user == null) {
                        Log.e("GetPremiumFragment", "User is null, cannot proceed")
                        throw Exception("User not found")
                    }
                    Log.d("GetPremiumFragment", "User fetched: email=${user.email}, balance=${user.balance}")
                    if (user.balance < 500000.0) {
                        Log.e("GetPremiumFragment", "Insufficient balance: ${user.balance}")
                        throw Exception("Insufficient balance (required: 500,000)")
                    }

                    // Step 3: Update premium status
                    Log.d("GetPremiumFragment", "Calling updatePremiumStatus with email=${user.email}, ktpUrl=$ktpUrl")
                    viewModel.updatePremiumStatus(user.email, ktpUrl, true)

                    // Step 4: Deduct balance
                    Log.d("GetPremiumFragment", "Deducting balance for email=${user.email}")
                    val orderId = "premium_${System.currentTimeMillis()}"
                    viewModel.topUpBalance(user.email, -500000.0, orderId)

                    // Step 5: Wait for updates and navigate
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.delay(2000) // Wait for Firestore to sync
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Premium request submitted successfully", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                } catch (e: Exception) {
                    Log.e("GetPremiumFragment", "Submit error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to submit premium request: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.GONE }
                }
            }
        }
    }

    private suspend fun uploadKtpPhotoToImgur(uri: Uri): String {
        val file = getFileFromUri(uri)
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
        Log.d("GetPremiumFragment", "Uploading KTP image to Imgur: ${file.absolutePath}")

        val response = ImgurClient.apiService.uploadImage("Client-ID b1108bcbfd4178d", body)
        Log.d("GetPremiumFragment", "Imgur response: success=${response.success}, status=${response.status}, data=${response.data}")

        if (response.success && response.data != null) {
            val photoUrl = response.data.link
            Log.d("GetPremiumFragment", "KTP image uploaded successfully, URL: $photoUrl")
            return photoUrl
        } else {
            Log.e("GetPremiumFragment", "Failed to upload KTP photo to Imgur: success=${response.success}, status=${response.status}")
            throw Exception("Failed to upload KTP photo to Imgur: status=${response.status}")
        }
    }

    private fun getFileFromUri(uri: Uri): File {
        Log.d("GetPremiumFragment", "Converting URI to File: $uri")
        return requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            val file = File(requireContext().cacheDir, "ktp_image_temp.jpg")
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            Log.d("GetPremiumFragment", "File created: ${file.absolutePath}, size=${file.length()} bytes")
            file
        } ?: run {
            Log.e("GetPremiumFragment", "Failed to create file from URI: $uri")
            throw IllegalStateException("Could not create file from URI")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}