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
import com.example.projectmdp.data.TransactionDisplayItem
import com.example.projectmdp.databinding.FragmentHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserViewModel by viewModels { UserViewModelFactory() }
    private val db = FirebaseFirestore.getInstance()

    private lateinit var transactionAdapter: TransactionAdapter

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
        Log.d("HistoryFragment", "onViewCreated: userEmail = $userEmail")
        if (userEmail.isEmpty()) {
            Log.w("HistoryFragment", "userEmail is empty, navigating back to login")
            Toast.makeText(context, "User email not found, please login.", Toast.LENGTH_SHORT).show()
            return
        }

        setupRecyclerView()

        viewModel.fetchUser(userEmail)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        loadTransactionHistory(userEmail)
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        binding.transactionRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun loadTransactionHistory(userEmail: String) {
        val normalizedEmail = userEmail.toLowerCase(Locale.ROOT)
        Log.d("HistoryFragment", "loadTransactionHistory: Querying for userEmail = $normalizedEmail")

        db.collection("transactions")
            .whereEqualTo("userEmail", normalizedEmail)

            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("HistoryFragment", "Query successful: Found ${querySnapshot.size()} transactions")

                val transactionDisplayItems = mutableListOf<TransactionDisplayItem>()
                for (document in querySnapshot) {
                    val transaksi = document.toObject(Transaksi::class.java)
                    val transactionDisplayItem = TransactionDisplayItem(document.id, transaksi)
                    transactionDisplayItems.add(transactionDisplayItem)
                    Log.d("HistoryFragment", "Fetched transaction item: ${document.id}, ${transaksi.type}")
                }

                val sortedTransactionItems = transactionDisplayItems.sortedByDescending { it.data.timestamp?.toDate() }

                transactionAdapter.submitList(sortedTransactionItems)
                Log.d("HistoryFragment", "Submitted ${sortedTransactionItems.size} transaction items to adapter.")

                if (sortedTransactionItems.isEmpty()) {
                    Log.w("HistoryFragment", "No transactions found for userEmail = $normalizedEmail")
                    Toast.makeText(context, "No transactions found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryFragment", "Failed to load transactions: ${e.message}", e)
                Toast.makeText(context, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}