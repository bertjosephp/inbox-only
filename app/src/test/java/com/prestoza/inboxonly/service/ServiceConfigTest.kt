package com.prestoza.inboxonly.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards the link between the accessibility service's package filter and rules.json. */
class ServiceConfigTest {
    // Unit tests run with the module directory (app/) as the working directory.
    private val serviceXml = File("src/main/res/xml/accessibility_service_config.xml")
    private val rulesJson = File("src/main/assets/rules.json")

    private fun serviceAttribute(name: String): String {
        val doc =
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(
                serviceXml,
            )
        return doc.documentElement.getAttributeNS("http://schemas.android.com/apk/res/android", name)
    }

    private fun servicePackages(): Set<String> =
        serviceAttribute("packageNames")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun rulesPackages(): Set<String> =
        Json
            .parseToJsonElement(rulesJson.readText())
            .jsonObject["apps"]!!
            .jsonArray
            .map { it.jsonObject["package"]!!.jsonPrimitive.content }
            .toSet()

    @Test
    fun `service listens to exactly the packages rules json covers`() {
        assertEquals(rulesPackages(), servicePackages())
    }

    @Test
    fun `service package filter is never empty, which would mean every app`() {
        assertEquals(4, servicePackages().size)
    }

    @Test
    fun `service listens only to window state and content changes`() {
        assertEquals("typeWindowStateChanged|typeWindowContentChanged", serviceAttribute("accessibilityEventTypes"))
    }

    @Test
    fun `messenger is never watched`() {
        assertTrue("com.facebook.orca" !in servicePackages())
    }
}
