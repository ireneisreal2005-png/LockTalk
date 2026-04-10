package com.proj.locktalk

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val mediaPermission: String = "allow_save",
    val viewed: Boolean = false
)