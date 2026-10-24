package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A held OK on a Video player row opens the pin menu, and that menu looks the row up with
 * `VIDEO_QUICK_ROWS.first { it.key == key }`. A row that declares a `quickKey` without an entry in
 * the catalogue therefore **crashes the app** with `NoSuchElementException` the moment it is held —
 * which is exactly what shipped for Multiview and Multiview tiles: both rows were pinnable, neither
 * was listed, and `dialogForQuickKey` even had a branch for one of them.
 *
 * The three lists have to agree, and nothing in the compiler makes them. This reads the screen's own
 * source rather than loading the class, because the file's top-level properties build Compose
 * brushes that have no business running in a JVM unit test.
 */
class VideoQuickRowCatalogueTest {

    private val source: String by lazy {
        val file = File("src/main/java/tv/own/owntv/features/settings/VideoPlayerSettingsScreen.kt")
        assertTrue("expected to run from the app module, cwd=${File(".").absolutePath}", file.isFile)
        file.readText()
    }

    /** Every `quickKey = "…"` a row on the screen declares. */
    private val declaredKeys: List<String> by lazy {
        Regex("""quickKey\s*=\s*"([^"]+)"""").findAll(source).map { it.groupValues[1] }.toList()
    }

    /** Every key listed in `VIDEO_QUICK_ROWS`. */
    private val catalogueKeys: List<String> by lazy {
        Regex("""VideoQuickRef\("([^"]+)"""").findAll(source).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `every pinnable row has a catalogue entry`() {
        assertTrue("no quickKey rows found — did the regex or the screen change?", declaredKeys.isNotEmpty())
        assertEquals(
            "rows offer 'Pin to Quick' but are missing from VIDEO_QUICK_ROWS, so holding OK on them crashes",
            emptyList<String>(),
            declaredKeys.filterNot { it in catalogueKeys }.sorted(),
        )
    }

    @Test
    fun `the catalogue lists no row that does not exist`() {
        assertEquals(
            "VIDEO_QUICK_ROWS entries with no row on the screen — Quick would show a pin that leads nowhere",
            emptyList<String>(),
            catalogueKeys.filterNot { it in declaredKeys }.sorted(),
        )
    }

    @Test
    fun `every pinnable row has a binding branch`() {
        // videoQuickBinding decides what a pinned copy shows and does. A key with no branch falls to
        // the else and the pin renders as a value-less link, which is wrong for a plain toggle.
        val branchKeys = Regex(""""(vp_[a-z_0-9]+)"\s*->""").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(
            "pinnable rows with no videoQuickBinding branch — their Quick copy would show no value",
            emptyList<String>(),
            declaredKeys.filterNot { it in branchKeys }.sorted(),
        )
    }

    @Test
    fun `keys are unique`() {
        assertEquals("duplicate quickKey on the screen", declaredKeys.distinct().size, declaredKeys.size)
        assertEquals("duplicate key in VIDEO_QUICK_ROWS", catalogueKeys.distinct().size, catalogueKeys.size)
    }
}
