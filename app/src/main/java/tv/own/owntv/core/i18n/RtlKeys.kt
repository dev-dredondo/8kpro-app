package tv.own.owntv.core.i18n

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection

enum class HorizontalDirection {
    START,
    END,
}

fun Key.horizontalDirection(
    layoutDirection: LayoutDirection,
): HorizontalDirection? =
    when (this) {
        Key.DirectionLeft ->
            if (layoutDirection == LayoutDirection.Ltr) {
                HorizontalDirection.START
            } else {
                HorizontalDirection.END
            }

        Key.DirectionRight ->
            if (layoutDirection == LayoutDirection.Ltr) {
                HorizontalDirection.END
            } else {
                HorizontalDirection.START
            }

        else -> null
    }
