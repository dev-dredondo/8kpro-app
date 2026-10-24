package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.core.settings.PanelShares

/**
 * The saved panel shares for [section], or null when that section is left at Default (or has never
 * been saved) — in which case the browse screen keeps its stock `Dimens` / `weight()` layout, so the
 * feature can't affect anyone who never opens it.
 */
@Composable
fun rememberPanelShares(section: PanelSection, vm: SettingsViewModel): PanelShares? {
    val enabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
    val shares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
    return shares.takeIf { enabled }
}
