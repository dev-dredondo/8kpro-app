package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.widthIn
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import androidx.compose.ui.focus.focusProperties
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx
import androidx.compose.ui.focus.onFocusChanged
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.resyncProgressPercent
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.ui.components.breakdownText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.warningText
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.core.setup.detailLines
import tv.own.owntv.core.setup.headline
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.features.setup.playlistAutoRefreshLabel
import tv.own.owntv.features.setup.AddSourceChooserScreen
import tv.own.owntv.features.setup.AddSourceScreen
import tv.own.owntv.features.setup.RemoteSetupScreen
import tv.own.owntv.ui.components.OwnTVSpinner

/** List / add / re-sync / delete the active profile's IPTV sources. */
@Composable
fun ManageSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val playlistAutoRefresh by vm.playlistAutoRefresh.collectAsStateWithLifecycle()
    val defaultId by vm.defaultSourceId.collectAsStateWithLifecycle()
    val sourceExpiry by vm.sourceExpiry.collectAsStateWithLifecycle()
    val sourceTest by vm.sourceTest.collectAsStateWithLifecycle()
    val deletingIds by vm.deletingSourceIds.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)

    // 8K PRO: showAdd / editingSource are never set any more — adding or editing a server is gone, so
    // the branches that open AddSourceScreen below are unreachable and kept only to keep the diff small.
    var showAdd by remember { mutableStateOf(false) }
    val sessionStore: tv.own.owntv.features.login.SessionStore = org.koin.compose.koinInject()
    val sessionState by sessionStore.state.collectAsStateWithLifecycle(tv.own.owntv.features.login.SessionState.Loading)
    val session = (sessionState as? tv.own.owntv.features.login.SessionState.LoggedIn)?.session
    var confirmLogout by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Within "Add source": null = the Remote|Manual chooser, else the chosen path.
    var addMode by remember { mutableStateOf<AddMode?>(null) }
    var editingSource by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    var resyncChoice by remember { mutableStateOf<SourceEntity?>(null) }
    // Set while the "this will stop playback and take a while" confirmation is on screen.
    var confirmRetest by remember { mutableStateOf<SourceEntity?>(null) }
    val addFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    // Per-row focus restore (mirrors MoviesScreen): track the row the user is acting on so, when
    // edit/re-sync/delete closes, focus lands back inside the list — on the same row if it survived,
    // else the nearest neighbour that slid into its slot, else the first row, else "Add Source".
    var contextId by remember { mutableStateOf<Long?>(null) }
    var contextIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

    // Whenever the list view is showing (no add form / edit form / delete dialog on top), restore
    // focus inside the list — not on "Add Source" as before, which is what pushed focus out of the
    // menu. contextId/contextFocus decide the specific row; firstRowFocus is the empty-list fallback.
    LaunchedEffect(showAdd, editingSource, confirmDelete) {
        if (showAdd || editingSource != null || confirmDelete != null) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val targetId = contextId
        if (targetId != null && sources.any { it.id == targetId }) {
            runCatching { contextFocus.requestFocus() }
        } else if (sources.isNotEmpty()) {
            runCatching { firstRowFocus.requestFocus() }
        } else {
            runCatching { addFocus.requestFocus() }
        }
    }

    // When the row a delete landed on disappears from `sources`, move focus to the nearest surviving
    // neighbour (same index slot, else new last row, else first row) instead of letting focus escape
    // outside the menu.
    LaunchedEffect(sources) {
        val targetId = contextId ?: return@LaunchedEffect
        if (sources.any { it.id == targetId }) return@LaunchedEffect
        withFrameNanos { }
        if (sources.isEmpty()) {
            contextId = null; contextIndex = -1
            runCatching { addFocus.requestFocus() }
            return@LaunchedEffect
        }
        val neighbor = sources.getOrNull(contextIndex.coerceAtLeast(0)) ?: sources.last()
        contextId = neighbor.id
        contextIndex = sources.indexOfFirst { it.id == neighbor.id }
        withFrameNanos { }
        runCatching { contextFocus.requestFocus() }
    }
    // Leaving "Add source" always returns to the Remote|Manual chooser next time (and drops any
    // running Remote listener), so a prior choice never skips the chooser.
    LaunchedEffect(showAdd) { if (!showAdd) { addMode = null; vm.stopRemoteListener() } }
    // A failed import/re-sync swaps the form for an error screen — move focus onto its action button.
    LaunchedEffect(importState) {
        if (importState is SettingsViewModel.ImportState.Failed) {
            kotlinx.coroutines.delay(50); runCatching { errorFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            showAdd -> { showAdd = false; addMode = null; vm.stopRemoteListener(); vm.cancelImport() }
            editingSource != null -> editingSource = null
            else -> onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (editingSource != null) {
            val src = editingSource!!
            AddSourceScreen(
                initial = src,
                initialAutoRefresh = playlistAutoRefresh[src.id] ?: PlaylistRefresh.OFF,
                initialIsDefault = src.id == defaultId,
                onStartXtream = { n, server, u, p, ua, ref, epg, autoRefresh, live, movies, series, isDefault, preferHls ->
                    vm.updateSource(
                        src.id, n, server, u, p, ua, epg, autoRefresh, isDefault, httpReferer = ref,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        preferHls = preferHls,
                    )
                    editingSource = null
                },
                onStartM3u = { n, url, ua, ref, epg, autoRefresh, isDefault -> vm.updateSource(src.id, n, url, "", "", ua, epg, autoRefresh, isDefault, httpReferer = ref); editingSource = null },
                onStartStalker = { n, url, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, autoRefresh, isDefault, live, movies, series ->
                    vm.updateSource(
                        src.id, n, url, "", "", ua, "", autoRefresh, isDefault, mac = mac, httpReferer = ref,
                        stalkerSerialNumber = serialNumber, stalkerDeviceId = deviceId,
                        stalkerDeviceId2 = deviceId2, stalkerSignature = signature,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                    )
                    editingSource = null
                },
                onBack = { editingSource = null },
                modifier = Modifier,
            )
        } else if (showAdd) {
            when (val s = importState) {
                SettingsViewModel.ImportState.Idle -> when (addMode) {
                    null -> AddSourceChooserScreen(
                        onRemote = { addMode = AddMode.REMOTE },
                        onManual = { addMode = AddMode.MANUAL },
                        onBack = { showAdd = false },
                        modifier = Modifier,
                    )
                    AddMode.REMOTE -> RemoteSetupScreen(
                        state = vm.remoteState.collectAsStateWithLifecycle().value,
                        payloads = vm.remotePayloads,
                        onStartListener = { port -> vm.startRemoteListener(port) },
                        onStopListener = { vm.stopRemoteListener() },
                        // A remote submission hands off to the pre-filled Manual form.
                        onPayloadReceived = { addMode = AddMode.MANUAL },
                        onBack = { vm.stopRemoteListener(); addMode = null },
                        modifier = Modifier,
                    )
                    AddMode.MANUAL -> AddSourceScreen(
                        onStartXtream = { n, server, u, p, ua, ref, epg, autoRefresh, live, movies, series, isDefault, preferHls ->
                            vm.addXtream(n.ifBlank { defaultIptvName }, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault, preferHls, httpReferer = ref)
                        },
                        onStartM3u = { n, url, ua, ref, epg, autoRefresh, isDefault -> vm.addM3u(n.ifBlank { defaultPlaylistName }, url, ua, epg, autoRefresh, isDefault, httpReferer = ref) },
                        onStartStalker = { n, url, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, autoRefresh, isDefault, live, movies, series ->
                            vm.addStalker(
                                n.ifBlank { defaultPortalName }, url, mac, serialNumber, deviceId,
                                deviceId2, signature, ua, autoRefresh, isDefault, live, movies, series, httpReferer = ref,
                            )
                        },
                        // Submissions from the Remote screen land here pre-filled (type + fields).
                        remotePayload = vm.remotePayload,
                        onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                        // A newly-added playlist can be made default only when others already exist.
                        showDefaultToggle = sources.isNotEmpty(),
                        onBack = { addMode = null },
                        modifier = Modifier,
                        initial = vm.lastFailedSource, // pre-fill on retry — no re-typing after a typo
                    )
                }
                SettingsViewModel.ImportState.Running -> CenterStatus {
                    val display = progress?.importProgressDisplay()
                    OwnTVSpinner(sizeDp = 56)
                    Spacer(Modifier.height(24.mpx))
                    Text(
                        stringResource(R.string.settings_sources_importing),
                        style = stageText(38, 800),
                        color = StageColors.Text,
                    )
                    Spacer(Modifier.height(9.mpx))
                    Text(display?.primaryText() ?: stringResource(R.string.settings_sources_preparing), style = stageText(30, 800), color = stageAccent.accent)
                    Spacer(Modifier.height(6.mpx))
                    Text(display?.detailText() ?: stringResource(R.string.settings_sources_preparing), style = stageText(18, 400), color = StageColors.Muted)
                    Spacer(Modifier.height(30.mpx))
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = { showAdd = false; vm.cancelImport() })
                }
                is SettingsViewModel.ImportState.Success -> {
                    // Semi-auto EPG: ask → sync (with a live count, like the import) → done, before returning.
                    if (epgSync !is EpgSyncUi.Hidden) {
                        EpgSyncDialog(state = epgSync, onSync = vm::syncPendingEpg, onDismiss = vm::dismissPendingEpg)
                    } else if (s.warnings.isNotEmpty() || s.remainder.hasAny) {
                        CenterStatus {
                            Text(stringResource(R.string.settings_sources_import_title), style = stageText(38, 800), color = StageColors.Text)
                            Spacer(Modifier.height(12.mpx))
                            Text(s.counts.summaryText(), style = stageText(18, 400), color = StageColors.Muted)
                            s.warnings.warningText()?.let { warning ->
                                Spacer(Modifier.height(6.mpx))
                                Text(warning, style = stageText(18, 400), color = StageColors.Muted)
                            }
                            s.remainder.remainderText()?.let { remainder ->
                                Spacer(Modifier.height(6.mpx))
                                Text(remainder, style = stageText(18, 400), color = StageColors.Muted)
                            }
                            Spacer(Modifier.height(30.mpx))
                            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = { showAdd = false; vm.resetImport() }, tinted = true)
                        }
                    } else {
                        LaunchedEffect(Unit) { showAdd = false; vm.resetImport() }
                    }
                }
                is SettingsViewModel.ImportState.Failed -> CenterStatus {
                    Text(stringResource(R.string.settings_sources_import_failed), style = stageText(38, 800), color = StageColors.Text)
                    Spacer(Modifier.height(12.mpx))
                    Text(s.failure.displayText(), style = stageText(18, 400), color = StageColors.Muted)
                    Spacer(Modifier.height(30.mpx))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                        tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_back), onClick = { showAdd = false; vm.resetImport() })
                        tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_sources_try_again), onClick = { vm.resetImport() }, modifier = Modifier.focusRequester(errorFocus), tinted = true)
                    }
                }
            }
        } else {
            // The playlists as rows; the focused one's Edit / Info / Re-sync / Delete in the panel.
            val actionsFocus = remember { FocusRequester() }
            StageFullPage(
                parents = listOf(stringResource(R.string.settings_group_sources)),
                title = stringResource(R.string.settings_playlists),
                count = "",
                onBack = onBack,
                handleBack = false,
                // 8K PRO: no "Add a playlist" (the server is fixed); the account and Sign out instead.
                toolbar = {
                    session?.let { current ->
                        Text(
                            stringResource(R.string.login_account, current.username),
                            style = stageText(17, 600), color = StageColors.Muted,
                            modifier = Modifier.padding(end = 14.mpx),
                        )
                    }
                    tv.own.owntv.ui.stage.StageTool(
                        stringResource(R.string.login_logout), onClick = { confirmLogout = true },
                        icon = tv.own.owntv.ui.components.OwnTVIcon.POWER, boxed = true,
                        modifier = Modifier.focusRequester(addFocus),
                    )
                },
            ) {
                if (sources.isEmpty()) StageSettingsNote(stringResource(R.string.settings_sources_empty), null)
                sources.forEachIndexed { index, source ->
                    val isDefault = source.id == defaultId
                    val counts by remember(source.id) { vm.contentCounts(source.id) }.collectAsStateWithLifecycle(null)
                    val syncState by remember(source.id) { vm.syncState(source.id) }.collectAsStateWithLifecycle(CatalogSyncState.Idle)
                    val rowFocus = when {
                        source.id == contextId -> contextFocus
                        index == 0 -> firstRowFocus
                        else -> null
                    }
                    SourceRow(
                        source = source,
                        autoRefresh = playlistAutoRefresh[source.id] ?: PlaylistRefresh.OFF,
                        isDefault = isDefault,
                        expiry = sourceExpiry[source.id],
                        counts = counts,
                        syncState = syncState,
                        isDeleting = source.id in deletingIds,
                        rowFocus = rowFocus,
                        actionsFocus = actionsFocus,
                        keepPanel = source.id == contextId,
                        onFocused = { contextId = source.id; contextIndex = index },
                        onEdit = { contextId = source.id; contextIndex = index; editingSource = source },
                        onTest = { contextId = source.id; contextIndex = index; vm.testSource(source) },
                        onResync = { contextId = source.id; contextIndex = index; resyncChoice = source },
                        onCancelSync = { contextId = source.id; contextIndex = index; vm.cancelResync(source) },
                        onDelete = { contextId = source.id; contextIndex = index; confirmDelete = source },
                    )
                }
            }
        }

        resyncChoice?.let { src ->
            ResyncChoiceDialog(
                sourceName = src.name,
                onNormal = { vm.resync(src); resyncChoice = null },
                onClean = { vm.resync(src, clean = true); resyncChoice = null },
                onDismiss = { resyncChoice = null },
            )
        }

        sourceTest?.let { state ->
            SourceTestDialog(
                state = state,
                onDismiss = { vm.dismissSourceTest() },
                // Only for a playlist that exists; the measurement is stored against its row.
                onRetest = sources.firstOrNull { it.name == state.sourceName }?.let { src ->
                    { confirmRetest = src }
                },
                onSkip = { vm.skipConnectionMeasurement() },
            )
        }

        // Measuring opens real streams, so the user is told plainly that playback stops and that it
        // is slow, and gets to say no. Same shape as the delete confirmation beneath it.
        confirmRetest?.let { src ->
            ConfirmDialog(
                title = stringResource(R.string.settings_sources_probe_title),
                message = stringResource(R.string.settings_sources_probe_warning),
                onConfirm = { confirmRetest = null; vm.retestSource(src) },
                onDismiss = { confirmRetest = null },
                confirmLabel = R.string.settings_sources_retest,
            )
        }

        if (confirmLogout) {
            ConfirmDialog(
                title = stringResource(R.string.login_logout),
                message = stringResource(R.string.login_logout_message),
                onConfirm = { confirmLogout = false; scope.launch { sessionStore.clear() } },
                onDismiss = { confirmLogout = false },
                confirmLabel = R.string.login_logout,
            )
        }

        confirmDelete?.let { src ->
            ConfirmDialog(
                title = stringResource(R.string.settings_sources_delete_title, src.name),
                message = stringResource(R.string.settings_sources_delete_message),
                onConfirm = { vm.delete(src); confirmDelete = null },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceEntity,
    autoRefresh: PlaylistRefresh,
    isDefault: Boolean,
    expiry: String?,
    counts: SyncCounts?,
    syncState: CatalogSyncState,
    isDeleting: Boolean,
    rowFocus: FocusRequester?,
    actionsFocus: FocusRequester,
    keepPanel: Boolean,
    onFocused: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val activeSync = syncState as? CatalogSyncState.Syncing
    val activeCounts = activeSync?.countsLabel(source.type, counts)
    val typeName = stringResource(
        when (source.type) {
            SourceType.XTREAM -> R.string.settings_sources_type_xtream
            SourceType.M3U -> R.string.settings_sources_type_m3u
            SourceType.STALKER -> R.string.settings_sources_type_stalker
            SourceType.LOCAL_BACKUP -> R.string.settings_sources_backup
        },
        // 8K PRO: show the account, never the server address.
        source.username.orEmpty(),
    )
    val visibleCounts = if (activeSync == null) counts?.breakdownText() else activeCounts?.displayText()
    val sep = stringResource(R.string.settings_sources_details_separator)
    // The row's line: counts, refresh and expiry; the URL itself goes to the panel.
    val line = buildList {
        if (!visibleCounts.isNullOrBlank()) add(visibleCounts)
        else if (activeSync != null) add(stringResource(R.string.settings_sources_preparing_detail))
        if (autoRefresh.mode != PlaylistAutoRefresh.OFF) add(stringResource(R.string.settings_sources_auto_refresh, playlistAutoRefreshLabel(autoRefresh)))
        if (!expiry.isNullOrBlank()) add(stringResource(R.string.settings_sources_expiry, expiry))
    }.joinToString(sep)
    val status = when {
        isDeleting -> stringResource(R.string.settings_sources_deleting)
        activeSync != null -> resyncProgressPercent(activeSync.baseItemCount, activeSync.totalProcessed)
            ?.let { stringResource(R.string.sync_progress_percent, it) } ?: stringResource(R.string.sync_progress_syncing)
        isDefault -> stringResource(R.string.settings_sources_default)
        else -> null
    }
    val back = rowFocus ?: remember { FocusRequester() }
    val actions = if (isDeleting) emptyList() else listOf(
        // "Info", not "Test": the expensive measurement lives behind Re-test inside the popup.
        StageAction(OwnTVIcon.INFO, stringResource(R.string.settings_sources_info), onTest),
        StageAction(
            OwnTVIcon.REFRESH,
            stringResource(if (syncState.isActive) R.string.settings_sources_cancel else R.string.settings_sources_resync),
            if (syncState.isActive) onCancelSync else onResync,
        ),
    )
    StageSettingRow(
        icon = OwnTVIcon.PLAYLIST,
        title = source.name,
        desc = line.ifBlank { typeName },
        value = status?.let { SettingValue.Action(it) },
        onClick = { runCatching { actionsFocus.requestFocus() } },
        keepPanel = keepPanel,
        help = SettingHelp(
            title = source.name,
            // The address on one line, the counts on the next.
            text = listOfNotNull(typeName, visibleCounts).joinToString("\n"),
            hints = listOf(
                "▶" to stringResource(R.string.settings_key_actions),
                stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
            ),
            extra = {
                if (isDeleting) {
                    Text(
                        stringResource(R.string.settings_sources_removing_detail),
                        style = tv.own.owntv.ui.theme.stageText(16, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
                        modifier = Modifier.padding(top = 14.mpx),
                    )
                } else {
                    StageActionColumn(actions, back, actionsFocus)
                }
            },
        ),
        modifier = Modifier
            .focusRequester(back)
            .focusProperties { right = actionsFocus }
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

private fun CatalogSyncState.Syncing.countsLabel(sourceType: SourceType, stored: SyncCounts?): tv.own.owntv.core.sync.SyncProgressCounts? {
    fun visibleCount(active: Boolean, processed: Int, storedCount: Int): Int =
        if (active) processed else storedCount

    val live = visibleCount(liveActive, liveProcessed, stored?.channels ?: 0)
    val movies = visibleCount(moviesActive, moviesProcessed, stored?.movies ?: 0)
    val series = visibleCount(seriesActive, seriesProcessed, stored?.series ?: 0)
    val counts = when (sourceType) {
        SourceType.M3U -> SyncProgressCounts(
            live = live,
            movies = 0,
            series = 0,
            liveActive = true,
            moviesActive = false,
            seriesActive = false,
        )
        SourceType.XTREAM -> SyncProgressCounts(
            live = live,
            movies = movies,
            series = series,
            liveActive = liveActive || live > 0,
            moviesActive = moviesActive || movies > 0,
            seriesActive = seriesActive || series > 0,
        )
        SourceType.LOCAL_BACKUP -> SyncProgressCounts(
            live = 0,
            movies = 0,
            series = 0,
            liveActive = false,
            moviesActive = false,
            seriesActive = false,
        )
        // Stalker: LIVE + VOD/series all populate.
        SourceType.STALKER -> SyncProgressCounts(
            live = live,
            movies = movies,
            series = series,
            liveActive = liveActive || live > 0,
            moviesActive = moviesActive || movies > 0,
            seriesActive = seriesActive || series > 0,
        )
    }
    return counts.takeIf { it.hasItems }
}

@Composable
private fun CenterStatus(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.padding(top = tv.own.owntv.features.shell.components.StageContentTop).fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 1000.mpx).stageGlass(30.mpx).padding(horizontal = 60.mpx, vertical = 48.mpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * What the confirming button says. Defaults to Delete because every caller was a deletion when
     * this was written — which is exactly how the connection-measurement confirmation ended up
     * offering "Delete", a word with nothing to do with what it would have done.
     */
    @StringRes confirmLabel: Int = R.string.common_delete,
) {
    // Focus starts on Cancel: a mis-press must not delete anything.
    tv.own.owntv.ui.stage.StageConfirm(
        title = title, body = message, confirm = stringResource(confirmLabel),
        onConfirm = onConfirm, onCancel = onDismiss, focusCancel = true,
    )
}

/**
 * Resync picker: an ordinary refresh, or one that is also allowed to drop titles the provider has
 * stopped listing.
 *
 * A normal resync will not remove more than half a source's rows, because a truncated provider
 * response is indistinguishable from a genuinely shrunken catalog and guessing wrong wipes a working
 * playlist. The consequence is that a provider that really did drop a lot of titles leaves them
 * behind with no way out — this dialog is that way out, kept behind a deliberate choice.
 *
 * Focus starts on the ordinary refresh, so the common case is still Select-then-Select and a
 * mis-press can't trigger removals. Back cancels.
 */
@Composable
private fun ResyncChoiceDialog(
    sourceName: String,
    onNormal: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_resync_title_full, sourceName),
        body = stringResource(R.string.settings_sources_resync_description),
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_now_full), onClick = onNormal, modifier = Modifier.focusRequester(focus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.REFRESH) },
        )
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_remove_full), onClick = onClean, danger = true,
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.TRASH, tv.own.owntv.ui.theme.StageColors.Danger) },
        )
    }
}

