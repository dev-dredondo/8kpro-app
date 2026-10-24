package tv.own.owntv.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat

@Composable
fun rememberLocalizedIntegerFormatter(grouping: Boolean = true): (Int) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, grouping) {
        NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = grouping }
    }
    return remember(formatter) { { value -> formatter.format(value) } }
}

@Composable
fun localizedInteger(value: Int, grouping: Boolean = true): String =
    rememberLocalizedIntegerFormatter(grouping)(value)

@Composable
fun rememberLocalizedDecimalFormatter(
    minimumFractionDigits: Int = 0,
    maximumFractionDigits: Int = 2,
): (Double) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, minimumFractionDigits, maximumFractionDigits) {
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            this.minimumFractionDigits = minimumFractionDigits
            this.maximumFractionDigits = maximumFractionDigits
        }
    }
    return remember(formatter) { { value -> formatter.format(value) } }
}

@Composable
fun localizedDecimal(
    value: Double,
    minimumFractionDigits: Int = 0,
    maximumFractionDigits: Int = 2,
): String = rememberLocalizedDecimalFormatter(minimumFractionDigits, maximumFractionDigits)(value)
