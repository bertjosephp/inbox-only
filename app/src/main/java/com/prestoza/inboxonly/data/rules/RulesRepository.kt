// RulesRepository: loads assets/rules.json once and keeps it in memory. "Assets" are raw files bundled
// inside the APK. If the file is missing or malformed we fall back to empty rules, which block nothing:
// a broken rules file must never break the phone.
package com.prestoza.inboxonly.data.rules

import android.content.Context
import com.prestoza.inboxonly.domain.Rules
import kotlinx.serialization.json.Json

/** Provides the parsed blocking rules. */
class RulesRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** The rules, parsed on first use. */
    val rules: Rules by lazy {
        val text =
            runCatching {
                appContext.assets
                    .open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrNull()
        text?.let(::parse) ?: Rules.EMPTY
    }

    companion object {
        const val ASSET_NAME = "rules.json"

        // Unknown keys are ignored so a newer rules file (with extra fields) still loads in an older app.
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses rules.json text, or returns null if it isn't valid. */
        fun parse(text: String): Rules? = runCatching { json.decodeFromString(Rules.serializer(), text) }.getOrNull()
    }
}
