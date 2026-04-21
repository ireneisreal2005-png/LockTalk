package com.proj.locktalk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.proj.locktalk.databinding.ItemUserBinding
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class UserAdapter(
    private val items: MutableList<ConversationItem>,
    private val pinnedChats: Set<String>,
    private val mutedChats: Set<String>,
    private val bannedUsers: Set<String>,
    private val blockedUsers: Set<String>,
    private val onClick: (User) -> Unit,
    private val onLongClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val item = items[position]
        val user = item.user
        if (pinnedChats.contains(user.uid)) {
            holder.binding.pinIcon.visibility = View.VISIBLE
        } else {
            holder.binding.pinIcon.visibility = View.GONE
        }
        holder.binding.tvName.text = user.name
        if (mutedChats.contains(user.uid)) {
            holder.binding.muteIcon.visibility = View.VISIBLE
        } else {
            holder.binding.muteIcon.visibility = View.GONE
        }
        if (blockedUsers.contains(user.uid)) {
            holder.binding.blockIcon.visibility = View.VISIBLE
        } else {
            holder.binding.blockIcon.visibility = View.GONE
        }
        when {
            user.isTyping -> {
                holder.binding.tvLastMessage.text = "typing..."
                holder.binding.tvLastMessage.setTextColor(
                    android.graphics.Color.parseColor("#00E5FF")
                )
            }
            item.lastMessage.isNotEmpty() -> {
                holder.binding.tvLastMessage.text = item.lastMessage
                holder.binding.tvLastMessage.setTextColor(
                    android.graphics.Color.parseColor("#5A7A9A")
                )
            }
            else -> {
                holder.binding.tvLastMessage.text = user.status
                holder.binding.tvLastMessage.setTextColor(
                    android.graphics.Color.parseColor("#5A7A9A")
                )
            }
        }

        if (item.lastMessageTime > 0L) {
            holder.binding.tvTime.text = formatTime(item.lastMessageTime)
        } else {
            holder.binding.tvTime.text = ""
        }

        if (item.unreadCount > 0) {
            holder.binding.tvUnreadCount.visibility = View.VISIBLE
            holder.binding.tvUnreadCount.text = if (item.unreadCount > 99) "99+"
            else item.unreadCount.toString()
        } else {
            holder.binding.tvUnreadCount.visibility = View.GONE
        }

        holder.binding.onlineDot.visibility =
            if (user.isOnline) View.VISIBLE else View.INVISIBLE

        if (user.profileImage.isNotEmpty()) {
            Picasso.get()
                .load(user.profileImage)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(holder.binding.ivProfile)
        } else {
            holder.binding.ivProfile.setImageResource(R.drawable.default_avatar)
        }

        holder.itemView.setOnClickListener {
            onClick(user)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(user)
            true
        }
        if (bannedUsers.contains(user.uid)) {
            holder.binding.tvLastMessage.text = "User is banned "
        }
    }
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val cal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m"
            cal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) ->
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
            diff < 7 * 24 * 3600_000L ->
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
            else ->
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    override fun getItemCount() = items.size
}