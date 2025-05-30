package com.example.projectmdp
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.projectmdp.R
import com.example.projectmdp.data.TransactionDisplayItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter : ListAdapter<TransactionDisplayItem, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_table, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transactionDisplayItem = getItem(position)
        holder.bind(transactionDisplayItem, position)
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowContainer: ConstraintLayout = itemView.findViewById(R.id.rowContainer)
        private val typeText: TextView = itemView.findViewById(R.id.typeText)
        private val recipientText: TextView = itemView.findViewById(R.id.recipientText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)

        fun bind(transactionDisplayItem: TransactionDisplayItem, position: Int) {
            val context = itemView.context
            val transaction = transactionDisplayItem.data

            val backgroundColor = if (position % 2 == 0) {
                ContextCompat.getColor(context, R.color.table_row_even)
            } else {
                ContextCompat.getColor(context, R.color.table_row_odd)
            }
            rowContainer.setBackgroundColor(backgroundColor)

            typeText.text = transaction.type
            recipientText.text = transaction.recipient ?: "N/A"
            amountText.text = formatCurrency(transaction.amount)

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateText.text = transaction.timestamp?.let { dateFormat.format(it.toDate()) } ?: "N/A"

            statusText.text = transaction.status
            val statusColor = when (transaction.status.toLowerCase(Locale.ROOT)) {
                "completed" -> ContextCompat.getColor(context, R.color.status_completed)
                "pending" -> ContextCompat.getColor(context, R.color.status_pending)
                "failed" -> ContextCompat.getColor(context, R.color.status_failed)
                else -> ContextCompat.getColor(context, R.color.text_primary)
            }
            statusText.setTextColor(statusColor)
        }

        private fun formatCurrency(amount: Double): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            return formatter.format(amount).replace("IDR", "Rp").trim()
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionDisplayItem>() {
        override fun areItemsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem): Boolean {
            // Bandingkan berdasarkan firestoreId (ID unik dokumen Firestore)
            return oldItem.firestoreId == newItem.firestoreId
        }

        override fun areContentsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem): Boolean {
            // Bandingkan objek Transaksi secara keseluruhan (data class secara otomatis membandingkan properti)
            return oldItem.data == newItem.data
        }
    }
}