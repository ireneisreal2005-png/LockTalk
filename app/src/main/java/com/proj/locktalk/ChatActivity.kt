package com.proj.locktalk

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.proj.locktalk.ContentFilter
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
    private var isFirstLoad = true

    private val typingHandler = Handler(Looper.getMainLooper())
    private val stopTypingRunnable = Runnable { setTyping(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        messageList = mutableListOf()

        receiverId = intent.getStringExtra("userId") ?: ""
        receiverName = intent.getStringExtra("userName") ?: ""
        val receiverPhoto = intent.getStringExtra("userPhoto") ?: ""

        binding.tvReceiverName.text = receiverName
        if (receiverPhoto.isNotEmpty()) {
            Picasso.get()
                .load(receiverPhoto)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(binding.ivReceiverPhoto)
        } else {
            binding.ivReceiverPhoto.setImageResource(R.drawable.default_avatar)
        }

        adapter = MessageAdapter(messageList, auth.currentUser?.uid ?: "", db)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {

            val originalText = binding.etMessage.text.toString().trim()
            if (originalText.isEmpty()) return@setOnClickListener

            if (ContentFilter.isExplicit(originalText)) {
                Toast.makeText(this, "Message blocked", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContentFilter.containsSuicidalContent(originalText)) {
                showHelpDialog()
            }

            val filteredText = ContentFilter.filterText(originalText)

            val url = extractUrl(originalText)?.let {
                if (!it.startsWith("http")) "https://$it" else it
            }

            if (url != null) {
                SafeBrowsingHelper.checkUrl(url) { isSafe ->
                    runOnUiThread {
                        if (isSafe) {
                            sendMessage(filteredText, "", "text", "allow_save")
                            binding.etMessage.setText("")
                        } else {
                            Toast.makeText(this, "Unsafe link blocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                sendMessage(filteredText, "", "text", "allow_save")
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

    private fun markMessagesAsRead() {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < receiverId) "${currentUid}_${receiverId}"
        else "${receiverId}_${currentUid}"

        db.collection("chats").document(chatId)
            .collection("messages")
            .whereEqualTo("receiverId", currentUid)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { docs ->
                val batch = db.batch()
                docs.forEach { doc ->
                    batch.update(doc.reference, "read", true)
                }
                batch.commit()
            }
    }

    private fun resetUnread() {
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("userChats").document(currentUid)
            .collection("conversations").document(receiverId)
            .set(mapOf("unreadCount" to 0), SetOptions.merge())
    }

    private fun setTyping(typing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isTyping" to typing), SetOptions.merge()
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
        db.collection("users").document(receiverId).get().addOnSuccessListener { doc ->

            val banned = doc.getBoolean("banned") ?: false

            if (banned) {
                Toast.makeText(this, "User is banned ", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

        }
        val senderId = auth.currentUser?.uid ?: return
        val chatId = if (senderId < receiverId) "${senderId}_${receiverId}"
        else "${receiverId}_${senderId}"
        val messageId = UUID.randomUUID().toString()

        val message = Message(
            messageId = messageId,
            senderId = senderId,
            receiverId = receiverId,
            message = EncryptionHelper.encrypt(text),
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis(),
            type = type,
            mediaPermission = mediaPermission,
            viewed = false,
            delivered = true,
            read = false
        )

        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .set(message)

        updateConversation(text, type)

        db.collection("users").document(senderId).get()
            .addOnSuccessListener { doc ->
                val sender = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                val notifBody = if (type == "image") "Sent you an image" else "Encrypted message"
                NotificationHelper.sendNotification(
                    db, receiverId, sender.name, senderId, sender.profileImage, notifBody
                )
            }
        db.collection("users").document(receiverId).get().addOnSuccessListener { doc ->

            val banned = doc.getBoolean("banned") ?: false

            if (banned) {
                Toast.makeText(this, "User is banned", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

        }
    }

    private fun updateConversation(text: String, type: String) {
        val senderId = auth.currentUser?.uid ?: return
        val displayMessage = if (type == "image") "Image" else text
        val time = System.currentTimeMillis()

        db.collection("userChats").document(senderId)
            .collection("conversations").document(receiverId)
            .set(mapOf(
                "lastMessage" to displayMessage,
                "lastMessageTime" to time,
                "unreadCount" to 0
            ), SetOptions.merge())

        val receiverRef = db.collection("userChats").document(receiverId)
            .collection("conversations").document(senderId)

        db.runTransaction { transaction ->
            val doc = transaction.get(receiverRef)
            val currentUnread = doc.getLong("unreadCount") ?: 0
            transaction.set(receiverRef, mapOf(
                "lastMessage" to displayMessage,
                "lastMessageTime" to time,
                "unreadCount" to currentUnread + 1
            ), SetOptions.merge())
        }
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
                markMessagesAsRead()
                if (isFirstLoad) {
                    resetUnread()
                    isFirstLoad = false
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri = data?.data ?: return
            showMediaPermissionDialog(imageUri)
        }
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

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Need Help?")
            .setMessage("It seems like you're going through something. You're not alone ❤️")
            .setPositiveButton("Call Helpline") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:9152987821") // example helpline
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    fun extractUrl(text: String): String? {
        val regex = Regex("(https?://|www\\.)\\S+")
        return regex.find(text)?.value
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isOnline" to true, "isTyping" to false,
                "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
        markMessagesAsRead()
    }

    override fun onPause() {
        super.onPause()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isOnline" to false, "isTyping" to false,
                "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        typingHandler.removeCallbacks(stopTypingRunnable)
        setTyping(false)
    }
}