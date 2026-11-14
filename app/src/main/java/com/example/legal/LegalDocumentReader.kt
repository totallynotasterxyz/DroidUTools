// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.legal

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class ThirdPartyTermItem(
    val id: String,
    val name: String,
    val category: String,
    val dataSummary: String,
    val officialUrl: String,
    val license: String
)

enum class LegalDocType(val fileName: String, val title: String) {
    TERMS_OF_SERVICE("terms_of_service.md", "Terms of Service"),
    PRIVACY_POLICY("privacy_policy.md", "Privacy Policy"),
    DISCLAIMERS("disclaimers.md", "Disclaimers"),
    OPEN_SOURCE_LICENSES("open_source_licenses.md", "Open-Source Licenses"),
    THIRD_PARTY_TERMS("third_party_terms.md", "Third-Party Terms")
}

object LegalDocumentReader {

    fun loadDocument(context: Context, docType: LegalDocType): String {
        return try {
            val assetPath = "legal/${docType.fileName}"
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            "# Error Loading Document\n\nCould not load ${docType.title}. Please contact Aster at spaminhaler1276@gmail.com."
        }
    }

    fun loadThirdPartyTerms(context: Context): List<ThirdPartyTermItem> {
        val result = mutableListOf<ThirdPartyTermItem>()
        try {
            val jsonString = context.assets.open("legal/third_party_terms.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    ThirdPartyTermItem(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        category = obj.optString("category"),
                        dataSummary = obj.optString("data_summary"),
                        officialUrl = obj.optString("official_url"),
                        license = obj.optString("license")
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }
        return result
    }
}
