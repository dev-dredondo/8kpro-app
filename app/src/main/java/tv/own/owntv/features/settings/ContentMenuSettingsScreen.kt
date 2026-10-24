package tv.own.owntv.features.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.pluralStringResource
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.menu.catalogue
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.trapAllFocusExit

@Composable
private fun menuTitle(menu: ContentMenu) = stringResource(
    when (menu) {
        ContentMenu.LIVE -> R.string.common_nav_live_tv
        ContentMenu.MOVIE -> R.string.common_nav_movies
        ContentMenu.SERIES -> R.string.common_nav_series
        ContentMenu.EPISODE -> R.string.content_episodes
    },
)

private fun menuIcon(menu: ContentMenu) = when (menu) {
    ContentMenu.LIVE -> OwnTVIcon.LIVE_TV
    ContentMenu.MOVIE -> OwnTVIcon.MOVIES
    ContentMenu.SERIES -> OwnTVIcon.SERIES
    ContentMenu.EPISODE -> OwnTVIcon.LIST_GRID
}

/**
 * Long-press menus — the order of the actions in each of the four content menus.
 *
 * Four rows, one per menu. Opening one shows that menu's actions in the Move overlay, where holding
 * OK picks an action up and Up/Down carries it. Nothing is written until Save, so Cancel and Back
 * always leave the menu exactly as it was. "Close" is not listed because it is pinned to the bottom
 * of every menu, outside the arrangeable part.
 */
@Composable
fun ContentMenuSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val scrollState = rememberScrollState()
    val rowFocus = remember { ContentMenu.entries.associateWith { FocusRequester() } }
    var openMenu by remember { mutableStateOf<ContentMenu?>(null) }
    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(ContentMenu.LIVE).requestFocus() } }
    // Four menu rows and Reset; the panel lists the focused menu's actions in their order.
    val about = stringResource(R.string.settings_content_menus_description)
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_layout)),
        title = stringResource(R.string.settings_content_menus_title),
        count = pluralStringResource(R.plurals.settings_setting_count, ContentMenu.entries.size, ContentMenu.entries.size),
        onBack = onBack,
        modifier = modifier,
        scroll = scrollState,
    ) {
        ContentMenu.entries.forEach { menu ->
            val saved by remember(menu) { vm.menuOrder(menu) }.collectAsStateWithLifecycle(emptyList())
            val refs = catalogue(menu)
            val order = applyMenuOrder(refs.map { it.key }, saved) { it }
            val labels = order.mapNotNull { k -> refs.firstOrNull { it.key == k }?.labelRes }
            val title = menuTitle(menu)
            val value = SettingValue.Opens(stringResource(if (saved.isEmpty()) R.string.settings_subtitle_default else R.string.settings_live_latency_custom))
            StageSettingRow(
                icon = menuIcon(menu),
                title = title,
                desc = null,
                value = value,
                onClick = { openMenu = menu },
                help = SettingHelp(
                    title, about,
                    hints = settingHints(value, pinnable = false),
                    extra = {
                        Column(Modifier.padding(top = 16.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                            labels.forEachIndexed { i, res ->
                                Row(Modifier.height(36.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                                    Text((i + 1).toString(), style = tv.own.owntv.ui.theme.stageText(17, 700), color = tv.own.owntv.ui.theme.StageColors.Dim, modifier = Modifier.width(22.mpx))
                                    Text(stringResource(res), style = tv.own.owntv.ui.theme.stageText(17, 500), color = tv.own.owntv.ui.theme.StageColors.Text)
                                }
                            }
                        }
                    },
                ),
                modifier = Modifier.focusRequester(rowFocus.getValue(menu)),
            )
        }
        val reset = stringResource(R.string.common_reset)
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = reset,
            desc = null,
            value = null,
            onClick = { ContentMenu.entries.forEach { vm.setMenuOrder(it, emptyList()) } },
            help = SettingHelp(reset, about, hints = settingHints(null, pinnable = false)),
        )
        // Arranged in the page's panel.
        openMenu?.let { menu ->
            ArrangeMenuOverlay(
                menu = menu,
                onSave = { keys -> vm.setMenuOrder(menu, keys); openMenu = null },
                onCancel = { openMenu = null },
            )
        }
    }

}

