package com.example.projectmdp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

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
        holder.bind(user, onStatusChange)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val emailText: TextView = itemView.findViewById(R.id.emailText)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val actionButton: Button = itemView.findViewById(R.id.actionButton)

        fun bind(user: User, onStatusChange: (User, String) -> Unit) {
            nameText.text = user.fullName
            emailText.text = user.email
            balanceText.text = "Rp ${String.format("%,.2f", user.balance)}"
            statusText.text = user.status

            actionButton.text = if (user.status == "active") "Deactivate" else "Activate"
            actionButton.setOnClickListener {
                val newStatus = if (user.status == "active") "inactive" else "active"
                onStatusChange(user, newStatus)
            }
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