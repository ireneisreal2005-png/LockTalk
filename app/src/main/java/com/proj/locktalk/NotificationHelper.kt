package com.proj.locktalk

import com.google.firebase.firestore.FirebaseFirestore

object NotificationHelper {
    fun sendNotification(
        db: FirebaseFirestore,
        receiverUid: String,
        senderName: String,
        senderUid: String,
        senderPhoto: String,
        messageBody: String
    ) {
        // Store a notification trigger in Firestore
        // A Cloud Function will pick this up and send via FCM
        db.collection("notifications").add(
            mapOf(
                "to" to receiverUid,
                "senderName" to senderName,
                "senderUid" to senderUid,
                "senderPhoto" to senderPhoto,
                "body" to messageBody,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}