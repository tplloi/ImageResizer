package ru.tech.imageresizershrinker.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.tech.imageresizershrinker.theme.blend
import ru.tech.imageresizershrinker.utils.modifier.block

@Composable
fun PreferenceRowSwitch(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(
        alpha = 0.2f
    ),
    onClick: (Boolean) -> Unit
) {
    PreferenceRow(
        modifier = modifier,
        title = title,
        color = color,
        subtitle = subtitle,
        onClick = { onClick(!checked) },
        endContent = {
            val thumbIcon: (@Composable () -> Unit)? = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            } else null
            Switch(
                thumbContent = thumbIcon,
                colors = SwitchDefaults.colors(
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline.blend(
                        MaterialTheme.colorScheme.secondaryContainer, 0.3f
                    ),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline.blend(
                        MaterialTheme.colorScheme.secondaryContainer, 0.2f
                    ),
                    uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                checked = checked,
                onCheckedChange = {
                    onClick(it)
                }
            )
        }
    )
}

@Composable
fun PreferenceRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(
        alpha = 0.2f
    ),
    applyHorPadding: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    startContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null,
    titleFontStyle: TextStyle = LocalTextStyle.current.copy(lineHeight = 18.sp),
    onClick: (() -> Unit)?
) {
    val contentColor =
        if (color == MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)) contentColorFor(
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
        ) else contentColorFor(backgroundColor = color)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .then(
                    if(applyHorPadding) {
                        Modifier.padding(horizontal = 16.dp)
                    } else Modifier
                )
                .clip(RoundedCornerShape(16.dp))
                .then(
                    onClick?.let {
                        Modifier.clickable { onClick() }
                    } ?: Modifier
                )
                .block(color = color)
                .padding(horizontal = if (startContent != null) 0.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            startContent?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, maxLines = maxLines, style = titleFontStyle)
                Spacer(modifier = Modifier.height(2.dp))
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 14.sp,
                        color = LocalContentColor.current.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            endContent?.invoke()
        }
    }
}