package com.proj.locktalk

import android.app.AlertDialog
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
import com.proj.locktalk.ContentFilter
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
        val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")
    }

    inner class SentViewHolder(val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(ItemMessageSentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentViewHolder) bindSent(holder, message)
        else if (holder is ReceivedViewHolder) bindReceived(holder, message)
    }

    private fun getChatId(msg: Message): String {
        return if (msg.senderId < msg.receiverId) "${msg.senderId}_${msg.receiverId}"
        else "${msg.receiverId}_${msg.senderId}"
    }

    private fun bindSent(holder: SentViewHolder, message: Message) {
        val b = holder.binding

        if (message.deleted) {
            b.tvMessage.text = "You deleted this message"
            b.tvMessage.alpha = 0.5f
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
            b.tvReactions.visibility = View.GONE
            b.tvTime.text = formatTime(message.timestamp)
            b.tvReadReceipt.visibility = View.GONE
            return
        }

        b.tvMessage.alpha = 1f
        b.tvReadReceipt.visibility = View.VISIBLE

        if (message.type == "text") {
            val decrypted = EncryptionHelper.decrypt(message.message)
            val safeText = ContentFilter.filterText(decrypted)
            b.tvMessage.text = safeText
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
        } else {
            b.tvMessage.visibility = View.GONE
            b.imageContainer.visibility = View.VISIBLE
            Picasso.get().load(message.imageUrl).into(b.ivImage)
            b.tvMediaLabel.text = when (message.mediaPermission) {
                "view_once" -> "View once"
                "no_save" -> "Cannot save"
                else -> "Can save"
            }
            b.btnSaveImage.visibility = View.GONE
            b.ivImage.setOnClickListener {
                showFullScreenImage(b.root.context, message.imageUrl)
            }
        }

        b.tvReadReceipt.visibility = View.VISIBLE
        b.tvReadReceipt.text = when {
            message.read -> "Seen"
            message.delivered -> "Delivered"
            else -> "Sent"
        }
        b.tvReadReceipt.setTextColor(
            if (message.read) android.graphics.Color.parseColor("#00E5FF")
            else android.graphics.Color.parseColor("#5A7A9A")
        )

        val reactionText = message.reactions.values.joinToString(" ")
        if (reactionText.isNotEmpty()) {
            b.tvReactions.text = reactionText
            b.tvReactions.visibility = View.VISIBLE
        } else {
            b.tvReactions.visibility = View.GONE
        }

        b.tvTime.text = formatTime(message.timestamp)

        b.root.setOnLongClickListener {
            showMessageOptions(b.root.context, message, isSender = true)
            true
        }
    }

    private fun bindReceived(holder: ReceivedViewHolder, message: Message) {
        val b = holder.binding

        if (message.deleted) {
            b.tvMessage.text = "This message was deleted"
            b.tvMessage.alpha = 0.5f
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
            b.tvReactions.visibility = View.GONE
            b.tvTime.text = formatTime(message.timestamp)
            return
        }

        b.tvMessage.alpha = 1f

        if (message.type == "text") {
            val decrypted = EncryptionHelper.decrypt(message.message)
            val safeText = ContentFilter.filterText(decrypted)
            b.tvMessage.text = safeText
            b.tvMessage.visibility = View.VISIBLE
            b.imageContainer.visibility = View.GONE
        } else {
            b.tvMessage.visibility = View.GONE
            b.imageContainer.visibility = View.VISIBLE

            when (message.mediaPermission) {
                "view_once" -> {
                    if (message.viewed) {
                        b.ivImage.setImageResource(R.drawable.default_avatar)
                        b.tvMediaLabel.text = "👁 Already opened"
                        b.btnSaveImage.visibility = View.GONE
                    } else {
                        Picasso.get().load(message.imageUrl).into(b.ivImage)
                        b.tvMediaLabel.text = "👁 Tap to view once"
                        b.btnSaveImage.visibility = View.GONE
                        b.ivImage.setOnClickListener { markAsViewed(message) }
                    }
                }
                "allow_save" -> {
                    Picasso.get().load(message.imageUrl).into(b.ivImage)
                    b.tvMediaLabel.text = "You can save this"
                    b.btnSaveImage.visibility = View.VISIBLE
                    b.btnSaveImage.setOnClickListener {
                        saveImageToGallery(it.context, message.imageUrl)
                    }
                    b.ivImage.setOnClickListener {
                        showFullScreenImage(b.root.context, message.imageUrl)
                    }
                }
                "no_save" -> {
                    Picasso.get().load(message.imageUrl).into(b.ivImage)
                    b.tvMediaLabel.text = "Saving not allowed"
                    b.btnSaveImage.visibility = View.GONE
                    b.ivImage.setOnLongClickListener { true }
                    b.ivImage.setOnClickListener {
                        showFullScreenImage(b.root.context, message.imageUrl)
                    }
                }
            }
        }

        val reactionText = message.reactions.values.joinToString(" ")
        if (reactionText.isNotEmpty()) {
            b.tvReactions.text = reactionText
            b.tvReactions.visibility = View.VISIBLE
        } else {
            b.tvReactions.visibility = View.GONE
        }

        b.tvTime.text = formatTime(message.timestamp)

        b.root.setOnLongClickListener {
            showMessageOptions(b.root.context, message, isSender = false)
            true
        }
    }

    private fun showMessageOptions(context: Context, message: Message, isSender: Boolean) {
        val options = if (isSender) {
            arrayOf("React", "Delete for everyone")
        } else {
            arrayOf("React")
        }

        AlertDialog.Builder(context)
            .setItems(options) { _, which ->
                when {
                    options[which] == "React" -> showReactionPicker(context, message)
                    options[which] == "Delete for everyone" -> deleteMessage(message)
                }
            }
            .show()
    }

    private fun showReactionPicker(context: Context, message: Message) {
        AlertDialog.Builder(context)
            .setTitle("React")
            .setItems(REACTION_EMOJIS.toTypedArray()) { _, which ->
                addReaction(message, REACTION_EMOJIS[which])
            }
            .show()
    }

    private fun addReaction(message: Message, emoji: String) {
        val chatId = getChatId(message)
        val newReactions = message.reactions.toMutableMap()
        newReactions[currentUserId] = emoji
        db.collection("chats").document(chatId)
            .collection("messages").document(message.messageId)
            .update("reactions", newReactions)
    }

    private fun deleteMessage(message: Message) {
        val chatId = getChatId(message)
        db.collection("chats").document(chatId)
            .collection("messages").document(message.messageId)
            .update("deleted", true)
    }

    private fun markAsViewed(message: Message) {
        val chatId = getChatId(message)
        db.collection("chats").document(chatId)
            .collection("messages").document(message.messageId)
            .update("viewed", true)
    }

    private fun showFullScreenImage(context: Context, imageUrl: String) {
        val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = android.widget.ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setOnClickListener { dialog.dismiss() }
        }
        Picasso.get().load(imageUrl).into(imageView)
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun saveImageToGallery(context: Context, imageUrl: String) {
        Toast.makeText(context, "Saving image...", Toast.LENGTH_SHORT).show()
        Picasso.get().load(imageUrl).into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,
                        "LockTalk_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
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