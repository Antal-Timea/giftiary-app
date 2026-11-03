package app.giftiary.presentation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * A string missing from the Hungarian resources silently falls back to English in the middle of a
 * Hungarian screen, and a translation that drops a placeholder loses the name or count it shows.
 */
class StringResourcesTest {

    private val english = load("values")
    private val hungarian = load("values-hu")

    @Test
    fun `every translatable string and plural has a Hungarian translation`() {
        assertTrue("found the strings", english.size > 500)
        assertEquals("missing in Hungarian", emptySet<String>(), english.keys - hungarian.keys)
        assertEquals("only in Hungarian", emptySet<String>(), hungarian.keys - english.keys)
    }

    @Test
    fun `translations keep the placeholders of the English text`() {
        english.forEach { (name, text) ->
            assertEquals(name, placeholders(text), placeholders(hungarian.getValue(name)))
        }
    }

    private fun placeholders(texts: List<String>) =
        texts.flatMap { text -> PLACEHOLDER.findAll(text).map { it.value }.toList() }.toSortedSet()

    private fun load(folder: String): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml"))
        val root = document.documentElement
        val strings = root.elements("string")
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to listOf(it.textContent) }
        val plurals = root.elements("plurals")
            .associate { plural -> "plurals/" + plural.getAttribute("name") to plural.elements("item").map { it.textContent } }
        return strings + plurals
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[sd]""")
    }
}
