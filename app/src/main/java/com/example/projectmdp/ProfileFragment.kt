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
                binding.imageView2.setImageURI(uri)
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

        // Ensure editTextText is editable and focusable
        binding.editTextText.isEnabled = true
        binding.editTextText.isFocusable = true
        binding.editTextText.isFocusableInTouchMode = true
        binding.editTextText.requestFocus() // Force focus to ensure keyboard appears
        Log.d("ProfileFragment", "editTextText state: enabled=${binding.editTextText.isEnabled}, focusable=${binding.editTextText.isFocusable}")

        viewModel.userEmail.observe(viewLifecycleOwner) { email ->
            if (email != null) {
                userEmail = email
                Log.d("ProfileFragment", "Observing user email: $email")
                viewModel.fetchUser(email)
            } else {
                Log.e("ProfileFragment", "User email is null, navigating to login")
                Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                Log.d("ProfileFragment", "User data received: email=${user.email}, phone=${user.phone}, photoUrl=${user.photoUrl}")
                binding.editTextText3.setText(user.fullName)
                binding.editTextText2.setText(user.email)
                binding.editTextText.setText(user.phone)
                if (user.photoUrl.isNotEmpty()) {
                    Glide.with(binding.imageView2)
                        .load(user.photoUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(binding.imageView2)
                } else {
                    binding.imageView2.setImageResource(R.drawable.profile)
                }
            } else {
                Log.e("ProfileFragment", "User data is null, navigating to login")
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }
        }

        viewModel.premiumStatus.observe(viewLifecycleOwner) { premium ->
            if (premium != null) {
                binding.buttonPremium.isEnabled = !premium.premium
                binding.buttonPremium.text = if (premium.premium) "Premium Activated" else "Get Premium"
            }
        }

        binding.button2.setOnClickListener {
            val newFullName = binding.editTextText3.text.toString()
            val newEmail = binding.editTextText2.text.toString()
            val newPhone = binding.editTextText.text.toString()

            Log.d("ProfileFragment", "Save clicked: newFullName=$newFullName, newEmail=$newEmail, newPhone=$newPhone")

            if (newFullName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
                Log.w("ProfileFragment", "Validation failed: Empty fields")
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            userEmail?.let { email ->
                val currentPhotoUrl = viewModel.user.value?.photoUrl ?: ""
                viewModel.updateUserProfile(email, newFullName, newEmail, currentPhotoUrl, newPhone)
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
            } ?: Log.e("ProfileFragment", "Cannot update profile: userEmail is null")
        }

        binding.button6.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonPremium.setOnClickListener {
            userEmail?.let { email ->
                val bundle = Bundle().apply {
                    putString("userEmail", email)
                }
                findNavController().navigate(R.id.getPremiumFragment, bundle)
            } ?: Log.e("ProfileFragment", "Cannot navigate to GetPremiumFragment: userEmail is null")
        }

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
                    viewModel.updateUserProfile(email, binding.editTextText3.text.toString(), binding.editTextText2.text.toString(), photoUrl)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Photo uploaded to Imgur", Toast.LENGTH_SHORT).show()
                        Glide.with(binding.imageView2)
                            .load(photoUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .into(binding.imageView2)
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