package com.x8bit.bitwarden.ui.platform.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * A Composable function that displays a row item.
 *
 * @param label The primary text label to display for the item.
 * @param supportingLabel An secondary text label to display beneath the label.
 * @param startIcon The [Painter] object used to draw the icon at the start of the item.
 * @param onClick The lambda to be invoked when the item is clicked.
 * @param modifier An optional [Modifier] for this Composable, defaulting to an empty Modifier.
 * This allows the caller to specify things like padding, size, etc.
 * @param selectionDataList A list of all the selection items to be displayed in the overflow
 * dialog.
 */
@Suppress("LongMethod")
@Composable
fun BitwardenListItem(
    label: String,
    supportingLabel: String,
    startIcon: Painter,
    onClick: () -> Unit,
    selectionDataList: List<SelectionItemData>,
    modifier: Modifier = Modifier,
) {
    var shouldShowDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 72.dp)
            .padding(vertical = 8.dp)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = startIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = supportingLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = { shouldShowDialog = true },
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_horizontal),
                contentDescription = stringResource(id = R.string.options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }

    if (shouldShowDialog) {
        BitwardenSelectionDialog(
            title = label,
            onDismissRequest = { shouldShowDialog = false },
            selectionItems = {
                selectionDataList.forEach {
                    BitwardenBasicDialogRow(
                        text = it.text,
                        onClick = {
                            shouldShowDialog = false
                            it.onClick()
                        },
                    )
                }
            },
        )
    }
}

/**
 * Wrapper for the an individual selection item's data.
 */
data class SelectionItemData(
    val text: String,
    val onClick: () -> Unit,
)

@Preview(showBackground = true)
@Composable
private fun BitwardenListItem_preview() {
    BitwardenTheme {
        BitwardenListItem(
            label = "Sample Label",
            supportingLabel = "Jan 3, 2024, 10:35 AM",
            startIcon = painterResource(id = R.drawable.ic_send_text),
            onClick = {},
            selectionDataList = emptyList(),
        )
    }
}