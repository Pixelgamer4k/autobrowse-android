package com.autobrowse.android.notes

import com.autobrowse.android.domain.model.Note
import com.autobrowse.android.domain.model.NoteBlock
import com.autobrowse.android.domain.model.NoteBlockType
import com.autobrowse.android.domain.model.NoteTextStyle
import org.json.JSONArray
import org.json.JSONObject

object NoteJson {
    fun serializeBlocks(blocks: List<NoteBlock>): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(
                JSONObject().apply {
                    put("id", block.id)
                    put("type", block.type.name)
                    put("markdown", block.markdown)
                    put("localPath", block.localPath)
                    put("caption", block.caption)
                    put("strokeJson", block.strokeJson)
                    block.style?.let { put("style", serializeStyle(it)) }
                },
            )
        }
        return JSONObject().put("blocks", array).toString()
    }

    fun deserializeBlocks(json: String): List<NoteBlock> {
        if (json.isBlank() || json == "{}") {
            return listOf(NoteBlock(id = "default", type = NoteBlockType.TEXT))
        }
        return runCatching {
            val root = JSONObject(json)
            val array = root.optJSONArray("blocks") ?: return listOf(
                NoteBlock(id = "default", type = NoteBlockType.TEXT, markdown = json),
            )
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        NoteBlock(
                            id = obj.optString("id", "block-$i"),
                            type = runCatching {
                                NoteBlockType.valueOf(obj.getString("type"))
                            }.getOrDefault(NoteBlockType.TEXT),
                            markdown = obj.optString("markdown"),
                            localPath = obj.optString("localPath").takeIf { it.isNotBlank() },
                            caption = obj.optString("caption").takeIf { it.isNotBlank() },
                            strokeJson = obj.optString("strokeJson").takeIf { it.isNotBlank() },
                            style = obj.optJSONObject("style")?.let { deserializeStyle(it) },
                        ),
                    )
                }
            }
        }.getOrDefault(listOf(NoteBlock(id = "default", type = NoteBlockType.TEXT)))
    }

    fun serializeTags(tags: List<String>): String = tags.joinToString(",")

    fun deserializeTags(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun noteToPlainText(note: Note): String = note.plainText

    private fun serializeStyle(style: NoteTextStyle): JSONObject = JSONObject().apply {
        put("fontSizeSp", style.fontSizeSp)
        put("bold", style.bold)
        put("italic", style.italic)
        put("underline", style.underline)
        put("strikethrough", style.strikethrough)
        put("textColor", style.textColor)
        put("highlightColor", style.highlightColor)
        put("alignment", style.alignment)
        put("fontFamily", style.fontFamily)
    }

    private fun deserializeStyle(obj: JSONObject): NoteTextStyle = NoteTextStyle(
        fontSizeSp = obj.optDouble("fontSizeSp", 16.0).toFloat(),
        bold = obj.optBoolean("bold"),
        italic = obj.optBoolean("italic"),
        underline = obj.optBoolean("underline"),
        strikethrough = obj.optBoolean("strikethrough"),
        textColor = obj.optString("textColor").takeIf { it.isNotBlank() },
        highlightColor = obj.optString("highlightColor").takeIf { it.isNotBlank() },
        alignment = obj.optString("alignment", "start"),
        fontFamily = obj.optString("fontFamily", "default"),
    )
}