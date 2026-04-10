package com.proj.locktalk

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.proj.locktalk.databinding.ActivityChatBinding
import com.squareup.picasso.Picasso
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var messageList: MutableList<Message>
    private lateinit var adapter: MessageAdapter
    private var receiverId = ""
    private var receiverName = ""
    private val IMAGE_PICK_CODE = 1001
    private var pendingImageUri: Uri? = null

    private val typingHandler = Handler(Looper.getMainLooper())
    private val stopTypingRunnable = Runnable { setTyping(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        messageList = mutableListOf()

        receiverId = intent.getStringExtra("userId") ?: ""
        receiverName = intent.getStringExtra("userName") ?: ""
        val receiverPhoto = intent.getStringExtra("userPhoto") ?: ""

        binding.tvReceiverName.text = receiverName
        if (receiverPhoto.isNotEmpty()) {
            Picasso.get().load(receiverPhoto).into(binding.ivReceiverPhoto)
        }

        adapter = MessageAdapter(messageList, auth.currentUser?.uid ?: "", db)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "", "text", "allow_save")
                binding.etMessage.setText("")
            }
        }

        binding.btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.isNotEmpty() == true) {
                    setTyping(true)
                    typingHandler.removeCallbacks(stopTypingRunnable)
                    typingHandler.postDelayed(stopTypingRunnable, 2000)
                } else {
                    typingHandler.removeCallbacks(stopTypingRunnable)
                    setTyping(false)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listenToReceiverStatus()
        loadMessages()
    }

    private fun showMediaPermissionDialog(uri: Uri) {
        val options = arrayOf(
            "View Once  —  disappears after opened",
            "Allow Save  —  receiver can save",
            "No Save  —  visible in chat only"
        )
        AlertDialog.Builder(this)
            .setTitle("Who controls this image?")
            .setItems(options) { _, which ->
                val permission = when (which) {
                    0 -> "view_once"
                    1 -> "allow_save"
                    else -> "no_save"
                }
                uploadImage(uri, permission)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setTyping(typing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isTyping" to typing),
            SetOptions.merge()
        )
    }

    private fun listenToReceiverStatus() {
        db.collection("users").document(receiverId)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val isTyping = doc.getBoolean("isTyping") ?: false
                    val isOnline = doc.getBoolean("isOnline") ?: false
                    val lastSeen = doc.getLong("lastSeen") ?: 0L
                    binding.tvOnlineStatus.text = when {
                        isTyping -> "typing..."
                        isOnline -> "online"
                        else -> "last seen ${getTimeAgo(lastSeen)}"
                    }
                }
            }
    }

    private fun getTimeAgo(time: Long): String {
        if (time == 0L) return "a while ago"
        val diff = System.currentTimeMillis() - time
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hrs ago"
            else -> "$days days ago"
        }
    }

    private fun sendMessage(text: String, imageUrl: String, type: String, mediaPermission: String) {
        val senderId = auth.currentUser?.uid ?: return
        val chatId = if (senderId < receiverId) "${senderId}_${receiverId}"
        else "${receiverId}_${senderId}"
        val messageId = UUID.randomUUID().toString()

        val message = Message(
            messageId = messageId,
            senderId = senderId,
            receiverId = receiverId,
            message = text,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis(),
            type = type,
            mediaPermission = mediaPermission,
            viewed = false
        )

        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .set(message)
    }

    private fun loadMessages() {
        val senderId = auth.currentUser?.uid ?: return
        val chatId = if (senderId < receiverId) "${senderId}_${receiverId}"
        else "${receiverId}_${senderId}"

        db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                messageList.clear()
                snapshot?.documents?.forEach { doc ->
                    val msg = doc.toObject(Message::class.java)
                    if (msg != null) messageList.add(msg)
                }
                adapter.notifyDataSetChanged()
                if (messageList.isNotEmpty())
                    binding.recyclerView.scrollToPosition(messageList.size - 1)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri = data?.data ?: return
            showMediaPermissionDialog(imageUri)
        }
    }

    private fun uploadImage(uri: Uri, permission: String) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
        val ref = storage.reference.child("chat_images/${UUID.randomUUID()}")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                sendMessage("", url.toString(), "image", permission)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isOnline" to true, "isTyping" to false, "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    override fun onPause() {
        super.onPause()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isOnline" to false, "isTyping" to false, "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        typingHandler.removeCallbacks(stopTypingRunnable)
        setTyping(false)
    }
}