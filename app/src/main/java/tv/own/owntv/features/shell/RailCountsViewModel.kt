package tv.own.owntv.features.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** The rail's details lines (Size = Extra wide), as data; the shell turns them into text. */
data class RailDetails(
    val lastChannel: String? = null,
    val resumeMovie: String? = null,
    /** The last watched episode: series name, season and episode number, and whether it is part-watched. */
    val episode: RailEpisode? = null,
    val downloading: Int = 0,
    /** Bytes done over bytes expected across the running downloads, null while no size is known. */
    val downloadProgress: Float? = null,
)

data class RailEpisode(val series: String, val season: Int, val episode: Int, val resume: Boolean)

/**
 * The numbers beside Live TV, Movies, Series and Downloads in the open Stage rail, for the playlists
 * being shown ("All playlists" or the one picked), and the details lines under them. Every query
 * already exists on the DAOs; titles in adult categories stay hidden exactly as in the Continue pill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RailCountsViewModel(
    channelDao: ChannelDao,
    movieDao: MovieDao,
    seriesDao: SeriesDao,
    downloadDao: DownloadDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
    historyDao: HistoryDao,
    progressDao: ProgressDao,
    profileDao: ProfileDao,
    categoryDao: CategoryDao,
) : ViewModel() {

    val counts: StateFlow<Map<MainSection, Int>> = activeProfileSources(settings, sourceDao)
        .flatMapLatest { c ->
            if (c.profileId < 0) return@flatMapLatest flowOf(emptyMap())
            // An empty id list would make the IN clause meaningless; -1 matches nothing.
            fun ids(type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }
            combine(
                channelDao.countAll(ids(MediaType.LIVE)),
                movieDao.countAll(ids(MediaType.MOVIE)),
                seriesDao.countAll(ids(MediaType.SERIES)),
                downloadDao.count(c.profileId),
            ) { live, movies, series, downloads ->
                mapOf(
                    MainSection.LIVE_TV to live,
                    MainSection.MOVIES to movies,
                    MainSection.SERIES to series,
                    MainSection.DOWNLOADS to downloads,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val details: StateFlow<RailDetails> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) return@flatMapLatest flowOf(RailDetails())
            suspend fun allowed(categoryId: Long?) = AdultCategoryClassifier.allows(pid, categoryId, profileDao, categoryDao)
            val live = historyDao.observeMostRecentOfType(pid, MediaType.LIVE).map { h ->
                h?.let { channelDao.getById(it.itemId) }?.takeIf { allowed(it.categoryId) }?.name
            }
            val movie = historyDao.observeMostRecentOfType(pid, MediaType.MOVIE).map { h ->
                h?.let { movieDao.getById(it.itemId) }
                    ?.takeIf { allowed(it.categoryId) && (progressDao.get(pid, MediaType.MOVIE, it.id)?.positionMs ?: 0L) > 0 }
                    ?.name
            }
            val episode = historyDao.observeMostRecentOfType(pid, MediaType.EPISODE).map { h ->
                val ep = h?.let { seriesDao.getEpisodeById(it.itemId) } ?: return@map null
                val s = seriesDao.getSeriesById(ep.seriesId)?.takeIf { allowed(it.categoryId) } ?: return@map null
                val pos = progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L
                RailEpisode(s.name, ep.seasonNumber, ep.episodeNumber, resume = pos > 0)
            }
            val downloads = downloadDao.observeForProfile(pid).map { all -> all.filter { it.status == DownloadStatus.RUNNING } }
            combine(live, movie, episode, downloads) { l, m, e, running ->
                val total = running.sumOf { it.totalBytes }
                RailDetails(
                    lastChannel = l,
                    resumeMovie = m,
                    episode = e,
                    downloading = running.size,
                    downloadProgress = if (total > 0) running.sumOf { it.downloadedBytes }.toFloat() / total else null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RailDetails())
}
