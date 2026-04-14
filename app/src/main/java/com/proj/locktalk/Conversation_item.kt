package com.proj.locktalk

data class ConversationItem(
    val user: User,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0
)