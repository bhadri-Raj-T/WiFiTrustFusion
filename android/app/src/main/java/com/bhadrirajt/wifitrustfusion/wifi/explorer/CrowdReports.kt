package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only local opt-in contribution drafts. NO upload, NO SSID/BSSID/GPS/device ID. */
internal object CrowdReports {
    // Avoid Android backup of draft reports by storing them in noBackupFilesDir.
    private fun file(context: Context): File = File(context.noBackupFilesDir, "wtf_crowd_drafts_v1.json")

    data class Draft(val category: String, val note: String, val epochMs: Long)

    fun read(context: Context): List<Draft> {
        val raw = try { file(context).takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: "[]" } catch (_: Exception) { "[]" }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                Draft(obj.optString("category"), obj.optString("note"), obj.optLong("time"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(context: Context, category: String, note: String): Draft {
        require(category in allowedCategories)
        val safeNote = note.trim().take(160)
        val draft = Draft(category, safeNote, System.currentTimeMillis())
        val list = (listOf(draft) + read(context)).take(30)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("category", it.category).put("note", it.note).put("time", it.epochMs))
        }
        val output = file(context)
        val temporary = File(context.noBackupFilesDir, "wtf_crowd_drafts_v1.tmp")
        temporary.writeText(arr.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(output)) {
            output.writeText(arr.toString(), Charsets.UTF_8)
            temporary.delete()
        }
        return draft
    }

    fun deleteAll(context: Context) {
        file(context).delete()
    }

    val allowedCategories = listOf(
        "Worked normally", "Captive portal", "Unexpected login", "Poor performance", "Other experience"
    )

    fun shareText(draft: Draft): String = "WiFiTrustFusion community report (voluntarily shared)\n" +
        "Category: ${draft.category}\n" +
        "Description: ${draft.note}\n" +
        "User report only; not verified. No SSID, BSSID or GPS was automatically attached."
}
