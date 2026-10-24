package tv.own.owntv.features.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.ChNavLimits
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBinding
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.owntv.core.settings.RemoteShortcutPress
import tv.own.owntv.ui.components.NumberInputDialog
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private enum class ChNavDialog { NONE, ENABLED, UP_SKIP, DOWN_SKIP, CAPTURE, ACTION, RESET }

@Composable
fun ChNavSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.chNavEnabled.collectAsStateWithLifecycle()
    val upSkip by vm.chNavUpSkip.collectAsStateWithLifecycle()
    val downSkip by vm.chNavDownSkip.collectAsStateWithLifecycle()
    val bindings by vm.remoteShortcutBindings.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val upSkipFocus = remember { FocusRequester() }
    val downSkipFocus = remember { FocusRequester() }
    val bindingFocus = remember { mutableMapOf<Pair<Int, RemoteShortcutPress>, FocusRequester>() }
    var dialog by remember { mutableStateOf(ChNavDialog.NONE) }
    var pendingBinding by remember { mutableStateOf<RemoteShortcutBinding?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialog) {
        if (dialog != ChNavDialog.NONE) {
            savedScroll = scrollState.value
            return@LaunchedEffect
        }
        if (dialogReturn != null) restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }

    // The switch, the button assignments as rows, the two skip counts; how it works in the panel.
    val back = stringResource(R.string.common_back)
    val layout = stringResource(R.string.settings_group_layout)
    val pageHints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_change), back to layout)
    val about = stringResource(R.string.settings_remote_shortcuts_description)
    StageFullPage(
        parents = listOf(layout),
        title = stringResource(R.string.settings_remote_shortcuts),
        count = "",
        onBack = onBack,
        modifier = modifier,
        scroll = scrollState,
        rowsFocus = firstFocus,
    ) {
        val onTitle = stringResource(R.string.settings_remote_shortcuts_enabled)
        val onValue = SettingValue.Switch(enabled)
        val howItWorks = stringResource(R.string.settings_remote_shortcuts_help)
        StageSettingRow(
            icon = OwnTVIcon.CH_NAV,
            title = onTitle,
            desc = stringResource(R.string.settings_line_remote_shortcuts_on),
            value = onValue,
            onClick = { vm.setChNavEnabled(!enabled) },
            help = settingHelp(null, onTitle, stringResource(R.string.settings_remote_shortcuts_enabled_description), onValue, pinnable = false).copy(
                extra = {
                    Text(howItWorks, style = stageText(15, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 14.mpx))
                },
            ),
        )

        val sorted = bindings.sortedWith(compareBy<RemoteShortcutBinding> { it.keyCode }.thenBy { it.press.ordinal })
        StageSettingsHeading(stringResource(R.string.settings_remote_shortcuts_assignments), sorted.size)
        val separator = dotSeparator()
        sorted.forEach { binding ->
            val focus = remember(binding.keyCode, binding.press) { bindingFocus.getOrPut(binding.keyCode to binding.press) { FocusRequester() } }
            val pressLabel = stringResource(
                if (binding.press == RemoteShortcutPress.SHORT) R.string.settings_remote_shortcuts_short_press
                else R.string.settings_remote_shortcuts_long_press,
            )
            val title = remoteButtonLabel(binding.keyCode) + separator + pressLabel
            val value = SettingValue.Opens(null)
            StageSettingRow(
                icon = remoteButtonIcon(binding.keyCode, binding.action),
                title = title,
                desc = remoteActionLabel(binding.action),
                value = value,
                onClick = {
                    dialogReturn = focus
                    pendingBinding = binding
                    editingExisting = true
                    dialog = ChNavDialog.ACTION
                },
                modifier = Modifier.focusRequester(focus),
                help = SettingHelp(title, about, hints = pageHints),
            )
        }
        val addTitle = stringResource(R.string.settings_remote_shortcuts_add)
        val addDesc = stringResource(R.string.settings_remote_shortcuts_add_description)
        StageSettingRow(
            icon = OwnTVIcon.ADD,
            title = addTitle,
            desc = addDesc,
            value = SettingValue.Opens(null),
            onClick = { editingExisting = false; dialog = ChNavDialog.CAPTURE },
            help = SettingHelp(addTitle, addDesc, hints = settingHints(SettingValue.Opens(null), pinnable = false)),
        )
        val resetTitle = stringResource(R.string.settings_remote_shortcuts_reset)
        val resetDesc = stringResource(R.string.settings_remote_shortcuts_reset_description)
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = resetTitle,
            desc = resetDesc,
            value = null,
            onClick = { dialog = ChNavDialog.RESET },
            help = SettingHelp(resetTitle, resetDesc, hints = settingHints(null, pinnable = false)),
        )

        StageSettingsHeading(stringResource(R.string.settings_skip_counts), 2)
        val upTitle = stringResource(R.string.settings_ch_nav_up)
        val upDesc = stringResource(R.string.settings_ch_nav_up_description)
        val upValue = SettingValue.Opens(localizedInteger(upSkip, grouping = false))
        StageSettingRow(
            icon = OwnTVIcon.PAGE_TOWARD_FIRST,
            title = upTitle,
            desc = upDesc,
            value = upValue,
            onClick = { dialogReturn = upSkipFocus; dialog = ChNavDialog.UP_SKIP },
            modifier = Modifier.focusRequester(upSkipFocus),
            help = settingHelp(null, upTitle, upDesc, upValue, pinnable = false),
        )
        val downTitle = stringResource(R.string.settings_ch_nav_down)
        val downDesc = stringResource(R.string.settings_ch_nav_down_description)
        val downValue = SettingValue.Opens(localizedInteger(downSkip, grouping = false))
        StageSettingRow(
            icon = OwnTVIcon.PAGE_TOWARD_LAST,
            title = downTitle,
            desc = downDesc,
            value = downValue,
            onClick = { dialogReturn = downSkipFocus; dialog = ChNavDialog.DOWN_SKIP },
            modifier = Modifier.focusRequester(downSkipFocus),
            help = settingHelp(null, downTitle, downDesc, downValue, pinnable = false),
        )
    }

    val warnText = stringResource(R.string.settings_large_skips_warning)
    when (dialog) {
        ChNavDialog.ENABLED -> PickerDialog(
            title = stringResource(R.string.settings_remote_shortcuts_enabled),
            options = listOf("true" to stringResource(R.string.common_on), "false" to stringResource(R.string.common_off)),
            selected = enabled.toString(),
            onSelect = { value -> vm.setChNavEnabled(value.toBoolean()); dialog = ChNavDialog.NONE },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.UP_SKIP -> NumberInputDialog(
            title = stringResource(R.string.settings_ch_nav_up),
            value = upSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = vm::setChNavUpSkip,
            onReset = { vm.setChNavUpSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.DOWN_SKIP -> NumberInputDialog(
            title = stringResource(R.string.settings_ch_nav_down),
            value = downSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = vm::setChNavDownSkip,
            onReset = { vm.setChNavDownSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.CAPTURE -> RemoteButtonCapturePopup(
            onCaptured = { keyCode, press ->
                pendingBinding = RemoteShortcutBinding(keyCode, press, RemoteShortcutAction.OPEN_HOME)
                dialog = ChNavDialog.ACTION
            },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.ACTION -> pendingBinding?.let { binding ->
            val actionOptions = RemoteShortcutAction.entries.map { it.name to remoteActionLabel(it) }
            PickerDialog(
                title = stringResource(R.string.settings_remote_shortcuts_choose_action),
                options = if (editingExisting) listOf(String() to stringResource(R.string.common_delete)) + actionOptions else actionOptions,
                selected = binding.action.name,
                searchable = true,
                leadingIcons = buildMap {
                    if (editingExisting) put(String(), OwnTVIcon.CLOSE)
                    RemoteShortcutAction.entries.forEach { put(it.name, remoteActionIcon(it)) }
                },
                onSelect = { value ->
                    if (value.isEmpty()) vm.removeRemoteShortcutBinding(binding.keyCode, binding.press)
                    else vm.setRemoteShortcutBinding(binding.copy(action = RemoteShortcutAction.valueOf(value)))
                    pendingBinding = null
                    dialog = ChNavDialog.NONE
                },
                onDismiss = { pendingBinding = null; dialog = ChNavDialog.NONE },
            )
        }
        ChNavDialog.RESET -> PickerDialog(
            title = stringResource(R.string.settings_remote_shortcuts_reset),
            options = listOf(
                "true" to stringResource(R.string.common_reset),
                "false" to stringResource(R.string.common_cancel),
            ),
            selected = "false",
            onSelect = { value ->
                if (value.toBoolean()) vm.resetRemoteShortcutBindings()
                dialog = ChNavDialog.NONE
            },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.NONE -> Unit
    }
}

@Composable
private fun RemoteButtonCapturePopup(
    onCaptured: (Int, RemoteShortcutPress) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var activeKey by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }
    var pressedAt by remember { mutableStateOf(0L) }
    BackHandler { onDismiss() }

    // A Stage popup that takes the next key press; the panel catches it before the focused button does.
    LaunchedEffect(focus) {
        withFrameNanos { }
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
    }
    val captureKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        if (RemoteShortcutBindings.isProtectedKey(keyCode)) {
            false
        } else {
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (activeKey == AndroidKeyEvent.KEYCODE_UNKNOWN) {
                        activeKey = keyCode
                        pressedAt = System.currentTimeMillis()
                    }
                    activeKey == keyCode
                }
                KeyEventType.KeyUp -> {
                    if (activeKey != keyCode) false
                    else {
                        val press = if (System.currentTimeMillis() - pressedAt >= CAPTURE_LONG_PRESS_MS) {
                            RemoteShortcutPress.LONG
                        } else RemoteShortcutPress.SHORT
                        activeKey = AndroidKeyEvent.KEYCODE_UNKNOWN
                        onCaptured(keyCode, press)
                        true
                    }
                }
                else -> activeKey == keyCode
            }
        }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_remote_shortcuts_capture_title),
        body = stringResource(R.string.settings_remote_shortcuts_capture_description),
        width = 760.mpx,
        modifier = Modifier.onPreviewKeyEvent(captureKeyEvent),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(focus))
        },
    )
}

