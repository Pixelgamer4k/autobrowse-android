package com.autobrowse.android.attachments

import com.autobrowse.android.domain.model.AttachmentType
import com.autobrowse.android.domain.model.StoredAttachment
import org.json.JSONArray
import org.json.JSONObject

object AttachmentJson {
    fun serialize(attachments: List<StoredAttachment>): String {
        val array = JSONArray()
        attachments.forEach { att ->
            array.put(
                JSONObject().apply {
                    put("id", att.id)
                    put("type", att.type.name)
                    put("fileName", att.fileName)
                    put("localPath", att.localPath)
                    put("mimeType", att.mimeType)
                    put("sizeBytes", att.sizeBytes)
                },
            )
        }
        return JSONObject().put("attachments", array).toString()
    }

    fun deserialize(json: String): List<StoredAttachment> {
        if (json.isBlank() || json == "{}") return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val array = root.optJSONArray("attachments") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        StoredAttachment(
                            id = obj.getString("id"),
                            type = AttachmentType.valueOf(obj.getString("type")),
                            fileName = obj.getString("fileName"),
                            localPath = obj.getString("localPath"),
                            mimeType = obj.getString("mimeType"),
                            sizeBytes = obj.optLong("sizeBytes"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}