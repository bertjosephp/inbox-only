package com.prestoza.inboxonly.data.rules

import com.prestoza.inboxonly.domain.AppRules
import com.prestoza.inboxonly.domain.BlockAction
import com.prestoza.inboxonly.domain.FixtureScreens
import com.prestoza.inboxonly.domain.Match
import com.prestoza.inboxonly.domain.ReelViewerRule
import com.prestoza.inboxonly.domain.Rules
import com.prestoza.inboxonly.domain.RulesValidator
import com.prestoza.inboxonly.domain.SurfaceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RulesTest {
    private val shipped = File("src/main/assets/rules.json")

    @Test
    fun `shipped rules json parses and is valid`() {
        val rules = RulesRepository.parse(shipped.readText())
        assertNotNull("assets/rules.json must parse", rules)
        rules!!
        assertEquals(emptyList<String>(), RulesValidator.problems(rules))
        assertTrue(rules.version >= 1)
    }

    @Test
    fun `shipped rules are real, not placeholders, and cover every app`() {
        val rules = FixtureScreens.rules
        assertTrue(!rules.placeholder)
        assertEquals(setOf("instagram", "facebook", "youtube", "reddit"), rules.apps.map { it.appId }.toSet())
        rules.apps.forEach { assertTrue("${it.appId} has no allowed screens", it.allowed.isNotEmpty()) }
    }

    @Test
    fun `malformed json fails to parse instead of crashing`() {
        assertNull(RulesRepository.parse("{ not json"))
        assertNull(RulesRepository.parse("""{"apps": []}""")) // missing version
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val rules = RulesRepository.parse("""{"version": 2, "futureField": true, "apps": []}""")
        assertEquals(2, rules?.version)
    }

    @Test
    fun `every action type parses`() {
        val json =
            """
            {"version": 1, "apps": [{"appId": "youtube", "package": "p", "surfaces": [{"id": "shorts",
              "match": {"allViewIds": ["fixture:id/a"]},
              "actions": [{"type": "redirect", "uri": "fixture://x", "package": "p"}, {"type": "launch", "package": "q"},
                          {"type": "back"}, {"type": "home"}]}]}]}
            """.trimIndent()
        val actions =
            RulesRepository
                .parse(json)!!
                .apps
                .single()
                .surfaces
                .single()
                .actions
        assertEquals(
            listOf(
                BlockAction.Redirect("fixture://x", "p"),
                BlockAction.Launch("q"),
                BlockAction.Back,
                BlockAction.Home,
            ),
            actions,
        )
    }

    @Test
    fun `surface actions default to back`() {
        val json =
            """
            {"version": 1, "apps": [{"appId": "reddit", "package": "p",
              "surfaces": [{"id": "popular", "match": {"allViewIds": ["fixture:id/a"]}}]}]}
            """.trimIndent()
        assertEquals(
            listOf(BlockAction.Back),
            RulesRepository
                .parse(json)!!
                .apps
                .single()
                .surfaces
                .single()
                .actions,
        )
    }

    @Test
    fun `validator reports unknown ids, empty matches and dangling reel links`() {
        val broken =
            Rules(
                version = 1,
                apps =
                    listOf(
                        AppRules(
                            appId = "instagram",
                            packageName = "p",
                            surfaces =
                                listOf(
                                    SurfaceRule("not_a_surface", Match()),
                                    SurfaceRule("explore", Match(), emptyList()),
                                ),
                            reelViewer =
                                ReelViewerRule(
                                    Match(allViewIds = listOf("fixture:id/r")),
                                    "reels_tab",
                                    listOf("dm_thread"),
                                ),
                        ),
                        AppRules(appId = "tiktok", packageName = "q"),
                    ),
            )
        val problems = RulesValidator.problems(broken)
        assertTrue(problems.any { "unknown surface id" in it })
        assertTrue(problems.any { "empty match" in it })
        assertTrue(problems.any { "no actions" in it })
        assertTrue(problems.any { "openedFrom 'dm_thread'" in it })
        assertTrue(problems.any { "tiktok" in it })
    }
}