/** How the user chose to add a source: fill it from another device (Remote) or type it here (Manual). */
private enum class AddMode { REMOTE, MANUAL }

/**
 * Result of the row's "Test" button: is the server reachable, is the subscription still good, and how
 * many of the account's connections are in use right now.
 *
 * The provider's own status word is shown verbatim rather than translated — panels invent their own
 * vocabulary there, and a wrong translation of "Banned" would be worse than the English original.
 */
@Composable
internal fun SourceTestDialog(
    state: SourceTestUi,
    onDismiss: () -> Unit,
    /** Null hides Re-test — the add form has no saved playlist to measure yet. */
    onRetest: (() -> Unit)? = null,
    /** Abandon a measurement in progress. Falls back to simply closing when not supplied. */
    onSkip: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    // Keyed on *which* button carries the requester, not on first composition: while measuring it is
    // Skip, then OK replaces it — a one-shot request left focus on a node that no longer existed, so
    // the finished dialog could not be dismissed. A frame is waited for so the replacement exists.
    val measuring = state is SourceTestUi.Measuring
    LaunchedEffect(measuring) {
        withFrameNanos { }
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_test_title),
        body = state.sourceName,
        buttons = {
            // Only once the quick check has finished: a measurement on top of a running request would
            // race it for the same connection.
            if (onRetest != null && state is SourceTestUi.Done) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_sources_retest), onClick = onRetest, height = 56.mpx, textSize = 19)
            }
            // While measuring, the only honest button abandons it: OK would suggest the answer is in.
            if (state is SourceTestUi.Measuring) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_sources_probe_skip), onClick = onSkip ?: onDismiss, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
            } else {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_ok), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
            }
        },
    ) {
        val line = tv.own.owntv.ui.theme.stageText(18, 500)
        when (state) {
            is SourceTestUi.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(16.mpx))
                Text(stringResource(R.string.setup_testing), style = line, color = tv.own.owntv.ui.theme.StageColors.Muted)
            }
            // Slow by nature, so it says which stream it is on rather than looking like a hang.
            is SourceTestUi.Measuring -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(16.mpx))
                Text(stringResource(R.string.settings_sources_probe_running, state.progress.stream, state.progress.maxStreams), style = line, color = tv.own.owntv.ui.theme.StageColors.Muted)
            }
            is SourceTestUi.Done -> SourceTestReport(state.result, state.limit)
        }
    }
}

@Composable
private fun SourceTestReport(result: SourceTestResult, limit: tv.own.owntv.core.live.ConnectionLimit?) {
    val res = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
        Text(
            result.headline(res), style = tv.own.owntv.ui.theme.stageText(20, 700),
            color = if (result is SourceTestResult.Ok) tv.own.owntv.ui.theme.StageColors.Ok else tv.own.owntv.ui.theme.StageColors.Danger,
        )
        result.detailLines(res, limit).forEach {
            Text(it, style = tv.own.owntv.ui.theme.stageText(17, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
        }
    }
}
