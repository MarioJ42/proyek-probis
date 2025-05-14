package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentHomeBinding
import java.text.NumberFormat
import java.util.Locale

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        Log.d("HomeFragment", "Received userEmail: $userEmail")
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            return
        }

        viewModel.fetchUser(userEmail)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.username.text = "Hello, ${user.fullName}!" + if (user.premium) " (Premium)" else ""
            } else {
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
        }

        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            val formattedBalance = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                .format(balance)
                .replace("IDR", "Rp ")
            binding.balanceTextView.text = formattedBalance
            Log.d("HomeFragment", "Balance updated: $balance")
        }

        binding.topUp.setOnClickListener {
            viewModel.topUp(userEmail, 1000000.0)
        }

        binding.transfer.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_transferFragment, bundle)
        }

        binding.history.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_historyFragment, bundle)
        }

        binding.investasi.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_investasiTabunganFragment, bundle)
        }

        binding.qris.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_qrisPaymentFragment, bundle)
        }

        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.userEmail = null
            }
            findNavController().popBackStack(R.id.loginFragment, true)
            findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}