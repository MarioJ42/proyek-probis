package com.example.projectmdp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.projectmdp.databinding.FragmentRequestPremiumBinding
import com.example.projectmdp.databinding.FragmentUserDetailPremiumBinding
import kotlin.getValue


class UserDetailPremiumFragment : Fragment() {
    private var _binding: FragmentUserDetailPremiumBinding? = null
    private val binding get() = _binding!!
    private val args: UserDetailPremiumFragmentArgs by navArgs()
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentUserDetailPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adminEmail = args.userEmail
        val bundle = Bundle().apply {
            putString("userEmail", adminEmail)
        }
        viewModel.getUserrequestByEmail(adminEmail)
        viewModel.selectedPremiumRequest.observe(viewLifecycleOwner) { user ->
            if (user != null) {
//                binding.Fullnameet.text = user.fullName
                binding.Emailet.text = user.userEmail
//                binding.Phoneet.text = user.phone
                Glide.with(requireContext())
                    .load(user.ktpPhoto)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_error_image)
                    .into(binding.imageView)
            } else {
                binding.Fullnameet.text = "User not found"
                binding.Emailet.text = adminEmail
                binding.Phoneet.text = "-"
            }
            binding.AccBtn.setOnClickListener{
                viewModel.acceptPremiumRequest(user!!.id, adminEmail)
                Toast.makeText(context, "Accepting request for ${user.userEmail}", Toast.LENGTH_SHORT).show()
            }
            binding.Decbtn.setOnClickListener{
                viewModel.rejectPremiumRequest(user!!.id, adminEmail)
                Toast.makeText(context, "Rejecting request for ${user.userEmail}", Toast.LENGTH_SHORT).show()

            }
        }

        viewModel.getUserByEmail(adminEmail)
        viewModel.selectedUser.observe(viewLifecycleOwner){ user ->
            if(user!= null){
                binding.Fullnameet.text = user.fullName
                binding.Phoneet.text = user.phone
            }
        }
        binding.BackBtn.setOnClickListener{
            findNavController().popBackStack()
        }


    }
}