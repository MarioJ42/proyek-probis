package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projectmdp.databinding.FragmentRequestPremiumBinding
class RequestPremiumFragment : Fragment() {
    private var _binding: FragmentRequestPremiumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }
    private var adminEmail: String = ""

    private lateinit var premiumRequestAdapter: PremiumRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adminEmail = arguments?.getString("userEmail") ?: ""
        if (adminEmail.isEmpty()) {
            Log.e("RequestPremiumFragment", "Admin email not provided, navigating up")
            Toast.makeText(context, "Admin email not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.progressBar.visibility = View.VISIBLE

        viewModel.fetchAllUsersExceptAdmin(adminEmail)
        viewModel.fetchPremiumRequestsExceptAdmin(adminEmail)

        viewModel.premiumRequests.observe(viewLifecycleOwner) { requests ->
            binding.progressBar.visibility = View.GONE
            if (requests != null) {
                Log.d("RequestPremiumFragment", "Fetched ${requests.size} premium requests")
                premiumRequestAdapter.submitList(requests)
                if (requests.isEmpty()) {
                    Toast.makeText(context, "No premium requests found.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("RequestPremiumFragment", "Premium requests data is null")
                Toast.makeText(context, "Failed to load premium requests.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.updatePremiumError?.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearUpdatePremiumError()
            }
        }


    }

    private fun setupRecyclerView() {
        premiumRequestAdapter = PremiumRequestAdapter(
            onAcceptClick = { request ->
                binding.progressBar.visibility = View.VISIBLE
                viewModel.acceptPremiumRequest(request.id, adminEmail)
                Toast.makeText(context, "Accepting request for ${request.userEmail}", Toast.LENGTH_SHORT).show()
            },
            onRejectClick = { request ->

                binding.progressBar.visibility = View.VISIBLE
                viewModel.rejectPremiumRequest(request.id, adminEmail)
                Toast.makeText(context, "Rejecting request for ${request.userEmail}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.premiumRequestRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = premiumRequestAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}