@Composable
private fun remoteButtonLabel(keyCode: Int): String = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_0, grouping = false)
    keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0, grouping = false)
    else -> when (keyCode) {
    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> stringResource(R.string.settings_remote_button_channel_up)
    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> stringResource(R.string.settings_remote_button_channel_down)
    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> stringResource(R.string.settings_remote_button_rewind)
    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> stringResource(R.string.settings_remote_button_fast_forward)
    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> stringResource(R.string.settings_remote_button_previous)
    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> stringResource(R.string.settings_remote_button_next)
    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> stringResource(R.string.settings_remote_button_last_channel)
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
    -> stringResource(R.string.settings_remote_action_play_pause)
    AndroidKeyEvent.KEYCODE_GUIDE -> stringResource(R.string.common_nav_guide)
    AndroidKeyEvent.KEYCODE_INFO -> stringResource(R.string.player_tool_info)
    AndroidKeyEvent.KEYCODE_MENU -> stringResource(R.string.settings_remote_button_menu)
    AndroidKeyEvent.KEYCODE_CAPTIONS -> stringResource(R.string.player_tool_subtitles)
    AndroidKeyEvent.KEYCODE_PROG_RED -> stringResource(R.string.settings_remote_button_red)
    AndroidKeyEvent.KEYCODE_PROG_GREEN -> stringResource(R.string.settings_remote_button_green)
    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> stringResource(R.string.settings_remote_button_yellow)
    AndroidKeyEvent.KEYCODE_PROG_BLUE -> stringResource(R.string.settings_remote_button_blue)
        else -> stringResource(R.string.settings_remote_shortcuts_unknown_button, keyCode)
    }
}

