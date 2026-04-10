package com.proj.locktalk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.proj.locktalk.databinding.ActivityMainBinding
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userList: MutableList<User>
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userList = mutableListOf()

        adapter = UserAdapter(userList) { user ->
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
        loadCurrentUserPhoto()
        loadUsers()
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
            mapOf(
                "isOnline" to online,
                "lastSeen" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
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

    private fun loadUsers() {
        val currentUid = auth.currentUser?.uid
        db.collection("users")
            .addSnapshotListener { result, _ ->
                userList.clear()
                result?.documents?.forEach { doc ->
                    val user = doc.toObject(User::class.java)
                    if (user != null && user.uid != currentUid) {
                        userList.add(user)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }
}