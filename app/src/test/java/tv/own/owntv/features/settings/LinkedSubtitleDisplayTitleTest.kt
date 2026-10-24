package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedSubtitleDisplayTitleTest {

    @Test
    fun `new raw episode title supplies locale-neutral formatting parts`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `matching legacy suffix is stripped before localized formatting`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad · S1E3", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `mismatched suffix remains part of the provider title`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad · S2E5", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad · S2E5", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `movie and malformed episode keys need no episode formatting`() {
        assertNull(episodeDisplayTitleParts("MOVIE", "Inception", "movie:tmdb:456"))
        assertNull(episodeDisplayTitleParts("SERIES", "Breaking Bad", "episode:tmdb:123"))
    }
}
