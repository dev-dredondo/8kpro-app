package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** The number columns of a league table: P W D L GD Pts, each 51 wide. */
private val NumberColumn = 51.mpx

/** `# Team P W D L GD Pts`, 16.5 px dim. */
@Composable
internal fun StandingsHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 6.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
        Text(stringResource(R.string.sports_table_team), style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        listOf(
            R.string.sports_table_played, R.string.sports_table_won, R.string.sports_table_drawn,
            R.string.sports_table_lost, R.string.sports_table_difference, R.string.sports_table_points,
        ).forEach { Text(stringResource(it), style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn)) }
    }
}

/** One table row; [highlight] (a side of the open match, a followed team) on accent 14%. */
@Composable
internal fun StandingsLine(row: StandingsRow, highlight: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlight) Modifier.background(stageAccent.accent.copy(alpha = 0.14f), RoundedCornerShape(10.mpx)) else Modifier)
            .padding(horizontal = 12.mpx, vertical = 7.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.rank.toString(), style = stageText(19, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            Crest(row.team, 30)
            Text(row.team.shortName, style = stageText(19, if (highlight) 800 else 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listOf(row.played.toString(), row.won.toString(), row.drawn.toString(), row.lost.toString(), row.difference, row.points.toString()).forEachIndexed { i, v ->
            Text(v, style = stageText(19, if (i == 5) 800 else 500), color = if (i == 5) StageColors.Text else StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn))
        }
    }
}
