package tv.own.owntv.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import tv.own.owntv.R
import tv.own.owntv.core.content.AdultCategoryClassifier

/**
 * Maps a channel / VOD category name to a coarse "genre" with a colour, used as a small dot/badge
 * in the category rail, the Live preview metadata row, and the Guide channel rows so a user can
 * spot content type at a glance.
 *
 * The inference is keyword-based on the (lower-cased) category name and intentionally lenient —
 * it's a visual hint, not authoritative metadata. Anything unmatched falls back to [OTHER] (grey),
 * which is drawn as a neutral dot so every row still has a consistent marker.
 *
 * The keyword lists are deliberately MULTILINGUAL. Real IPTV playlists label categories in many
 * languages (EN/DE/FR/ES/IT/PT/NL/PL/Nordic/TR/AR/GR/AL …) and with provider prefixes
 * ("AR| BEIN ENTERTAINMENT", "DE - KINDER FILME", "|EN| SCIENCE FICTION"). The keywords below were
 * derived from a real ~3k-category sample so common non-English labels also classify instead of
 * silently falling to OTHER.
 */
enum class ChannelGenre(@param:StringRes val displayLabelRes: Int, val canonicalLabel: String, val dot: Color) {
    SPORT(R.string.content_genre_sport, "Sport", Color(0xFF4CAF50)),
    NEWS(R.string.content_genre_news, "News", Color(0xFFEF5350)),
    MOVIES(R.string.content_genre_movies, "Movies", Color(0xFFAB47BC)),
    SERIES(R.string.content_genre_series, "Series", Color(0xFF3D5AFE)),
    KIDS(R.string.content_genre_kids, "Kids", Color(0xFFFFB300)),
    MUSIC(R.string.content_genre_music, "Music", Color(0xFF42A5F5)),
    DOCUMENTARY(R.string.content_genre_documentary, "Documentary", Color(0xFF26A69A)),
    ANIME(R.string.content_genre_anime, "Anime", Color(0xFFD500F9)),
    ENTERTAINMENT(R.string.content_genre_entertainment, "Entertainment", Color(0xFF7C4DFF)),
    RELIGIOUS(R.string.content_genre_religious, "Religious", Color(0xFF8D6E63)),
    ADULT(R.string.content_genre_adult, "Adult", Color(0xFF78909C)),
    // Film/series sub-genres — mainly for Movies & Series category rails (VOD categories like
    // "Action", "Comedy", "Drama"). Distinct hues spaced around the wheel from the channel genres.
    ACTION(R.string.content_genre_action, "Action", Color(0xFFFF7043)),
    COMEDY(R.string.content_genre_comedy, "Comedy", Color(0xFFFFEE58)),
    DRAMA(R.string.content_genre_drama, "Drama", Color(0xFF5C6BC0)),
    HORROR(R.string.content_genre_horror, "Horror", Color(0xFFB71C1C)),
    SCIFI(R.string.content_genre_scifi, "Sci-Fi", Color(0xFF00BCD4)),
    ROMANCE(R.string.content_genre_romance, "Romance", Color(0xFFEC407A)),
    OTHER(R.string.content_genre_other, "Other", Color(0xFF9E9E9E));

