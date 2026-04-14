package com.proj.locktalk
import com.google.firebase.messaging.FirebaseMessaging
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.proj.locktalk.databinding.ActivityMainBinding
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UserAdapter

    private val conversationList = mutableListOf<ConversationItem>()
    private val userMap = mutableMapOf<String, User>()
    private val convMap = mutableMapOf<String, ConversationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        adapter = UserAdapter(conversationList) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.uid)
            intent.putExtra("userName", user.name)
            intent.putExtra("userPhoto", user.profileImage)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        setOnline(true)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
        }
        loadCurrentUserPhoto()
        loadUsers()
        loadConversations()
    }

    private fun loadCurrentUserPhoto() {
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null && user.profileImage.isNotEmpty()) {
                    Picasso.get().load(user.profileImage).into(binding.btnProfile)
                }
            }
    }

    private fun setOnline(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("isOnline" to online, "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    private fun loadUsers() {
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("users").addSnapshotListener { result, _ ->
            result?.documentChanges?.forEach { change ->
                val user = change.document.toObject(User::class.java)
                if (user.uid != currentUid) {
                    userMap[user.uid] = user
                }
            }
            rebuildList()
        }
    }

    private fun loadConversations() {
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("userChats").document(currentUid)
            .collection("conversations")
            .addSnapshotListener { result, _ ->
                convMap.clear()
                result?.documents?.forEach { doc ->
                    convMap[doc.id] = ConversationItem(
                        user = User(),
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTime = doc.getLong("lastMessageTime") ?: 0L,
                        unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0
                    )
                }
                rebuildList()
            }
    }

    private fun rebuildList() {
        conversationList.clear()
        userMap.values.forEach { user ->
            val conv = convMap[user.uid]
            conversationList.add(
                ConversationItem(
                    user = user,
                    lastMessage = when {
                        user.isTyping -> "typing..."
                        else -> conv?.lastMessage ?: user.status
                    },
                    lastMessageTime = conv?.lastMessageTime ?: 0L,
                    unreadCount = conv?.unreadCount ?: 0
                )
            )
        }
        conversationList.sortByDescending { it.lastMessageTime }
        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        setOnline(true)
        loadCurrentUserPhoto()
    }

    override fun onPause() {
        super.onPause()
        setOnline(false)
    }
}