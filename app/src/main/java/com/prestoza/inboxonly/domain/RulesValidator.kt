// RulesValidator: sanity checks for rules.json, run by unit tests against the shipped file so a typo
// (an unknown surface ID, a Reel rule pointing at a missing chat screen) fails CI, not the phone.
package com.prestoza.inboxonly.domain

/** Finds structural mistakes in [Rules]. */
object RulesValidator {
    /** Human-readable problems; empty when the rules are consistent. */
    fun problems(rules: Rules): List<String> =
        buildList {
            if (rules.version < 1) add("version must be >= 1")
            val duplicates =
                rules.apps
                    .groupBy { it.packageName }
                    .filterValues { it.size > 1 }
                    .keys
            duplicates.forEach { add("package $it appears more than once") }

            rules.apps.forEach { app ->
                val catalogApp = BlockCatalog.app(app.appId)
                if (catalogApp == null) {
                    add("${app.appId}: unknown appId (not in BlockCatalog)")
                    return@forEach
                }
                val surfaceIds = catalogApp.surfaces.map { it.id }.toSet()
                val allowedIds = app.allowed.map { it.id }.toSet()

                app.allowed.filter { it.match.isEmpty }.forEach { add("${app.appId}/${it.id}: empty match") }
                app.surfaces.forEach { surface ->
                    if (surface.id !in surfaceIds) add("${app.appId}/${surface.id}: unknown surface id")
                    if (surface.match.isEmpty) add("${app.appId}/${surface.id}: empty match")
                    if (surface.actions.isEmpty()) add("${app.appId}/${surface.id}: no actions")
                }
                app.reelViewer?.let { viewer ->
                    val prefix = "${app.appId}/reelViewer"
                    if (viewer.surfaceId !in surfaceIds) add("$prefix: unknown surface id ${viewer.surfaceId}")
                    if (viewer.match.isEmpty) add("$prefix: empty match")
                    if (viewer.openedFrom.isEmpty() && viewer.openedFromWindowClasses.isEmpty()) {
                        add("$prefix: needs openedFrom or openedFromWindowClasses")
                    }
                    viewer.openedFrom
                        .filter { it !in allowedIds }
                        .forEach { add("$prefix: openedFrom '$it' is not an allowed screen") }
                }
            }
        }
}
