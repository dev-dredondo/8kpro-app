package tv.own.owntv.features.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.own.owntv.brand.BrandConfig
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.SourceType

/** Why the last attempt was refused; null while there is nothing to report. */
sealed interface LoginError {
    data object Empty : LoginError
    data object BadCredentials : LoginError
    data object Expired : LoginError
    data class Inactive(val status: String) : LoginError
    data object Network : LoginError
}

data class LoginUi(
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: LoginError? = null,
)

/**
 * 8K PRO login. A successful sign-in is saved to [SessionStore]; MainActivity observes the store and
 * moves on by itself (first run → automatic setup, otherwise straight into the app).
 */
class LoginViewModel(
    private val authenticator: XtreamAuthenticator,
    private val sessionStore: SessionStore,
    private val sourceDao: SourceDao,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    fun setUsername(value: String) = _ui.update { it.copy(username = value.trim(), error = null) }

    fun setPassword(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun login() {
        val current = _ui.value
        if (current.busy) return
        val user = current.username.trim()
        val pass = current.password
        if (user.isEmpty() || pass.isEmpty()) {
            _ui.update { it.copy(error = LoginError.Empty) }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = authenticator.login(user, pass)) {
                is LoginResult.Ok -> {
                    adoptCredentials(user, pass)
                    sessionStore.save(Session(user, pass))
                    null
                }
                LoginResult.BadCredentials -> LoginError.BadCredentials
                LoginResult.Expired -> LoginError.Expired
                is LoginResult.Inactive -> LoginError.Inactive(result.status)
                LoginResult.Unreachable -> LoginError.Network
            }
            // On success the screen leaves the composition; clearing the password still matters if
            // this ViewModel outlives it (the activity-scoped store).
            _ui.update { it.copy(busy = false, error = error, password = if (error == null) "" else it.password) }
        }
    }

    /**
     * Signing in again after "Sign out" (possibly as another account): the playlists already on the
     * device keep their catalogue and favourites, and only their credentials change. Playlists the
     * app did not create (a restored backup's) are left alone.
     */
    private suspend fun adoptCredentials(user: String, pass: String) {
        runCatching {
            sourceDao.getAllOnce()
                .filter { it.type == SourceType.XTREAM && BrandConfig.isBrandServer(it.url) }
                .filter { it.username != user || it.password != pass }
                .forEach { sourceDao.update(it.copy(username = user, password = pass)) }
        }
    }
}
