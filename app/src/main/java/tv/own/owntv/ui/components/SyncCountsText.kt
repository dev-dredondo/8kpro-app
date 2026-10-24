package tv.own.owntv.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncProgressDisplay
import tv.own.owntv.core.sync.SyncWarning
import tv.own.owntv.core.sync.breakdownText
import tv.own.owntv.core.sync.compactCount
import tv.own.owntv.core.sync.detailText
import tv.own.owntv.core.sync.displayText
import tv.own.owntv.core.sync.labelText
import tv.own.owntv.core.sync.primaryText
import tv.own.owntv.core.sync.remainderText
import tv.own.owntv.core.sync.summaryText
import tv.own.owntv.core.sync.warningText

/**
 * Import counts at the Compose presentation boundary. The sentences themselves are core's, shared
 * with the mobile app; these are only the composable wrappers that reach for the resources.
 */

@Composable
fun SyncCounts.breakdownText(includeEpg: Boolean = false): String =
    breakdownText(LocalContext.current.resources, includeEpg)

@Composable
fun SyncCounts.summaryText(includeEpg: Boolean = false): String =
    summaryText(LocalContext.current.resources, includeEpg)

@Composable
fun SyncProgressCounts.displayText(): String = displayText(LocalContext.current.resources)

internal fun compactCount(context: Context, value: Int): String = compactCount(context.resources, value)

@Composable
fun SyncProgressDisplay.primaryText(): String = primaryText(LocalContext.current.resources)

@Composable
fun SyncProgressDisplay.detailText(): String = detailText(LocalContext.current.resources)

@Composable
fun SyncWarning.labelText(): String = labelText(LocalContext.current.resources)

@Composable
fun List<SyncWarning>.warningText(): String? = warningText(LocalContext.current.resources)

@Composable
fun SyncContentTypes.remainderText(): String? = remainderText(LocalContext.current.resources)
