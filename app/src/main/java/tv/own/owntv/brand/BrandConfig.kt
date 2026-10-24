package tv.own.owntv.brand

import tv.own.owntv.BuildConfig

/**
 * 8K PRO: everything that makes this build "8K PRO" rather than OwnTV, in one place.
 *
 * The values come from app/build.gradle.kts (BuildConfig), so a different server or update repository
 * is a Gradle property away (`-Pbrand.serverUrl=…`, `-Pbrand.updateRepo=owner/repo`) — no code edit.
 */
object BrandConfig {
    /**
     * The one Xtream Codes server (the provider's "DNS") the app signs in to and loads from. Users
     * can no longer type a server, an M3U link or a portal: the login screen only asks for username and
     * password, and every playlist the app creates points here.
     */
    val serverUrl: String = BuildConfig.BRAND_SERVER_URL.trimEnd('/')

    /** GitHub `owner/repo` whose releases the in-app updater reads; blank turns the updater off. */
    val updateRepo: String = BuildConfig.BRAND_UPDATE_REPO.trim()

    val updatesEnabled: Boolean get() = updateRepo.isNotEmpty()

    /**
     * TMDB v3 API key for movie/series details and Trending. OwnTV's shared metadata server only
     * answers OwnTV's own signed builds, so this brand talks to api.themoviedb.org with its own key.
     */
    val tmdbApiKey: String = BuildConfig.BRAND_TMDB_API_KEY.trim()

    /** Host of [serverUrl] ("vpn.example.com"), for spotting it inside longer addresses. */
    val serverHost: String = runCatching { java.net.URI(serverUrl).host }.getOrNull().orEmpty()

    /**
     * Whether [url] points at the built-in server — any path or port on it (player_api.php, xmltv.php,
     * a stream). Used to keep the server's address off every screen.
     */
    fun isOnBrandServer(url: String?): Boolean {
        if (url.isNullOrBlank() || serverHost.isEmpty()) return false
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull() ?: return false
        return host.equals(serverHost, ignoreCase = true)
    }

    /** Whether [text] (an error message, a log line) mentions the built-in server anywhere. */
    fun mentionsServer(text: String?): Boolean =
        !text.isNullOrEmpty() && serverHost.isNotEmpty() && text.contains(serverHost, ignoreCase = true)

    /** Whether [url] is the built-in server (ignoring a trailing slash and letter case). */
    fun isBrandServer(url: String?): Boolean =
        url != null && url.trim().trimEnd('/').equals(serverUrl, ignoreCase = true)
}
