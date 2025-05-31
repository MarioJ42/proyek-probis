package com.example.projectmdp // Sesuaikan dengan package Anda

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

import java.util.Locale



class PremiumRequestAdapter(
    private val onAcceptClick: (PremiumRequest) -> Unit,
    private val onRejectClick: (PremiumRequest) -> Unit,
    private val onDetailClick: (PremiumRequest) -> Unit,
) : ListAdapter<PremiumRequest, PremiumRequestAdapter.PremiumRequestViewHolder>(PremiumRequestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PremiumRequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_premium_request_table, parent, false)
        return PremiumRequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: PremiumRequestViewHolder, position: Int) {
        val request = getItem(position)
        holder.bind(request, onAcceptClick, onRejectClick,onDetailClick, position)
    }

    class PremiumRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowContainer: ConstraintLayout = itemView.findViewById(R.id.rowContainer)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val emailText: TextView = itemView.findViewById(R.id.emailText)
        private val ktpPhotoImageView: ImageView = itemView.findViewById(R.id.ktpPhotoImageView)
        private val premiumStatusText: TextView = itemView.findViewById(R.id.premiumStatusText)
        private val detailButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.detailButton)
//        private val rejectButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.Decbtn)
//        private val acceptButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.AccBtn)

        fun bind(request: PremiumRequest, onAcceptClick: (PremiumRequest) -> Unit, onRejectClick: (PremiumRequest) -> Unit, onDetailClick: (PremiumRequest) -> Unit, position: Int) {
            val context = itemView.context

            val backgroundColor = if (position % 2 == 0) {
                ContextCompat.getColor(context, R.color.table_row_even)
            } else {
                ContextCompat.getColor(context, R.color.table_row_odd)
            }
            rowContainer.setBackgroundColor(backgroundColor)

            nameText.text = request.userEmail.split("@")[0].capitalize(Locale.ROOT)
            emailText.text = request.userEmail

            if (request.ktpPhoto.isNotEmpty()) {
                Glide.with(context)
                    .load(request.ktpPhoto)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_error_image)
                    .into(ktpPhotoImageView)
            } else {
                ktpPhotoImageView.setImageResource(R.drawable.ic_no_image_available)
            }

            val statusText = if (request.premium) "Approved" else if (request.requestPremium) "Pending" else "Rejected"
            premiumStatusText.text = statusText
            val statusColor = when {
                request.premium -> ContextCompat.getColor(context, R.color.status_active)
                request.requestPremium -> ContextCompat.getColor(context, R.color.status_pending)
                else -> ContextCompat.getColor(context, R.color.status_inactive)
            }
            premiumStatusText.setTextColor(statusColor)

            if (request.requestPremium) {
                detailButton.visibility = View.VISIBLE
//                rejectButton.visibility = View.VISIBLE
//                acceptButton.visibility = View.VISIBLE
//                acceptButton.setOnClickListener { onAcceptClick(request) }
//                rejectButton.setOnClickListener { onRejectClick(request) }
                detailButton.setOnClickListener { onDetailClick(request)}
            } else {
                detailButton.visibility = View.GONE
//                rejectButton.visibility = View.GONE
//                acceptButton.visibility = View.GONE
            }
        }
    }

    class PremiumRequestDiffCallback : DiffUtil.ItemCallback<PremiumRequest>() {
        override fun areItemsTheSame(oldItem: PremiumRequest, newItem: PremiumRequest): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PremiumRequest, newItem: PremiumRequest): Boolean {
            return oldItem == newItem
        }
    }
}