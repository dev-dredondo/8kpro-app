package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionFailure

@Composable
fun CompanionFailure.displayText(): String = when (this) {
    CompanionFailure.InvalidPort -> stringResource(R.string.setup_companion_invalid_port)
    is CompanionFailure.PortInUse -> stringResource(R.string.setup_companion_port_in_use, port)
    CompanionFailure.Unavailable -> stringResource(R.string.setup_companion_unavailable)
}

@Composable
fun companionLockedText(): String = stringResource(R.string.setup_companion_locked)
