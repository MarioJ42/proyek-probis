package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projectmdp.databinding.FragmentAllUserBinding

class AllUsersFragment : Fragment() {
    private var _binding: FragmentAllUserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }

//    companion object {
//        private const val ARG_EMAIL = "userEmail"
//        fun newInstance(email: String): AllUsersFragment {
//            val fragment = AllUsersFragment()
//            val args = Bundle().apply {
//                putString(ARG_EMAIL, email)
//            }
//            fragment.arguments = args
//            return fragment
//        }
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
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


        // Fetch users
        viewModel.fetchAllUsersExceptAdmin(userEmail)

        // Observe users
        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            binding.userCountText.text = "Total Users: ${users.size}"
            binding.btnsearch12.setOnClickListener {
                val query = binding.etsearch12.text.toString().trim()
                if (query.isNotEmpty()) {
                    val filteredUsers = users.filter { it.email.contains(query, ignoreCase = true) }
                    adapter.submitList(filteredUsers)
                    binding.userCountText.text = "Total Users: ${filteredUsers.size}"
                } else {
                    adapter.submitList(users)
                    binding.userCountText.text = "Total Users: ${users.size}"
                }
            }
        }

        // Logout button
//        binding.btnLogout.setOnClickListener {

//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}