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

    // Gunakan key ARG_EMAIL konsisten untuk ambil argumen email
    companion object {
        private const val ARG_EMAIL = "userEmail"
        fun newInstance(email: String): AllUsersFragment {
            val fragment = AllUsersFragment()
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
        _binding = FragmentAllUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ambil email user dari arguments pakai key ARG_EMAIL
        val userEmail = arguments?.getString(ARG_EMAIL) ?: ""

        if (userEmail.isEmpty()) {
            // Jika kosong, langsung navigasi ke login
            findNavController().navigate(R.id.action_global_loginFragment)
            return
        }

        // Setup RecyclerView dan adapter
        val adapter = UserTableAdapter { user, newStatus ->
            viewModel.updateUserStatus(user.id, newStatus, userEmail)
        }
        binding.userRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        // Fetch data user (kecuali admin)
        viewModel.fetchAllUsersExceptAdmin(userEmail)

        // Observe LiveData users
        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            binding.userCountText.text = "Total Users: ${users.size}"
        }

        // Setup search button
        binding.btnsearch12.setOnClickListener {
            val query = binding.etsearch12.text.toString().trim()
            val currentUsers = viewModel.users.value ?: emptyList()
            if (query.isNotEmpty()) {
                val filteredUsers = currentUsers.filter { it.email.contains(query, ignoreCase = true) }
                adapter.submitList(filteredUsers)
                binding.userCountText.text = "Total Users: ${filteredUsers.size}"
            } else {
                adapter.submitList(currentUsers)
                binding.userCountText.text = "Total Users: ${currentUsers.size}"
            }
        }


        // Jika ingin logout, bisa aktifkan ini
        /*
        binding.btnLogout.setOnClickListener {
            // Contoh logout: navigasi ke login dan clear back stack
            findNavController().navigate(R.id.action_global_loginFragment)
        }
        */
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
