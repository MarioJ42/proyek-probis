package com.example.projectmdp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.projectmdp.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private var selectedImageUri: Uri? = null
    private var userEmail: String? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d("ProfileFragment", "Storage permission granted")
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickImageLauncher.launch(intent)
        } else {
            Log.e("ProfileFragment", "Storage permission denied")
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                Log.d("ProfileFragment", "Image selected: $uri")
                binding.imageViewProfile.setImageURI(uri)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.VISIBLE }
                        uploadProfilePhotoToImgur(uri)
                    } finally {
                        withContext(Dispatchers.Main) { binding.loadingProgressBar.visibility = View.GONE }
                    }
                }
            } ?: run {
                Log.e("ProfileFragment", "Failed to pick image: No URI returned")
                Toast.makeText(context, "Failed to pick image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("ProfileFragment", "Image picker failed: resultCode=${result.resultCode}")
            Toast.makeText(context, "Image picker failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userEmail = arguments?.getString("userEmail")
        if (userEmail != null) {
            Log.d("ProfileFragment", "User email received: $userEmail")
            viewModel.setUserEmail(userEmail!!)
        } else {
            Log.e("ProfileFragment", "No user email provided in arguments")
            Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.user.observe(viewLifecycleOwner, { user ->
            if (user != null) {
                Log.d("ProfileFragment", "User data received: email=${user.email}, phone=${user.phone}, photoUrl=${user.photoUrl}")
                binding.etFullName.setText(user.fullName)
                binding.etEmail.setText(user.email)
                binding.etPhone.setText(user.phone)

                if (user.photoUrl.isNotEmpty()) {
                    Glide.with(binding.imageViewProfile)
                        .load(user.photoUrl)
                        .placeholder(R.drawable.ic_default_profile_image)
                        .error(R.drawable.ic_default_profile_image)
                        .into(binding.imageViewProfile)
                } else {
                    binding.imageViewProfile.setImageResource(R.drawable.ic_default_profile_image)
                }
            } else {
                Log.e("ProfileFragment", "User data is null, navigating to login")
                Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }
        })

        viewModel.premiumStatus.observe(viewLifecycleOwner) { premium ->
            if (premium != null) {
                binding.btnGetPremium.isEnabled = !premium.premium
                binding.btnGetPremium.text = if (premium.premium) "Premium Activated" else "Get Premium"
            }
        }

        // Phone number formatting and validation
        binding.etPhone.addTextWatcher {
            val digitsOnly = it.replace(Regex("[^0-9]"), "")
            if (digitsOnly.length > 12) {
                binding.etPhone.setText(digitsOnly.substring(0, 12))
                binding.etPhone.setSelection(12)
            }
        }

        // Save Profile Button
        binding.btnSaveProfile.setOnClickListener {
            val newFullName = binding.etFullName.text.toString().trim()
            val newEmail = binding.etEmail.text.toString().trim()
            val newPhone = binding.etPhone.text.toString().trim()

            Log.d("ProfileFragment", "Save clicked: newFullName=$newFullName, newEmail=$newEmail, newPhone=$newPhone")

            if (newFullName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
                Log.w("ProfileFragment", "Validation failed: Empty fields")
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val phoneDigits = newPhone.replace(Regex("[^0-9]"), "")
            if (phoneDigits.length != 12) {
                Log.w("ProfileFragment", "Validation failed: Phone must be exactly 12 digits")
                Toast.makeText(requireContext(), "Phone number must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            userEmail?.let { email ->
                val currentPhotoUrl = viewModel.user.value?.photoUrl ?: ""
                viewModel.updateUserProfile(email, newFullName, newEmail, currentPhotoUrl, newPhone)
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.delay(1000)
                    }
                    viewModel.fetchUser(newEmail.lowercase())
                }
            } ?: Log.e("ProfileFragment", "Cannot update profile: userEmail is null")
        }

        // Back Button (header)
        binding.btnBackHeader.setOnClickListener {
            findNavController().navigateUp()
        }

        // Get Premium Button
        binding.btnGetPremium.setOnClickListener {
            userEmail?.let { email ->
                val bundle = Bundle().apply {
                    putString("userEmail", email)
                }
                findNavController().navigate(R.id.getPremiumFragment, bundle)
            } ?: Log.e("ProfileFragment", "Cannot navigate to GetPremiumFragment: userEmail is null")
        }

        // Upload Photo Button
        binding.btnUploadPhoto.setOnClickListener {
            Log.d("ProfileFragment", "Upload photo button clicked")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File {
        Log.d("ProfileFragment", "Converting URI to File: $uri")
        return requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            val file = File(requireContext().cacheDir, "profile_image_temp.jpg")
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            Log.d("ProfileFragment", "File created: ${file.absolutePath}, size=${file.length()} bytes")
            file
        } ?: run {
            Log.e("ProfileFragment", "Failed to create file from URI: $uri")
            throw IllegalStateException("Could not create file from URI")
        }
    }

    private suspend fun uploadProfilePhotoToImgur(uri: Uri) {
        try {
            val file = getFileFromUri(uri)
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
            Log.d("ProfileFragment", "Uploading image to Imgur: ${file.absolutePath}")

            val response = ImgurClient.apiService.uploadImage("Client-ID b1108bcbfd4178d", body)
            Log.d("ProfileFragment", "Imgur response: success=${response.success}, data=${response.data}")

            if (response.success && response.data != null) {
                val photoUrl = response.data.link
                Log.d("ProfileFragment", "Image uploaded successfully, URL: $photoUrl")
                userEmail?.let { email ->
                    viewModel.updateUserProfile(email, binding.etFullName.text.toString(), binding.etEmail.text.toString(), photoUrl, binding.etPhone.text.toString())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Photo uploaded to Imgur", Toast.LENGTH_SHORT).show()
                        Glide.with(binding.imageViewProfile)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_default_profile_image)
                            .error(R.drawable.ic_default_profile_image)
                            .into(binding.imageViewProfile)
                    }
                } ?: Log.e("ProfileFragment", "Cannot update profile: userEmail is null")
            } else {
                Log.e("ProfileFragment", "Imgur upload failed: success=${response.success}, data=${response.data}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to upload to Imgur", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Imgur upload error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Imgur upload error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun EditText.addTextWatcher(block: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            block(s.toString())
        }
    })
}