private fun remoteButtonIcon(keyCode: Int, fallbackAction: RemoteShortcutAction): OwnTVIcon = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ||
        keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> OwnTVIcon.MORE
    else -> when (keyCode) {
        AndroidKeyEvent.KEYCODE_CHANNEL_UP -> OwnTVIcon.REMOTE_CHANNEL_UP
        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> OwnTVIcon.REMOTE_CHANNEL_DOWN
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> OwnTVIcon.REWIND
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> OwnTVIcon.FORWARD
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> OwnTVIcon.SKIP_PREVIOUS
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> OwnTVIcon.SKIP_NEXT
        AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> OwnTVIcon.HISTORY
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        -> OwnTVIcon.PLAY
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> OwnTVIcon.PAUSE
        AndroidKeyEvent.KEYCODE_GUIDE -> OwnTVIcon.EPG
        AndroidKeyEvent.KEYCODE_INFO -> OwnTVIcon.INFO
        AndroidKeyEvent.KEYCODE_MENU -> OwnTVIcon.MENU
        AndroidKeyEvent.KEYCODE_CAPTIONS -> OwnTVIcon.SUBTITLE
        AndroidKeyEvent.KEYCODE_PROG_RED,
        AndroidKeyEvent.KEYCODE_PROG_GREEN,
        AndroidKeyEvent.KEYCODE_PROG_YELLOW,
        AndroidKeyEvent.KEYCODE_PROG_BLUE,
        -> OwnTVIcon.LIVE_DOT
        else -> remoteActionIcon(fallbackAction)
    }
}

