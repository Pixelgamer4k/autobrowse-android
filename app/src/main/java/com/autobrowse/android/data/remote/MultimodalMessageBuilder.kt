package com.autobrowse.android.data.remote

import com.autobrowse.android.domain.model.AttachmentPayload
import org.json.JSONArray
import org.json.JSONObject

object MultimodalMessageBuilder {
    fun buildUserMessageJson(role: String, text: String, payload: AttachmentPayload): JSONObject {
        val hasMedia = payload.hasVisionContent || payload.attachments.any { it.videoBase64 != null }
        if (!hasMedia && payload.attachments.isEmpty()) {
            return JSONObject().put("role", role).put("content", text)
        }

        val parts = JSONArray()
        val fullText = buildString {
            append(text)
            val context = payload.buildContextBlock()
            if (context.isNotBlank()) {
                append("\n\n--- Attachments ---\n")
                append(context)
            }
        }
        parts.put(JSONObject().put("type", "text").put("text", fullText))

        payload.attachments.forEach { processed ->
            processed.imageBase64Parts.forEach { dataUrl ->
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", dataUrl).put("detail", "auto"),
                        ),
                )
            }
            processed.videoBase64?.let { base64 ->
                val mime = processed.attachment.mimeType.ifBlank { "video/mp4" }
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject()
                                .put("url", "data:$mime;base64,$base64")
                                .put("detail", "auto"),
                        ),
                )
            }
        }

        return JSONObject().put("role", role).put("content", parts)
    }

    fun injectIntoRequestBody(baseJson: String, multimodalIndices: Map<Int, JSONObject>): String {
        val root = JSONObject(baseJson)
        val messages = root.getJSONArray("messages")
        val newMessages = JSONArray()
        for (i in 0 until messages.length()) {
            val replacement = multimodalIndices[i]
            newMessages.put(replacement ?: messages.getJSONObject(i))
        }
        root.put("messages", newMessages)
        return root.toString()
    }
}