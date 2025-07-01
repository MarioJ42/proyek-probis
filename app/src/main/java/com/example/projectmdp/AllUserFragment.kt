package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projectmdp.databinding.FragmentAllUserBinding

class AllUsersFragment : Fragment() {

    private var _binding: FragmentAllUserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }
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
        val userEmail = arguments?.getString(ARG_EMAIL) ?: ""
        if (userEmail.isEmpty()) {
            findNavController().navigate(R.id.action_global_loginFragment)
            return
        }
        val adapter = UserTableAdapter { user, newStatus ->
            viewModel.updateUserStatus(user.id, newStatus, userEmail)
        }
        binding.userRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        val statusOptions = listOf("All", "Active", "Inactive")
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusOptions
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.statusFilterSpinner.adapter = spinnerAdapter
        binding.statusFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateFilteredList(viewModel.users.value ?: emptyList())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        binding.btnsearch12.setOnClickListener {
            updateFilteredList(viewModel.users.value ?: emptyList())
        }
        viewModel.fetchAllUsersExceptAdmin(userEmail)
        viewModel.users.observe(viewLifecycleOwner) { users ->
            updateFilteredList(users)
        }
    }
    private fun updateFilteredList(allUsers: List<User>) {
        val query = binding.etsearch12.text.toString().trim()
        val statusFilter = binding.statusFilterSpinner.selectedItem.toString()

        val filtered = allUsers.filter { user ->
            val matchesQuery = user.email.contains(query, ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                "Active" -> user.status.equals("active", ignoreCase = true)
                "Inactive" -> user.status.equals("inactive", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesStatus
        }
        binding.userCountText.text = "Total Users: ${filtered.size}"
        (binding.userRecyclerView.adapter as? UserTableAdapter)?.submitList(filtered)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
