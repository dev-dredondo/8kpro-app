package tv.own.owntv.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.ui.components.compactCount
import java.util.Locale

/** Exercises quantity selection at the Android resource boundary, including non-English CLDR tags. */
class PluralResourceBoundaryTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun english_zero_one_two_use_plural_resources() {
        val context = localized("en-US")
        val zero = context.resources.getQuantityString(R.plurals.content_count_movies, 0, "Library", 0)
        val one = context.resources.getQuantityString(R.plurals.content_count_movies, 1, "Library", 1)
        val two = context.resources.getQuantityString(R.plurals.content_count_movies, 2, "Library", 2)
        assertTrue(zero.contains("movies"))
        assertTrue(one.contains("movie)"))
        assertTrue(two.contains("movies"))
    }

    @Test
    fun compact_counts_keep_the_pill_short() {
        val context = localized("en-US")
        assertTrue(compactCount(context, 999).contains("999"))
        assertTrue(compactCount(context, 1_000).contains("K"))
        assertTrue(compactCount(context, 125_000).contains("K"))
        assertTrue(compactCount(context, 1_000_000).contains("M"))
    }

    @Test
    fun arabic_polish_and_russian_quantity_requests_have_a_valid_fallback() {
        listOf("ar", "pl", "ru").forEach { tag ->
            val context = localized(tag)
            listOf(0, 1, 2, 5, 21, 102).forEach { count ->
                val text = context.resources.getQuantityString(
                    R.plurals.settings_epg_sources_catchup,
                    count,
                    count,
                )
                assertTrue("$tag/$count must render a non-empty plural", text.isNotBlank())
            }
        }
    }

    private fun localized(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }
}
