package com.proj.locktalk

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object SafeBrowsingHelper {

    private const val API_KEY = "AIzaSyBTu8VU7Do7SXAWBz_7RAonVlkeKIfsi-Y"

    fun checkUrl(url: String, callback: (Boolean) -> Unit) {

        val clientJson = JSONObject().apply {
            put("clientId", "locktalk")
            put("clientVersion", "1.0")
        }

        val threatEntry = JSONObject().apply {
            put("url", url)
        }

        val threatEntries = org.json.JSONArray().apply {
            put(threatEntry)
        }

        val threatTypes = org.json.JSONArray().apply {
            put("MALWARE")
            put("SOCIAL_ENGINEERING")
        }

        val platformTypes = org.json.JSONArray().apply {
            put("ANY_PLATFORM")
        }

        val threatEntryTypes = org.json.JSONArray().apply {
            put("URL")
        }

        val threatInfo = JSONObject().apply {
            put("threatTypes", threatTypes)
            put("platformTypes", platformTypes)
            put("threatEntryTypes", threatEntryTypes)
            put("threatEntries", threatEntries)
        }

        val mainJson = JSONObject().apply {
            put("client", clientJson)
            put("threatInfo", threatInfo)
        }

        val body = RequestBody.create(
            MediaType.parse("application/json"),
            mainJson.toString()
        )

        val request = Request.Builder()
            .url("https://safebrowsing.googleapis.com/v4/threatMatches:find?key=$API_KEY")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(true)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body()?.string() ?: ""

                println("SafeBrowsing Response: $responseBody")

                if (responseBody.isEmpty()) {
                    callback(true)
                    return
                }

                val json = JSONObject(responseBody)
                val isUnsafe = json.has("matches")

                callback(!isUnsafe)
            }
        })
    }
}