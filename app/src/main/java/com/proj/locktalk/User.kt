package com.proj.locktalk

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val status: String = "Hey there! I am using LockTalk",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val isTyping: Boolean = false,
    val fcmToken: String = ""
)