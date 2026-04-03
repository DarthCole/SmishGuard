package com.smishguard.app.ui.conversations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smishguard.app.R
import com.smishguard.app.databinding.ItemConversationBinding
import com.smishguard.app.domain.model.SmsConversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * ConversationAdapter.kt — RecyclerView Adapter with RED DOT indicator
 * ======================================================================
 * The Adapter is the BRIDGE between your data and the RecyclerView.
 * It tells the RecyclerView:
 *   1. HOW MANY items there are
 *   2. HOW to create a View for each item (onCreateViewHolder)
 *   3. HOW to fill that View with data (onBindViewHolder)
 *
 * "ListAdapter" is a special RecyclerView.Adapter that uses DiffUtil
 * to efficiently compute differences between old and new lists.
 * When you call submitList(newList), it automatically:
 *   - Detects which items were added, removed, or changed
 *   - Animates only the changes (instead of refreshing everything)
 *
 * DIFFUTIL explained:
 *   DiffUtil.ItemCallback has two methods:
 *   - areItemsTheSame(): "Is this the SAME item?" (compare IDs)
 *   - areContentsTheSame(): "Does this item LOOK the same?" (compare all fields)
 *
 * VIEWHOLDER explained:
 *   A ViewHolder holds references to the Views inside each list item.
 *   Instead of calling findViewById every time an item scrolls into view,
 *   the ViewHolder caches those references for efficiency.
 *
 * "(private val onItemClick: (SmsConversation) -> Unit)" is a constructor
 * parameter that accepts a LAMBDA FUNCTION:
 *   - "(SmsConversation)" = the lambda takes one parameter of this type
 *   - "-> Unit" = the lambda returns nothing (Unit ≈ void in Java)
 */
class ConversationAdapter(
    private val onItemClick: (SmsConversation) -> Unit
) : ListAdapter<SmsConversation, ConversationAdapter.ConversationViewHolder>(DiffCallback()) {

    /**
     * Called when RecyclerView needs a NEW ViewHolder (a new row view).
     * This only happens a few times — enough to fill the screen + a buffer.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false  // Don't attach to parent yet — RecyclerView does that
        )
        return ConversationViewHolder(binding)
    }

    /**
     * Called when RecyclerView wants to display data at a specific position.
     * This is called FREQUENTLY as the user scrolls — keep it fast!
     *
     * "getItem(position)" comes from ListAdapter — returns the item at that index.
     */
    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder — holds and populates the views for ONE conversation item.
     *
     * "inner class" means this class has access to the outer class's properties
     * (specifically, the onItemClick lambda).
     */
    inner class ConversationViewHolder(
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        // "binding.root" is the outermost View of item_conversation.xml

        /**
         * Populate the views with data from one SmsConversation.
         */
        fun bind(conversation: SmsConversation) {
            // Set the sender name (contact name or phone number)
            binding.textSenderName.text = conversation.senderName
                ?: conversation.senderAddress
            // "?:" ELVIS OPERATOR: if senderName is null, use senderAddress

            // Set the last message preview (truncated)
            binding.textLastMessage.text = conversation.lastMessage

            // Set the timestamp
            binding.textTimestamp.text = formatTimestamp(conversation.lastTimestamp)

            // Set the message count badge
            binding.textMessageCount.text = "${conversation.messageCount} msgs"

            // ═══════════════════════════════════════════════════════
            // ★ THE RED DOT — Fraud Indicator ★
            // ═══════════════════════════════════════════════════════
            // Show or hide the red warning dot based on ML analysis.
            // This is right-aligned and vertically centered in the item layout.
            if (conversation.isFlaggedFraudulent) {
                binding.indicatorFraudDot.visibility = View.VISIBLE
                // Also tint the entire item with a subtle red background
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.fraud_background)
                )
                // Show confidence percentage
                val percent = (conversation.fraudConfidence * 100).toInt()
                binding.textFraudConfidence.visibility = View.VISIBLE
                binding.textFraudConfidence.text = "$percent% risk"
            } else {
                binding.indicatorFraudDot.visibility = View.GONE
                // "GONE" = invisible AND takes no space (vs INVISIBLE which takes space)
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.safe_background)
                )
                binding.textFraudConfidence.visibility = View.GONE
            }

            // Handle item click
            binding.root.setOnClickListener {
                onItemClick(conversation)
            }
        }

        /**
         * Format a Unix timestamp into a human-readable date/time.
         */
        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                // "when" is Kotlin's powerful switch/case.
                // Unlike Java's switch, it can match ANY condition, not just values.
                diff < 60 * 1000 -> "Just now"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    sdf.format(Date(timestamp))
                    // "else" is the default case — used when no other matches
                }
            }
        }
    }

    /**
     * DiffUtil callback — tells RecyclerView how to compare items.
     */
    class DiffCallback : DiffUtil.ItemCallback<SmsConversation>() {
        override fun areItemsTheSame(old: SmsConversation, new: SmsConversation): Boolean {
            return old.threadId == new.threadId
            // Same thread ID = same conversation (even if content changed)
        }

        override fun areContentsTheSame(old: SmsConversation, new: SmsConversation): Boolean {
            return old == new
            // data class "==" compares ALL properties automatically
        }
    }
}
