package tv.own.owntv.features.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.setup.WizardAction
import tv.own.owntv.features.setup.WizardFrame
import tv.own.owntv.features.setup.WizardStep
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * 8K PRO sign-in, built for the remote control.
 *
 * It reuses the setup wizard's frame and the settings form rows, which already solve the hard part of
 * text entry on a TV: each field is a focusable row (D-pad ▲▼ moves between Username, Password and the
 * Login button); OK opens the on-screen keyboard for that row only, and its Done/OK closes it and
 * returns focus to the row, so the D-pad is never trapped inside a text field. Back on the remote
 * leaves the app (there is nothing behind the login).
 *
 * The server is not asked for: it is [tv.own.owntv.brand.BrandConfig.serverUrl].
 */
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    val vm: LoginViewModel = koinViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { userFocus.requestFocus() }
    }
    // A refused attempt puts focus back where the fix is: the username when it is empty or wrong.
    LaunchedEffect(ui.error) {
        when (ui.error) {
            LoginError.Empty -> runCatching { (if (ui.username.isEmpty()) userFocus else passFocus).requestFocus() }
            LoginError.BadCredentials -> runCatching { userFocus.requestFocus() }
            else -> Unit
        }
    }

    val userLabel = stringResource(R.string.login_username)
    val passLabel = stringResource(R.string.login_password)
    val help = stringResource(R.string.login_field_help)
    val sub = when (val e = ui.error) {
        null -> if (ui.busy) stringResource(R.string.login_checking) else stringResource(R.string.login_subtitle)
        LoginError.Empty -> stringResource(R.string.login_error_empty)
        LoginError.BadCredentials -> stringResource(R.string.login_error_credentials)
        LoginError.Expired -> stringResource(R.string.login_error_expired)
        is LoginError.Inactive -> stringResource(R.string.login_error_status, e.status)
        LoginError.Network -> stringResource(R.string.login_error_network)
    }

    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.login_title),
        sub = sub,
        back = null,
        next = WizardAction(stringResource(R.string.login_button), vm::login, loginFocus, enabled = !ui.busy),
        onBack = null,
    ) {
        StageFieldRow(
            icon = OwnTVIcon.PERSON,
            label = userLabel,
            value = ui.username,
            onValueChange = vm::setUsername,
            help = SettingHelp(userLabel, help),
            placeholder = stringResource(R.string.login_username_hint),
            keyboardType = KeyboardType.Email, // no autocorrect/capitalisation on most TV keyboards
            onDone = { runCatching { passFocus.requestFocus() } },
            modifier = Modifier.focusRequester(userFocus),
        )
        StageFieldRow(
            icon = OwnTVIcon.PENCIL,
            label = passLabel,
            value = ui.password,
            onValueChange = vm::setPassword,
            help = SettingHelp(passLabel, help),
            placeholder = stringResource(R.string.login_password_hint),
            password = true,
            keyboardType = KeyboardType.Password,
            // Done on the password keyboard signs in straight away — the usual TV flow.
            onDone = { runCatching { loginFocus.requestFocus() }; vm.login() },
            modifier = Modifier.focusRequester(passFocus),
        )
    }
}
