package com.example.projectmdp

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.ActivityAdminBinding
import com.example.projectmdp.databinding.FragmentAdminProfileBinding
import com.example.projectmdp.databinding.FragmentAllUserBinding
import kotlin.getValue


class AdminProfileFragment : Fragment() {
    private var _binding: FragmentAdminProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }
    companion object {
        private const val ARG_EMAIL = "userEmail"
        fun newInstance(email: String): AdminProfileFragment {
            val fragment = AdminProfileFragment()
            val args = Bundle().apply {
                putString(ARG_EMAIL, email)
            }
            fragment.arguments = args
            return fragment
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAdminProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userEmail = arguments?.getString(ARG_EMAIL) ?: ""

        if (userEmail.isEmpty()) {
            // Jika kosong, langsung navigasi ke login
            findNavController().navigate(R.id.action_global_loginFragment)
            return
        }
        binding.editTextText2.setText(userEmail)
        binding.button3.setOnClickListener{
            (activity as? AdminActivity)?.let { adminActivity ->
                adminActivity.userEmail = null
            }
            findNavController().popBackStack(R.id.loginFragment, true)
            findNavController().navigate(R.id.loginFragment)
        }
    }

}