package tv.own.owntv.features.login

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.brand.BrandConfig
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XtreamClient

/** What the login attempt found out. */
sealed interface LoginResult {
    data class Ok(val expiryMs: Long?) : LoginResult
    data object BadCredentials : LoginResult
    data object Expired : LoginResult
    /** The panel knows the account but it is not usable ("Banned", "Disabled", …), shown verbatim. */
    data class Inactive(val status: String) : LoginResult
    data object Unreachable : LoginResult
}

/**
 * 8K PRO: validates a username/password against the built-in server with the Xtream Codes API —
 * `GET {BrandConfig.serverUrl}/player_api.php?username=…&password=…` — and reads `user_info`.
 *
 * Goes through core's [XtreamClient] (and so core's OkHttp client), which means the request honours
 * the same proxy, DNS-over-HTTPS and User-Agent settings as every later sync and stream.
 */
class XtreamAuthenticator(private val xtream: XtreamClient) {

    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val probe = SourceEntity(
            name = "",
            type = SourceType.XTREAM,
            url = BrandConfig.serverUrl,
            username = username,
            password = password,
        )
        try {
            val info = xtream.fetchAccountStatus(probe)
            val status = info.status
            val expired = status.equals("Expired", ignoreCase = true) ||
                (info.expiryMs?.let { it <= System.currentTimeMillis() } == true)
            when {
                // `auth: 0`, or no `user_info` at all (some panels answer bad logins with `[]` or an
                // empty object, which parses to all defaults): either way, not a valid account.
                !info.authOk || status == null -> LoginResult.BadCredentials
                expired -> LoginResult.Expired
                !status.equals("Active", ignoreCase = true) -> LoginResult.Inactive(status)
                else -> LoginResult.Ok(info.expiryMs)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: HttpClient.HttpStatusException) {
            Log.i(TAG, "login http ${e.code}")
            if (e.code == 401 || e.code == 403) LoginResult.BadCredentials else LoginResult.Unreachable
        } catch (e: Exception) {
            Log.w(TAG, "login failed: ${e.javaClass.simpleName}")
            LoginResult.Unreachable
        }
    }

    private companion object {
        const val TAG = "XtreamAuth"
    }
}
