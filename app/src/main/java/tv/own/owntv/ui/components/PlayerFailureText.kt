package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.player.PlayerFailureReason

/** Resolves a semantic playback diagnosis only where it is rendered by Compose. */
@Composable
fun PlayerFailureReason.displayText(): String = stringResource(messageRes)
