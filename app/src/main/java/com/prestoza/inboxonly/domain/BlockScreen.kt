// BlockScreen: what the block screen says and offers for one blocked surface, e.g. "Instagram ·
// Home feed is blocked" with "Open DMs" and "Close Instagram". Pure Kotlin so the wording rules are
// unit tested; BlockScreenActivity only draws it.
package com.prestoza.inboxonly.domain

/**
 * The block screen's content.
 *
 * @property primaryAction the way out the rules prefer (the surface's first action), shown as the main button.
 * @property showClose whether to also offer "Close ‹app›" (not needed when the main button already does that).
 */
data class BlockScreen(
    val appLabel: String,
    val title: String,
    val primaryAction: BlockAction,
    val primaryLabel: String,
    val closeLabel: String,
    val showClose: Boolean,
)

/** Builds [BlockScreen]s from a block decision. */
object BlockScreenFormatter {
    /** The screen for [surfaceId] in [appId] with the rule's [actions], or null for an unknown app. */
    fun forBlock(
        appId: String,
        surfaceId: String,
        actions: List<BlockAction>,
    ): BlockScreen? {
        val app = BlockCatalog.app(appId) ?: return null
        val surfaceLabel = app.surfaces.firstOrNull { it.id == surfaceId }?.label ?: "This screen"
        val closeLabel = "Close ${app.label}"
        val primary = actions.firstOrNull() ?: BlockAction.Home
        val primaryLabel =
            when (primary) {
                is BlockAction.Redirect -> primary.label ?: "Continue"
                is BlockAction.Launch -> primary.label ?: "Continue"
                BlockAction.Back -> "Go back"
                BlockAction.Home -> closeLabel
            }
        return BlockScreen(
            appLabel = app.label,
            title = "$surfaceLabel is blocked",
            primaryAction = primary,
            primaryLabel = primaryLabel,
            closeLabel = closeLabel,
            showClose = primary != BlockAction.Home,
        )
    }
}
