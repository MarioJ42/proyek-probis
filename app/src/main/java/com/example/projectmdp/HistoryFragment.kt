package com.example.projectmdp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.projectmdp.databinding.FragmentHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = arguments?.getString("userEmail") ?: ""
        viewModel.fetchUser(userEmail)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        loadTransactionHistory(userEmail)
    }

    private fun loadTransactionHistory(userEmail: String) {
        db.collection("transactions")
            .whereEqualTo("userEmail", userEmail)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.transactionTable.removeViews(1, binding.transactionTable.childCount - 1) // Clear existing rows except header
                for (document in querySnapshot) {
                    val transaksi = document.toObject(Transaksi::class.java)
                    val date = transaksi.timestamp?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it.toDate()) } ?: "N/A"

                    val row = TableRow(context)
                    row.addView(createTextView(transaksi.type))
                    row.addView(createTextView(transaksi.recipient ?: "N/A"))
                    row.addView(createTextView(String.format("Rp%,.2f", transaksi.amount)))
                    row.addView(createTextView(date))
                    row.addView(createTextView(transaksi.status))
                    row.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                    row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                    row.setPadding(0, 0, 0, 1)

                    binding.transactionTable.addView(row)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createTextView(text: String): TextView {
        return TextView(context).apply {
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            this.text = text
            setTextColor(android.graphics.Color.BLACK)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}