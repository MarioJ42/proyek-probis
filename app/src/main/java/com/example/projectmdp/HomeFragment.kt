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
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }

    private var isBalanceHidden: Boolean = false
    private var currentBalanceValue: Double = 0.0
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

        if (viewModel.userEmail.value != userEmail.lowercase()) {
            viewModel.fetchUser(userEmail)
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                val greeting = if (user.role == 1) {
                    "Hello Admin, ${user.fullName}!"
                } else {
                    "Hello, ${user.fullName}!" + if (user.premium) " (Premium)" else ""
                }
                binding.username.text = greeting
            } else {
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
        }

        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            currentBalanceValue = balance
            updateBalanceDisplay()
            Log.d("HomeFragment", "Balance updated: $balance")
        }

        binding.balanceVisibilityToggle.setOnClickListener {
            isBalanceHidden = !isBalanceHidden
            updateBalanceDisplay()
        }

        binding.topUp.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_topUpFragment, bundle)
        }

        binding.transfer.setOnClickListener {
            val bundle = Bundle().apply {
                putString("userEmail", userEmail)
            }
            findNavController().navigate(R.id.action_homeFragment_to_transferFragment, bundle)
        }

        binding.history.setOnClickListener {
            val bundle = Bundle().apply { putString("userEmail", userEmail) }
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
            viewModel.clearRememberMe()
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.userEmail = null
            }
            findNavController().popBackStack(R.id.loginFragment, true)
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun updateBalanceDisplay() {
        if (isBalanceHidden) {
            binding.balanceTextView.text = "Rp *********"
            binding.balanceVisibilityToggle.setImageResource(R.drawable.ic_visibility_off)
        } else {
            val formattedBalance = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                .format(currentBalanceValue)
                .replace("IDR", "Rp ")
            binding.balanceTextView.text = formattedBalance
            binding.balanceVisibilityToggle.setImageResource(R.drawable.ic_visibility)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}