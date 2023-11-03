package com.x8bit.bitwarden.ui.platform.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.x8bit.bitwarden.ui.platform.base.util.Text

/**
 * Represents a clickable row of text and can contains an optional [content] that appears to the
 * right of the [text].
 *
 * @param text The label for the row as a [Text].
 * @param onClick The callback when the row is clicked.
 * @param modifier The modifier to be applied to the layout.
 * @param withDivider Indicates if a divider should be drawn on the bottom of the row, defaults
 * to `false`.
 * @param content The content of the [BitwardenTextRow].
 */
@Composable
fun BitwardenTextRow(
    text: Text,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    withDivider: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { },
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .weight(1f),
                text = text(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content?.invoke()
        }
        if (withDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}