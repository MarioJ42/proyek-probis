package com.example.projectmdp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.projectmdp.databinding.FragmentRequestPremiumBinding
import kotlinx.coroutines.launch

class RequestPremiumFragment : Fragment() {
    private var _binding: FragmentRequestPremiumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels { AdminViewModelFactory() }
    private var adminEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adminEmail = arguments?.getString("userEmail") ?: ""
        if (adminEmail.isEmpty()) {
            Log.e("RequestPremiumFragment", "Admin email not provided")
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

        binding.progressBar.visibility = View.VISIBLE
        viewModel.fetchAllUsersExceptAdmin(adminEmail) // Pre-fetch users for caching
        viewModel.fetchPremiumRequestsExceptAdmin(adminEmail)

        viewModel.premiumRequests.observe(viewLifecycleOwner) { requests ->
            binding.progressBar.visibility = View.GONE
            if (requests != null) {
                Log.d("RequestPremiumFragment", "Fetched ${requests.size} premium requests")
                displayPremiumRequests(requests)
            } else {
                Log.e("RequestPremiumFragment", "No premium requests fetched")
                Toast.makeText(context, "No premium requests found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun displayPremiumRequests(requests: List<PremiumRequest>) {
        val tableLayout = binding.tableLayout
        tableLayout.removeAllViews()

        // Add header row
        val headerRow = TableRow(context)
        headerRow.addView(createTextView("Name"))
        headerRow.addView(createTextView("Email"))
        headerRow.addView(createTextView("KTP Photo"))
        headerRow.addView(createTextView("Premium Status"))
        headerRow.addView(createTextView("Action"))
        tableLayout.addView(headerRow)

        // Add data rows
        requests.forEach { request ->
            val row = TableRow(context)
            val user = viewModel.getUserByEmail(request.userEmail)
            val name = user?.fullName ?: "Unknown"

            row.addView(createTextView(name))
            row.addView(createTextView(request.userEmail))
            row.addView(createImageView(request.ktpPhoto))
            row.addView(createTextView(request.premium.toString()))
            row.addView(createActionButtons(request))

            tableLayout.addView(row)
        }
    }

    private fun createTextView(tesxt: String): TextView {
        return TextView(context).apply {
            text = tesxt
            setPadding(8, 8, 8, 8)
        }
    }

    private fun createImageView(url: String): ImageView {
        return ImageView(context).apply {
            Glide.with(this).load(url).placeholder(android.R.drawable.ic_menu_gallery).into(this)
            layoutParams = TableRow.LayoutParams(200, 200)
            setPadding(8, 8, 8, 8)
        }
    }

    private fun createActionButtons(request: PremiumRequest): TableRow {
        val row = TableRow(context)
        val acceptButton = Button(context).apply {
            text = "Accept"
            setOnClickListener {
                viewModel.acceptPremiumRequest(request.id, adminEmail)
                Toast.makeText(context, "Accepted request for ${request.userEmail}", Toast.LENGTH_SHORT).show()
            }
        }
        val rejectButton = Button(context).apply {
            text = "Reject"
            setOnClickListener {
                viewModel.rejectPremiumRequest(request.id, adminEmail)
                Toast.makeText(context, "Rejected request for ${request.userEmail}", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(acceptButton)
        row.addView(rejectButton)
        return row
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}