const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = onDocumentCreated(
    "notifications/{notifId}",
    async (event) => {
        const data = event.data.data();
        const receiverUid = data.to;

        const receiverDoc = await admin.firestore()
            .collection("users").doc(receiverUid).get();
        const fcmToken = receiverDoc.data()?.fcmToken;

        if (!fcmToken) return null;

        const payload = {
            notification: {
                title: data.senderName,
                body: data.body
            },
            data: {
                senderId: data.senderUid,
                senderName: data.senderName,
                senderPhoto: data.senderPhoto || ""
            },
            token: fcmToken
        };

        return admin.messaging().send(payload);
    }
);