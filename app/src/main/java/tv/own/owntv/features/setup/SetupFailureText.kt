package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.ui.components.brandSafe

/**
 * Words a semantic onboarding failure at the Compose boundary; the sentences are core's.
 * 8K PRO: a sync failure naming the built-in server is worded generically (see [brandSafe]).
 */
@Composable
fun SourceImporter.SetupFailure.displayText(): String {
    val safe = if (this is SourceImporter.SetupFailure.Sync) SourceImporter.SetupFailure.Sync(failure.brandSafe()) else this
    val text = safe.displayText(LocalContext.current.resources)
    // Any other wording that still carries the address (a future failure type) falls back to the generic line.
    return if (tv.own.owntv.brand.BrandConfig.mentionsServer(text)) {
        tv.own.owntv.core.util.FriendlySyncFailure.Generic.displayText(LocalContext.current.resources)
    } else {
        text
    }
}
