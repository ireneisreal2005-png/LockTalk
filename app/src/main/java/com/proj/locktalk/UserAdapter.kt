package com.proj.locktalk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.proj.locktalk.databinding.ItemUserBinding
import com.squareup.picasso.Picasso

class UserAdapter(
    private val users: MutableList<User>,
    private val onClick: (User) -> Unit
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
        val user = users[position]
        holder.binding.tvName.text = user.name
        holder.binding.tvStatus.text = if (user.isOnline) "online" else user.status
        holder.binding.onlineDot.visibility = if (user.isOnline) View.VISIBLE else View.INVISIBLE
        if (user.profileImage.isNotEmpty()) {
            Picasso.get().load(user.profileImage).into(holder.binding.ivProfile)
        }
        holder.itemView.setOnClickListener { onClick(user) }
    }

    override fun getItemCount() = users.size
}