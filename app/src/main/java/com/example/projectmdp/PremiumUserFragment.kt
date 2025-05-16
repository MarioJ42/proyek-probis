package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projectmdp.databinding.FragmentPremiumUserBinding

class PremiumUsersFragment : Fragment() {
    private var _binding: FragmentPremiumUserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }

    companion object {
        private const val ARG_EMAIL = "userEmail"
        fun newInstance(email: String): PremiumUsersFragment {
            val fragment = PremiumUsersFragment()
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
    ): View {
        _binding = FragmentPremiumUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString(ARG_EMAIL) ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_global_loginFragment)
            return
        }

        // Set up RecyclerView
        val adapter = UserTableAdapter { user, newStatus ->
            viewModel.updateUserStatus(user.id, newStatus, userEmail)
        }
        binding.userRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        // Fetch premium users
        viewModel.fetchPremiumUsersExceptAdmin(userEmail)

        // Observe premium users
        viewModel.premiumUsers.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            binding.userCountText.text = "Total Premium Users: ${users.size}"
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            (activity as? AdminActivity)?.let { adminActivity ->
                adminActivity.finish()
            }
            findNavController().popBackStack(R.id.loginFragment, true)
            findNavController().navigate(R.id.action_global_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}