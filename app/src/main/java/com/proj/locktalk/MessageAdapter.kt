package com.proj.locktalk

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.proj.locktalk.databinding.ItemMessageReceivedBinding
import com.proj.locktalk.databinding.ItemMessageSentBinding
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String,
    private val db: FirebaseFirestore
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2
    }

    inner class SentViewHolder(val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT
        else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(
                ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            ReceivedViewHolder(
                ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentViewHolder) bindSent(holder, message)
        else if (holder is ReceivedViewHolder) bindReceived(holder, message)
    }

    private fun bindSent(holder: SentViewHolder, message: Message) {
        val b = holder.binding
        if (message.type == "text") {
            b.tvMessage.text = message.message
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
        } else {
            b.tvMessage.visibility = View.GONE
            b.imageContainer.visibility = View.VISIBLE
            Picasso.get().load(message.imageUrl).into(b.ivImage)

            val label = when (message.mediaPermission) {
                "view_once" -> "View once"
                "no_save" -> "Cannot save"
                else -> "Can save"
            }
            b.tvMediaLabel.text = label
            b.btnSaveImage.visibility = View.GONE
        }
        b.tvTime.text = formatTime(message.timestamp)
    }

    private fun bindReceived(holder: ReceivedViewHolder, message: Message) {
        val b = holder.binding
        if (message.type == "text") {
            b.tvMessage.text = message.message
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
        } else {
            b.tvMessage.visibility = View.GONE
            b.imageContainer.visibility = View.VISIBLE

            when (message.mediaPermission) {
                "view_once" -> {
                    if (message.viewed) {
                        b.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
                        b.tvMediaLabel.text = "Opened"
                        b.btnSaveImage.visibility = View.GONE
                    } else {
                        Picasso.get().load(message.imageUrl).into(b.ivImage)
                        b.tvMediaLabel.text = "View once — tap to open"
                        b.btnSaveImage.visibility = View.GONE
                        b.ivImage.setOnClickListener {
                            markAsViewed(message)
                        }
                    }
                }
                "allow_save" -> {
                    Picasso.get().load(message.imageUrl).into(b.ivImage)
                    b.tvMediaLabel.text = "You can save this"
                    b.btnSaveImage.visibility = View.VISIBLE
                    b.btnSaveImage.setOnClickListener {
                        saveImageToGallery(it.context, message.imageUrl)
                    }
                }
                "no_save" -> {
                    Picasso.get().load(message.imageUrl).into(b.ivImage)
                    b.tvMediaLabel.text = "Saving not allowed"
                    b.btnSaveImage.visibility = View.GONE
                    b.ivImage.setOnLongClickListener { true }
                }
            }
        }
        b.tvTime.text = formatTime(message.timestamp)
    }

    private fun markAsViewed(message: Message) {
        val senderId = message.senderId
        val receiverId = message.receiverId
        val chatId = if (senderId < receiverId) "${senderId}_${receiverId}"
        else "${receiverId}_${senderId}"

        db.collection("chats").document(chatId)
            .collection("messages").document(message.messageId)
            .update("viewed", true)
    }

    private fun saveImageToGallery(context: Context, imageUrl: String) {
        Toast.makeText(context, "Saving image...", Toast.LENGTH_SHORT).show()
        Picasso.get().load(imageUrl).into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "LockTalk_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
            }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        })
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    override fun getItemCount() = messages.size
}