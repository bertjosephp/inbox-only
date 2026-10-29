// Rules: the in-memory form of assets/rules.json, the data-driven description of which screens in each
// target app are allowed or blocked. Every per-app identifier (view IDs, window classes, content
// descriptions, deep links) lives in that JSON, never in Kotlin, so an app UI update is fixed with a
// JSON edit. See docs/TECHNICAL.md for the full format.
package com.prestoza.inboxonly.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The whole rules file. When [placeholder] is true, nothing is ever blocked. */
@Serializable
data class Rules(
    val version: Int,
    val placeholder: Boolean = false,
    val comment: String? = null,
    val apps: List<AppRules> = emptyList(),
) {
    /** Rules for [packageName], or null if we have none (which means: allow everything). */
    fun forPackage(packageName: String): AppRules? = apps.firstOrNull { it.packageName == packageName }

    companion object {
        /** Used when rules.json is missing or unreadable: blocks nothing. */
        val EMPTY = Rules(version = 0, placeholder = true)
    }
}

/**
 * Rules for one app.
 *
 * @property appId links to [BlockCatalog] and the user's settings.
 * @property allowed screens that are always allowed (DMs, threads). Checked first; they win over everything.
 * @property surfaces blockable screens, checked in order; the first match decides.
 * @property reelViewer optional "Reel opened from a chat" handling (see [ReelFromChatTracker]).
 */
@Serializable
data class AppRules(
    val appId: String,
    @SerialName("package") val packageName: String,
    val allowed: List<ScreenRule> = emptyList(),
    val surfaces: List<SurfaceRule> = emptyList(),
    val reelViewer: ReelViewerRule? = null,
) {
    /** Every view ID any rule for this app mentions: the only IDs the service ever looks up. */
    val referencedViewIds: Set<String> by lazy {
        (allowed.map { it.match } + surfaces.map { it.match } + listOfNotNull(reelViewer?.match))
            .flatMap { it.referencedViewIds }
            .plus(listOfNotNull(reelViewer?.identityViewId))
            .toSet()
    }

    /** Compiled [ReelViewerRule.identityDescriptionPattern], if any. */
    val reelIdentityDescriptionRegex: Regex? by lazy { reelViewer?.identityDescriptionPattern?.let(::Regex) }

    /** Every content description any rule for this app mentions. */
    val referencedContentDescriptions: Set<String> by lazy {
        (allowed.map { it.match } + surfaces.map { it.match } + listOfNotNull(reelViewer?.match))
            .flatMap { it.contentDescriptions + it.selectedContentDescriptions }
            .toSet()
    }
}

/** A named screen, e.g. `dm_thread`. */
@Serializable
data class ScreenRule(
    val id: String,
    val match: Match,
)

/**
 * A blockable screen. [id] must be a surface ID from [BlockCatalog] for this app, so the user's
 * per-surface toggle applies. [actions] are tried in order until one works; Home is always the last resort.
 */
@Serializable
data class SurfaceRule(
    val id: String,
    val match: Match,
    val actions: List<BlockAction> = listOf(BlockAction.Back),
)

/**
 * The full-screen Reel viewer.
 *
 * @property surfaceId the [BlockCatalog] surface whose toggle governs Reels in this app.
 * @property openedFrom IDs of [AppRules.allowed] screens that count as "a chat" for the allow-one rule.
 * @property openedFromWindowClasses windows that only open when a Reel is tapped in a chat in another
 *   app (Facebook's viewer opened from Messenger). Opening one counts as coming from a chat.
 * @property identityViewId a view whose content description (or text) differs per Reel, so swiping to
 *   another Reel can be noticed. Hashed in memory only; never logged or stored. Optional.
 * @property identityDescriptionPattern the same idea for apps with obfuscated view IDs: the first
 *   content description matching this regex identifies the Reel. Optional.
 */
@Serializable
data class ReelViewerRule(
    val match: Match,
    val surfaceId: String,
    val openedFrom: List<String> = emptyList(),
    val openedFromWindowClasses: List<String> = emptyList(),
    val identityViewId: String? = null,
    val identityDescriptionPattern: String? = null,
    val actions: List<BlockAction> = listOf(BlockAction.Back),
)

/**
 * Conditions on a [ScreenSignature]. All non-empty conditions must hold. A match with no conditions at
 * all matches nothing, so a half-written rule can never block everything.
 */
@Serializable
data class Match(
    val windowClasses: List<String> = emptyList(),
    val allViewIds: List<String> = emptyList(),
    val anyViewIds: List<String> = emptyList(),
    val noneViewIds: List<String> = emptyList(),
    val selectedViewIds: List<String> = emptyList(),
    // Instagram marks no tab as selected until one is tapped, so a cold-started feed is recognised by
    // "tab bar present, nothing selected".
    val noneSelectedViewIds: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val selectedContentDescriptions: List<String> = emptyList(),
) {
    /** True when this match has no conditions (and therefore never matches). */
    val isEmpty: Boolean
        get() =
            windowClasses.isEmpty() && allViewIds.isEmpty() && anyViewIds.isEmpty() && noneViewIds.isEmpty() &&
                selectedViewIds.isEmpty() && noneSelectedViewIds.isEmpty() && contentDescriptions.isEmpty() &&
                selectedContentDescriptions.isEmpty()

    /** View IDs this match needs the service to look up. */
    val referencedViewIds: List<String>
        get() = allViewIds + anyViewIds + noneViewIds + selectedViewIds + noneSelectedViewIds

    /** True when [signature] satisfies every condition. */
    fun matches(signature: ScreenSignature): Boolean {
        if (isEmpty) return false
        return (windowClasses.isEmpty() || signature.windowClass in windowClasses) &&
            signature.viewIds.containsAll(allViewIds) &&
            (anyViewIds.isEmpty() || anyViewIds.any { it in signature.viewIds }) &&
            noneViewIds.none { it in signature.viewIds } &&
            signature.selectedViewIds.containsAll(selectedViewIds) &&
            noneSelectedViewIds.none { it in signature.selectedViewIds } &&
            (contentDescriptions.isEmpty() || contentDescriptions.any { it in signature.contentDescriptions }) &&
            signature.selectedContentDescriptions.containsAll(selectedContentDescriptions)
    }
}

/** What to do when a blocked screen appears. Serialized with a `"type"` field. */
@Serializable
sealed interface BlockAction {
    /** Open a deep link, e.g. Instagram's DM inbox. Must be verified on a real device before use. */
    @Serializable
    @SerialName("redirect")
    data class Redirect(
        val uri: String,
        @SerialName("package") val packageName: String? = null,
    ) : BlockAction

    /** Launch another app's main screen, e.g. Messenger instead of the Facebook feed. */
    @Serializable
    @SerialName("launch")
    data class Launch(
        @SerialName("package") val packageName: String,
    ) : BlockAction

    /** Press Back. */
    @Serializable
    @SerialName("back")
    data object Back : BlockAction

    /** Go to the home screen, closing the app. */
    @Serializable
    @SerialName("home")
    data object Home : BlockAction
}