/**
 * One menu's actions in the Move overlay — the same panel channels are reordered with, except
 * the item being carried is chosen here rather than handed in: hold OK on an action to pick it up,
 * Up/Down to carry it, OK to put it down.
 */
@Composable
private fun ArrangeMenuOverlay(
    menu: ContentMenu,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: SettingsViewModel = koinViewModel()
    val refs = catalogue(menu)
    // Wait for DataStore's first real value before seeding. An eager empty placeholder would make
    // every reopened editor show the shipped order even when a custom order is already saved.
    var keys by remember(menu) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(menu) {
        val saved = vm.menuOrder(menu).first()
        keys = applyMenuOrder(refs.map { it.key }, saved) { it }
    }
    // -1 = nothing picked up; otherwise the index Up/Down carries.
    var picked by remember(menu) { mutableIntStateOf(-1) }
    val pickedFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(keys.isEmpty()) { if (keys.isNotEmpty()) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
    // The carried action is a different composable at its new index, so focus has to follow it.
    LaunchedEffect(picked, keys) { if (picked >= 0) runCatching { pickedFocus.requestFocus() } }
    fun move(delta: Int) {
        val to = picked + delta
        if (picked < 0 || to !in keys.indices) return
        keys = keys.toMutableList().apply { add(to, removeAt(picked)) }
        picked = to
    }

    val list: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Column(
            // The rows scroll inside the panel; Save and Cancel stay under them, on screen.
            Modifier.weight(1f, fill = false).trapAllFocusExit().focusGroup()
                // Only while an action is picked up: Up/Down carry it instead of moving focus.
                .onPreviewKeyEvent { event ->
                    if (picked < 0 || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { move(-1); true }
                        Key.DirectionDown -> { move(1); true }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(2.mpx),
        ) {
            Text(stringResource(R.string.settings_content_menus_hint), style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(bottom = 10.mpx))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
            keys.forEachIndexed { index, key ->
                val ref = refs.first { it.key == key }
                ArrangeMenuRow(
                    number = index + 1,
                    label = stringResource(ref.labelRes),
                    picked = picked == index,
                    onPick = { picked = if (picked == index) -1 else index },
                    modifier = when {
                        picked == index -> Modifier.focusRequester(pickedFocus)
                        picked < 0 && index == 0 -> Modifier.focusRequester(firstFocus)
                        else -> Modifier
                    },
                )
            }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End)) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onCancel, height = 52.mpx, textSize = 18)
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_save), onClick = { onSave(keys) }, height = 52.mpx, textSize = 18, tinted = true)
            }
        }
    }
    if (panelEditor(onCancel) { list() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onCancel, title = menuTitle(menu), scroll = false) { list() }
}

/** One action in the arrange overlay: accent-filled with a move sign while it is picked up. */
@Composable
private fun ArrangeMenuRow(
    number: Int,
    label: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A held OK raises the long press first and the plain click when the key is finally released,
    // which would pick the action up and immediately put it back down.
    var longAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val a = tv.own.owntv.ui.theme.stageAccent
    tv.own.owntv.ui.stage.StageSurface(
        onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) onPick() },
        onLongClick = { longAt = android.os.SystemClock.uptimeMillis(); onPick() },
        radius = 14.mpx,
        focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
        // The action being carried stays lit, focused or not.
        idle = if (picked) Modifier.background(a.accent.copy(alpha = 0.22f), RoundedCornerShape(14.mpx)) else Modifier,
        modifier = modifier.fillMaxWidth().height(50.mpx),
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text(if (picked) stringResource(R.string.setup_move_indicator) else number.toString(), style = tv.own.owntv.ui.theme.stageText(17, 700), color = if (picked) a.accent else tv.own.owntv.ui.theme.StageColors.Dim, modifier = Modifier.width(26.mpx))
            Text(label, style = tv.own.owntv.ui.theme.stageText(18, 600), color = if (picked || focused) tv.own.owntv.ui.theme.StageColors.Text else tv.own.owntv.ui.theme.StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
