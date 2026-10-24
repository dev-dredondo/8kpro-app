package tv.own.owntv.core.i18n

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontTheme
import tv.own.owntv.core.theme.ThemeMode

/** Debug-only, static screen for checking system glyph fallback on representative TV devices. */
class FontFallbackQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwnTVTheme(
                themeMode = ThemeMode.DARK,
                accent = AccentColor.TEAL,
                systemInDarkTheme = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    QaSection("Normal OwnTV typography")
                    PopupFontTheme { QaSection("PopupFontTheme at 1f") }
                    PopupFontTheme(fontScale = 0.75f) {
                        QaSection("Compact PopupFontTheme at 0.75f")
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QaSection(heading: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge)
        Text("Arabic — العربية: أهلاً بكم في تلفازكم")
        Text("Simplified Chinese — 简体中文：正在播放电视节目")
        Text("Traditional Chinese — 繁體中文：正在播放電視節目")
        Text("Japanese — 日本語：テレビ番組を再生中")
        Text("Korean — 한국어: 텔레비전 프로그램 재생 중")
        Text("Malayalam — മലയാളം: OwnTV-ലേക്ക് സ്വാഗതം")
        Text("Hindi — हिन्दी: OwnTV में आपका स्वागत है")
        Text("Bangla — বাংলা: OwnTV-তে স্বাগতম")
        Text("Cyrillic — Русский: Сейчас в эфире")
        Text("Greek — Ελληνικά: Τώρα παίζει")
        Text("Mixed Latin/Arabic — OwnTV • الحلقة 12 • HD")
        Text("Mixed Latin/CJK — OwnTV • 第12話 • 4K")
        Text("Provider title — الأخبار الدولية / 世界ニュース")
        Text("Provider subtitle — الحلقة الجديدة / 新しいエピソード")
        Text("Channel — القناة الأولى / 総合テレビ")
        Text("Programme — رحلة عبر الزمن / 時間を旅する")
        Text("Playlist — قائمة العائلة / 家族のプレイリスト")
        Text("Subtitle file — العربية_日本語_한국어.srt")
    }
}