@Composable
private fun remoteActionLabel(action: RemoteShortcutAction): String = stringResource(
    when (action) {
        RemoteShortcutAction.OPEN_HOME -> R.string.common_nav_home
        RemoteShortcutAction.OPEN_LIVE_TV -> R.string.common_nav_live_tv
        RemoteShortcutAction.OPEN_MOVIES -> R.string.common_nav_movies
        RemoteShortcutAction.OPEN_SERIES -> R.string.common_nav_series
        RemoteShortcutAction.OPEN_DOWNLOADS -> R.string.common_nav_downloads
        RemoteShortcutAction.OPEN_GUIDE -> R.string.common_nav_guide
        RemoteShortcutAction.OPEN_SEARCH -> R.string.common_nav_search
        RemoteShortcutAction.OPEN_SETTINGS -> R.string.common_nav_settings
        RemoteShortcutAction.OPEN_PROFILE_SWITCHER -> R.string.profiles_title
        RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> R.string.settings_playlists
        RemoteShortcutAction.CONTINUE_LAST_WATCHED -> R.string.settings_remote_action_continue
        RemoteShortcutAction.FOCUS_NOW_PLAYING -> R.string.settings_remote_action_focus_now_playing
        RemoteShortcutAction.EXPAND_NOW_PLAYING -> R.string.settings_remote_action_expand_now_playing
        RemoteShortcutAction.ENTER_MINI_PLAYER -> R.string.settings_remote_action_mini_player
        RemoteShortcutAction.ENTER_AUDIO_MODE -> R.string.player_tool_audio_only
        RemoteShortcutAction.PLAY_PAUSE -> R.string.settings_remote_action_play_pause
        RemoteShortcutAction.PAGE_TOWARD_FIRST -> R.string.settings_remote_action_page_first
        RemoteShortcutAction.PAGE_TOWARD_LAST -> R.string.settings_remote_action_page_last
        RemoteShortcutAction.JUMP_TO_FIRST -> R.string.settings_remote_action_jump_first
        RemoteShortcutAction.JUMP_TO_LAST -> R.string.settings_remote_action_jump_last
        RemoteShortcutAction.RETURN_TO_LIVE -> R.string.player_go_live
        RemoteShortcutAction.PREVIOUS_CHANNEL -> R.string.player_previous_channel
        RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> R.string.player_tool_subtitles
        RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> R.string.player_tool_audio
        RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> R.string.player_tool_aspect
        RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> R.string.player_tool_info
    },
)

private fun remoteActionIcon(action: RemoteShortcutAction): OwnTVIcon = when (action) {
    RemoteShortcutAction.OPEN_HOME -> OwnTVIcon.HOME
    RemoteShortcutAction.OPEN_LIVE_TV, RemoteShortcutAction.RETURN_TO_LIVE -> OwnTVIcon.LIVE_TV
    RemoteShortcutAction.OPEN_MOVIES -> OwnTVIcon.MOVIES
    RemoteShortcutAction.OPEN_SERIES -> OwnTVIcon.SERIES
    RemoteShortcutAction.OPEN_DOWNLOADS -> OwnTVIcon.DOWNLOADS
    RemoteShortcutAction.OPEN_GUIDE -> OwnTVIcon.EPG
    RemoteShortcutAction.OPEN_SEARCH -> OwnTVIcon.SEARCH
    RemoteShortcutAction.OPEN_SETTINGS -> OwnTVIcon.SETTINGS
    RemoteShortcutAction.OPEN_PROFILE_SWITCHER -> OwnTVIcon.PERSON
    RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> OwnTVIcon.PLAYLIST
    RemoteShortcutAction.CONTINUE_LAST_WATCHED, RemoteShortcutAction.PLAY_PAUSE -> OwnTVIcon.PLAY
    RemoteShortcutAction.FOCUS_NOW_PLAYING -> OwnTVIcon.FOCUS_HIGHLIGHT
    RemoteShortcutAction.EXPAND_NOW_PLAYING -> OwnTVIcon.EXPAND
    RemoteShortcutAction.ENTER_MINI_PLAYER -> OwnTVIcon.PIP
    RemoteShortcutAction.ENTER_AUDIO_MODE, RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> OwnTVIcon.AUDIO
    RemoteShortcutAction.PAGE_TOWARD_FIRST, RemoteShortcutAction.JUMP_TO_FIRST -> OwnTVIcon.SKIP_PREVIOUS
    RemoteShortcutAction.PAGE_TOWARD_LAST, RemoteShortcutAction.JUMP_TO_LAST -> OwnTVIcon.SKIP_NEXT
    RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> OwnTVIcon.SUBTITLE
    RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> OwnTVIcon.ASPECT
    RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> OwnTVIcon.INFO
    RemoteShortcutAction.PREVIOUS_CHANNEL -> OwnTVIcon.HISTORY
}

private const val CAPTURE_LONG_PRESS_MS = 600L
