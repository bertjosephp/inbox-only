// InspectorFormat (debug builds only): pure text formatting for the screen inspector, kept apart from
// Android types so the redaction rules are unit tested.
package com.prestoza.inboxonly.debug

/** Formatting and redaction rules for inspector output. */
object InspectorFormat {
    /** Longer strings are probably content (captions, messages), so only their length is shown. */
    const val MAX_VISIBLE_CHARS = 30

    /** Returns [value] if short, `‹N chars›` if long, or null if blank. Newlines are flattened. */
    fun redact(value: CharSequence?): String? {
        if (value.isNullOrBlank()) return null
        val flat = value.toString().replace('\n', ' ').trim()
        return if (flat.length > MAX_VISIBLE_CHARS) "‹${flat.length} chars›" else flat
    }

    /** Formats one node as a single indented line, or null if it carries nothing useful for rules. */
    fun nodeLine(
        depth: Int,
        className: CharSequence?,
        viewId: String?,
        contentDescription: CharSequence?,
        text: CharSequence?,
        selected: Boolean,
    ): String? {
        val desc = redact(contentDescription)
        val shownText = redact(text)
        if (viewId == null && desc == null && shownText == null && !selected) return null
        return buildString {
            append("  ".repeat(depth))
            append(className?.toString()?.substringAfterLast('.') ?: "?")
            viewId?.let { append(" id=").append(it) }
            desc?.let { append(" desc=\"").append(it).append('"') }
            shownText?.let { append(" text=\"").append(it).append('"') }
            if (selected) append(" [selected]")
        }
    }
}
