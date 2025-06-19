package com.example.projectmdp

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale



class UserTableAdapter(
    private val onStatusChange: (User, String) -> Unit
) : ListAdapter<User, UserTableAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_table, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user, onStatusChange, position)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowContainer: ConstraintLayout = itemView.findViewById(R.id.rowContainer)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val emailText: TextView = itemView.findViewById(R.id.emailText)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val actionButton: Button = itemView.findViewById(R.id.actionButton)

        fun bind(user: User, onStatusChange: (User, String) -> Unit, position: Int) {
            val context = itemView.context

            val backgroundColor = if (position % 2 == 0) {
                ContextCompat.getColor(context, R.color.table_row_even)
            } else {
                ContextCompat.getColor(context, R.color.table_row_odd)
            }
            rowContainer.setBackgroundColor(backgroundColor)

            nameText.text = user.fullName
            emailText.text = user.email
            balanceText.text = formatCurrency(user.balance)


            statusText.text = user.status
            val statusColor = when (user.status.toLowerCase(Locale.ROOT)) {
                "active" -> ContextCompat.getColor(context, R.color.status_active)
                "inactive" -> ContextCompat.getColor(context, R.color.status_inactive)
                else -> ContextCompat.getColor(context, R.color.text_primary)
            }
            statusText.setTextColor(statusColor)

            actionButton.text = if (user.status.toLowerCase(Locale.ROOT) == "active") "Deactivate" else "Activate"
            val buttonBackgroundTint = if (user.status.toLowerCase(Locale.ROOT) == "active") {
                ContextCompat.getColorStateList(context, R.color.action_button_deactivate)
            } else {
                ContextCompat.getColorStateList(context, R.color.action_button_active)
            }
            actionButton.backgroundTintList = buttonBackgroundTint


            actionButton.setOnClickListener {
                val newStatus = if (user.status.toLowerCase(Locale.ROOT) == "active") "inactive" else "active"

                val dialogTitle = if (newStatus == "active") "activate User" else "deactivate User"
                val dialogMessage = "Are you sure you want to ${dialogTitle} this user?"

                AlertDialog.Builder(context)
                    .setTitle(dialogTitle)
                    .setMessage(dialogMessage)
                    .setPositiveButton("Yes") { _, _ ->
                        onStatusChange(user, newStatus)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

        private fun formatCurrency(amount: Double): String { // Ubah tipe parameter menjadi Double
            val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            return formatter.format(amount).replace("IDR", "Rp").trim()
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}