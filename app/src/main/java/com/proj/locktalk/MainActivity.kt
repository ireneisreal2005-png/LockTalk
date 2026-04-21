package com.proj.locktalk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.proj.locktalk.databinding.ActivityMainBinding
import com.squareup.picasso.Picasso
import android.hardware.display.DisplayManager
import android.view.Display

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UserAdapter
    private lateinit var prefs: android.content.SharedPreferences

    private val conversationList = mutableListOf<ConversationItem>()
    private val userMap = mutableMapOf<String, User>()
    private val convMap = mutableMapOf<String, ConversationItem>()

    private val lockedChats = mutableSetOf<String>()
    private val pinnedChats = mutableSetOf<String>()
    private val mutedChats = mutableSetOf<String>()
    private val bannedUsers = mutableSetOf<String>()
    private val blockedUsers = mutableSetOf<String>()
    private fun showOptionsDialog(user: User, isBlocked: Boolean) {

        val options = if (isBlocked) {
            arrayOf(
                if (mutedChats.contains(user.uid)) "Unmute" else "Mute",
                "Unblock",
                "Report",
                if (lockedChats.contains(user.uid)) "Unlock Chat" else "Lock Chat",
                if (pinnedChats.contains(user.uid)) "Unpin" else "Pin"
            )
        } else {
            arrayOf(
                if (mutedChats.contains(user.uid)) "Unmute" else "Mute",
                "Block",
                "Report",
                if (lockedChats.contains(user.uid)) "Unlock Chat" else "Lock Chat",
                if (pinnedChats.contains(user.uid)) "Unpin" else "Pin"
            )
        }

        AlertDialog.Builder(this)
            .setTitle(user.name)
            .setItems(options) { _, which ->

                when (options[which]) {

                    "Lock Chat" -> {
                        lockedChats.add(user.uid)
                        prefs.edit().putBoolean(user.uid, true).apply()
                        Toast.makeText(this, "Chat Locked", Toast.LENGTH_SHORT).show()
                    }

                    "Unlock Chat" -> authenticateAndOpen(user)

                    "Mute" -> {
                        mutedChats.add(user.uid)
                        prefs.edit().putBoolean("mute_${user.uid}", true).apply()
                        Toast.makeText(this, "Chat Muted", Toast.LENGTH_SHORT).show()
                        rebuildList()
                    }

                    "Unmute" -> {
                        mutedChats.remove(user.uid)
                        prefs.edit().remove("mute_${user.uid}").apply()
                        Toast.makeText(this, "Chat Unmuted", Toast.LENGTH_SHORT).show()
                        rebuildList()
                    }

                    "Block" -> {
                        db.collection("blockedUsers")
                            .document(auth.currentUser!!.uid)
                            .collection("list")
                            .document(user.uid)
                            .set(mapOf("blocked" to true))

                        Toast.makeText(this, "User Blocked", Toast.LENGTH_SHORT).show()
                    }

                    "Unblock" -> {
                        db.collection("blockedUsers")
                            .document(auth.currentUser!!.uid)
                            .collection("list")
                            .document(user.uid)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "User Unblocked", Toast.LENGTH_SHORT).show()
                                loadUsers()
                                loadConversations()
                            }
                    }

                    "Pin" -> {
                        pinnedChats.add(user.uid)
                        prefs.edit().putBoolean("pin_${user.uid}", true).apply()
                        Toast.makeText(this, "Chat Pinned", Toast.LENGTH_SHORT).show()
                        rebuildList()
                    }

                    "Unpin" -> {
                        pinnedChats.remove(user.uid)
                        prefs.edit().remove("pin_${user.uid}").apply()
                        Toast.makeText(this, "Chat Unpinned", Toast.LENGTH_SHORT).show()
                        rebuildList()
                    }

                    "Report" -> {
                        val reportRef = db.collection("reports").document(user.uid)

                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(reportRef)
                            val current = snapshot.getLong("count") ?: 0L
                            val newCount = current + 1

                            transaction.set(reportRef, mapOf("count" to newCount), SetOptions.merge())
                            newCount
                        }.addOnSuccessListener { count ->

                            Toast.makeText(this, "User Reported", Toast.LENGTH_SHORT).show()

                            if (count == 3L) banUser(user.uid)
                            if (count >= 5L) deleteUserAccount(user.uid)
                        }
                    }
                }
            }
            .show()
    }

    private fun authenticateAndOpen(user: User) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)

        val prompt = androidx.biometric.BiometricPrompt(
            this,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    lockedChats.remove(user.uid)
                    prefs.edit().remove(user.uid).apply()

                    val intent = Intent(this@MainActivity, ChatActivity::class.java)
                    intent.putExtra("userId", user.uid)
                    intent.putExtra("userName", user.name)
                    intent.putExtra("userPhoto", user.profileImage)
                    startActivity(intent)
                }
            })

        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Chat")
            .setSubtitle("Authenticate")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("lock_prefs", MODE_PRIVATE)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadLockedChats()
        loadPinnedChats()
        loadMutedChats()
        loadBannedUsers()

        adapter = UserAdapter(
            conversationList,
            pinnedChats,
            mutedChats,
            bannedUsers,
            blockedUsers,

            { user ->
                val currentUid = auth.currentUser?.uid ?: return@UserAdapter

                db.collection("blockedUsers")
                    .document(currentUid)
                    .collection("list")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { doc ->

                        if (bannedUsers.contains(user.uid)) {
                            Toast.makeText(this, "User is banned", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        if (doc.exists()) {
                            Toast.makeText(this, "User is blocked", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        if (lockedChats.contains(user.uid)) {
                            authenticateAndOpen(user)
                            return@addOnSuccessListener
                        }

                        val intent = Intent(this, ChatActivity::class.java)
                        intent.putExtra("userId", user.uid)
                        intent.putExtra("userName", user.name)
                        intent.putExtra("userPhoto", user.profileImage)
                        startActivity(intent)
                    }
            },

            { user ->
                val currentUid = auth.currentUser?.uid ?: return@UserAdapter

                db.collection("blockedUsers")
                    .document(currentUid)
                    .collection("list")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        showOptionsDialog(user, doc.exists())
                    }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadUsers()
        loadConversations()
        loadBlockedUsers()
    }
    private fun rebuildList() {
        conversationList.clear()

        userMap.values.forEach { user ->
            val conv = convMap[user.uid]

            conversationList.add(
                ConversationItem(
                    user = user,
                    lastMessage = conv?.lastMessage ?: user.status,
                    lastMessageTime = conv?.lastMessageTime ?: 0L,
                    unreadCount = conv?.unreadCount ?: 0
                )
            )
        }

        conversationList.sortWith(
            compareByDescending<ConversationItem> { pinnedChats.contains(it.user.uid) }
                .thenByDescending { it.lastMessageTime }
        )

        adapter.notifyDataSetChanged()
    }

    private fun loadUsers() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").addSnapshotListener { result, _ ->
            result?.documents?.forEach {
                val user = it.toObject(User::class.java)
                if (user != null && user.uid != uid) {
                    userMap[user.uid] = user
                }
            }
            rebuildList()
        }
    }

    private fun loadConversations() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("userChats")
            .document(uid)
            .collection("conversations")
            .addSnapshotListener { result, _ ->
                convMap.clear()

                result?.documents?.forEach {
                    convMap[it.id] = ConversationItem(
                        user = User(),
                        lastMessage = it.getString("lastMessage") ?: "",
                        lastMessageTime = it.getLong("lastMessageTime") ?: 0L,
                        unreadCount = it.getLong("unreadCount")?.toInt() ?: 0
                    )
                }

                rebuildList()
            }
    }

    private fun loadLockedChats() {
        prefs.all.forEach {
            if (it.value == true && !it.key.startsWith("pin_") && !it.key.startsWith("mute_")) {
                lockedChats.add(it.key)
            }
        }
    }

    private fun loadPinnedChats() {
        prefs.all.forEach {
            if (it.key.startsWith("pin_") && it.value == true) {
                pinnedChats.add(it.key.removePrefix("pin_"))
            }
        }
    }

    private fun loadMutedChats() {
        prefs.all.forEach {
            if (it.key.startsWith("mute_") && it.value == true) {
                mutedChats.add(it.key.removePrefix("mute_"))
            }
        }
    }

    private fun loadBannedUsers() {
        db.collection("users").addSnapshotListener { result, _ ->
            bannedUsers.clear()

            result?.documents?.forEach {
                if (it.getBoolean("banned") == true) {
                    bannedUsers.add(it.id)
                }
            }

            adapter.notifyDataSetChanged()
        }
    }

    private fun banUser(uid: String) {
        db.collection("users").document(uid)
            .set(
                mapOf(
                    "banned" to true,
                    "banUntil" to System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                ),
                SetOptions.merge()
            )

        Toast.makeText(this, "User temporarily banned", Toast.LENGTH_LONG).show()
    }

    private fun deleteUserAccount(uid: String) {
        db.collection("users").document(uid)
            .update("banned", true)
    }
    private fun loadBlockedUsers() {
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("blockedUsers")
            .document(currentUid)
            .collection("list")
            .addSnapshotListener { result, _ ->

                blockedUsers.clear()

                result?.documents?.forEach {
                    blockedUsers.add(it.id)
                }

                adapter.notifyDataSetChanged()
            }
    }
}