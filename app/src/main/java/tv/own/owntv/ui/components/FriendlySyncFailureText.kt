package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.util.FriendlySyncFailure

/**
 * Resolve a classified sync failure only at the Compose presentation boundary; the sentences are core's.
 *
 * 8K PRO: an unclassified failure is shown as its raw exception text, which for network errors carries
 * the request URL ("unexpected end of stream on http://host/xmltv.php?…"). One that names the built-in
 * server is shown as the generic sync error instead, so the server's address never reaches the screen.
 */
@Composable
fun FriendlySyncFailure.displayText(): String = brandSafe().displayText(LocalContext.current.resources)

fun FriendlySyncFailure.brandSafe(): FriendlySyncFailure =
    if (this is FriendlySyncFailure.Unknown && tv.own.owntv.brand.BrandConfig.mentionsServer(rawMessage)) {
        FriendlySyncFailure.Generic
    } else {
        this
    }
