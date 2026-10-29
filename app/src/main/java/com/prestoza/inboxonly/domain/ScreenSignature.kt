// ScreenSignature: a compact, content-free description of what's on screen, built by the accessibility
// service from only the view IDs and content descriptions that rules.json mentions. This is all the
// blocking logic ever sees: no message text, no full view tree.
package com.prestoza.inboxonly.domain

/**
 * What the service observed on one screen.
 *
 * @property windowClass the activity/window class from the last window-state event, if known.
 * @property viewIds rule-referenced view IDs present on screen.
 * @property selectedViewIds the subset of [viewIds] whose view is in the selected state (e.g. the active tab).
 * @property contentDescriptions rule-referenced content descriptions present on screen.
 * @property selectedContentDescriptions the subset of [contentDescriptions] on a selected view.
 * @property itemIdentity an opaque hash identifying the Reel or video on screen, used only in memory to
 *   notice a swipe or a move to another video. Never logged or stored.
 */
data class ScreenSignature(
    val packageName: String,
    val windowClass: String? = null,
    val viewIds: Set<String> = emptySet(),
    val selectedViewIds: Set<String> = emptySet(),
    val contentDescriptions: Set<String> = emptySet(),
    val selectedContentDescriptions: Set<String> = emptySet(),
    val itemIdentity: Int? = null,
)