    companion object {
        /**
         * Match by keyword. Order matters — the first genre in [ORDER] whose keyword list matches
         * wins, so more-specific genres are checked before generic buckets (e.g. "Action Movies" →
         * Action, "Anime Series" → Anime, "Kids Movies" → Kids). Returns [OTHER] when the name is
         * blank or nothing matches.
         */
    fun fromCategory(categoryName: String?): ChannelGenre {
        val n = categoryName?.lowercase()?.trim().orEmpty()
        if (n.isEmpty()) return OTHER
        if (AdultCategoryClassifier.isAdult(categoryName)) return ADULT
        for (g in ORDER) {
                if (keywords(g).any { n.contains(it) }) return g
            }
            return OTHER
        }

        /**
         * The dot colour for a category, or null when the category doesn't match a known genre.
         * For Guide-style usage where no dot is preferable to a grey "other" dot.
         */
        fun dotFor(categoryName: String?): Color? = fromCategory(categoryName).let {
            if (it == OTHER) null else it.dot
        }

        // Precedence: specific/high-signal first, generic scripted-content buckets (MOVIES/SERIES)
        // last so a "NETFLIX ACTION" reads as Action and only a bare "NETFLIX MOVIES" reads as Movies.
    private val ORDER = listOf(
        SPORT, NEWS, RELIGIOUS, ANIME, KIDS, MUSIC, DOCUMENTARY,
            SCIFI, HORROR, ROMANCE, COMEDY, ACTION, DRAMA,
            ENTERTAINMENT, MOVIES, SERIES,
        )

        private fun keywords(g: ChannelGenre): List<String> = when (g) {
            // "adults" (not bare "adult", so "adult swim" animation still reads as Anime); رعب-free.
            ADULT -> listOf(
                "for adult", "adults", "rated r", "rated-r", "+18", "18+", "xxx", "للكبار", "adult movies",
            )
            SPORT -> listOf(
                "sport", "desporto", "deportes", "sportowe", "sportski", "αθλητ", "رياض", "spor ",
                "football", "soccer", "fussball", "futbol", "fútbol", "calcio", "voetbal", "piłka",
                "cricket", "espn", "golf", "tennis", "basket", "baseball", "rugby", "handball",
                "racing", "formula 1", "formula1", "f1", "motogp", "moto gp", "wrc", "nascar", "cyclisme",
                "fight", "ufc", "wwe", "boxing", "wrestling", "مصارعة", "hockey", "liiga",
                "nba", "nfl", "nhl", "mlb", "afl", "nrl", "bundesliga", "ligue 1", "ligue1",
                "laliga", "la liga", "serie a", "champions league", "uefa", "dazn", "bein sport",
                "eurosport", "euro sport", "sky sport", "tsn", "sportsnet", "ppv", "world cup",
                "worldcup", "mundial", "cdm", "fifa", "horse racing", "rikstoto", "martial",
                "art martiaux", "sztuki walki", "combat", "أحدث المباريات",
            )
            NEWS -> listOf("news", "lajme", "informacyjne", "الاخبار", "اخبار", "أخبار", "noticias", "nachrichten")
            RELIGIOUS -> listOf(
                "islamic", "islamique", "islam", "quran", "قرآن", "القرآن", "القران", "اسلام", "إسلام",
                "اسلامية", "إسلامية", "muslim", "مسلم", "christian", "christen", "christem", "biblical",
                "biblique", "biblia", "مسيحي", "مسيحية", "دينية", "ديني", "religious", "religieux",
                "religiös", "religios", "fetare", "naat", "nasheed", "qawwali", "ناشيد",
            )
            ANIME -> listOf(
                "anime", "animé", "anmi", "animi", "manga", "crunchyroll", "adult swim", "adult-swim",
                "انمي", "أنمي", "انيمي", "انیمه", "کارتون",
            )
            KIDS -> listOf(
                "kid", "child", "cartoon", "junior", "baby", "teen", "nick", "boomerang", "cbeebies",
                "family", "famili", "familia", "familie", "pixar", "dreamworks", "ghibli",
                "animation", "animac", "animaç", "animaz", "animacio", "animasyon", "animatie",
                "kinder", "enfant", "jeunesse", "bambini", "bimbi", "figli", "infantil", "niño", "nino",
                "niña", "nina", "criança", "crianca", "crianças", "dzieci", "bajki", "deti", "děti",
                "barn", "lapset", "børn", "femij", "fëmij", "çocuk", "cocuk", "krakkar", "kraka",
                "أطفال", "اطفال", "παιδ", "بچه", "کودک",
            )
            MUSIC -> listOf(
                "music", "musik", "musikk", "musique", "musica", "música", "muzyka", "muzyczn",
                "muziek", "muzicki", "muzik", "μουσικ", "موسيقى", "أغاني", "اغاني", "اغانى", "طرب",
                "concert", "koncert", "mtv", "vh1", "karaoke", "stingray", "spotify", "anghami", "singer",
            )
            DOCUMENTARY -> listOf(
                "documentary", "documentaries", "docu", "doku", "documentaire", "documentar",
                "dokumentar", "documental", "documentario", "documentacao", "documentação",
                "dokumentalne", "dokumentarni", "dokumentarne", "وثائق", "الوثائقية", "ντοκιμαντ",
                "nat geo", "national geo", "national geographic", "ناشيونال",
            )
            SCIFI -> listOf(
                "sci-fi", "sci fi", "scifi", "science fiction", "science-fiction", "ciencia ficcion",
                "ciencia ficción", "fantascienza", "fantasy", "fantastico", "fantastique",
                "fantastyczne", "fantasia", "fantasi", "katastroficzne",
            )
            HORROR -> listOf(
                "horror", "horreur", "horor", "terror", "رعب", "epouvante", "suspense", "thriller", "scary",
            )
            ROMANCE -> listOf(
                "romance", "romans", "romantic", "romantico", "romantik", "romantique", "romant",
                "رومانس", "رومانسي", "miłość", "milosc",
            )
            COMEDY -> listOf(
                "comedy", "comedie", "comédie", "commedia", "komedia", "komedie", "komedi", "humor",
                "humour", "kabaret", "sitcom", "stand-up", "standup", "stand up", "كوميدي",
            )
            ACTION -> listOf(
                "action", "aksja", "akcja", "akcija", "aksiyon", "azione", "aventura", "adventure",
                "aventure", "avventura", "przygod", "abenteuer", "war", "guerre", "guerra", "wojenne",
                "belico", "belic", "western", "mafia", "gangster", "gangsterskie", "crime", "crimen",
                "kryminał", "kryminal", "policier", "superhero", "marvel", "marvel/dc", "dc universe",
                "اكشن", "حرب", "أكشن",
            )
            DRAMA -> listOf(
                "drama", "drame", "dramma", "dramat", "drammatico", "دراما", "δραμα", "daily soap", "soap",
            )
            ENTERTAINMENT -> listOf(
                "entertainment", "unterhaltung", "vermaak", "intretenimento", "entretenimento",
                "reality", "télé-réalité", "tele-realite", "variety", "variete", "varieté", "varietes",
                "varietà", "zabava", "zábava", "podcast", "talk show", "game show", "tv show", "tv-show",
                "tv shows", "منوعات", "منوعه", "برامج", "spectacle", "مسرحيات", "مسرحية", "teatr",
                "theatre", "teatro",
            )
            MOVIES -> listOf(
                "movie", "film", "cinema", "cinéma", "cine", "kino", "pelicula", "película",
                "peliculas", "películas", "sinema", "bioscoop", "box office", "elokuva", "kvikmynd",
                "ταιν", "σινεμα", "κινηματογρ", "أفلام", "افلام", "فيلم", "pellicola", "bollywood",
                "hollywood",
            )
            SERIES -> listOf(
                "series", "serie", "serien", "serial", "seriale", "serija", "serije", "séries",
                "sarja", "sorozat", "dizi", "novela", "telenovela", "soap opera", "web serie",
                "مسلسلات", "سلسلة", "σειρ",
            )
            OTHER -> emptyList()
        }
    }
}
