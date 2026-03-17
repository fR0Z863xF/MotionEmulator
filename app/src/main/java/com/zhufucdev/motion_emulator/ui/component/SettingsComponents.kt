package com.zhufucdev.motion_emulator.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhufucdev.motion_emulator.ui.theme.PaddingCommon
import com.zhufucdev.motion_emulator.ui.theme.PaddingSmall

@Composable
fun SettingsSectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    summary: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PaddingCommon, vertical = PaddingSmall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(description)
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            leadingContent = { Icon(icon, contentDescription = null) }
        )
    }
}

@Composable
fun SettingsGroupTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = PaddingCommon, vertical = PaddingSmall)
    )
}

@Composable
fun SettingsSingleChoiceGroup(
    title: String,
    options: List<SettingsChoiceOption>,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Column(modifier) {
        SettingsGroupTitle(title)
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PaddingCommon, vertical = PaddingSmall)
            )
        }
        options.forEachIndexed { index, option ->
            SettingsChoiceItem(option)
            if (index < options.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = PaddingCommon))
            }
        }
    }
}

data class SettingsChoiceOption(
    val title: String,
    val description: String? = null,
    val selected: Boolean,
    val onSelect: () -> Unit
)

@Composable
fun SettingsChoiceItem(option: SettingsChoiceOption) {
    ListItem(
        headlineContent = { Text(option.title) },
        supportingContent = {
            option.description?.let {
                Text(text = it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            RadioButton(selected = option.selected, onClick = option.onSelect)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = option.onSelect)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { description?.let { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun SettingsTextFieldItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    Column(modifier = modifier.padding(horizontal = PaddingCommon, vertical = PaddingSmall)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            enabled = enabled,
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsTextAction(
    title: String,
    actionLabel: String,
    onClick: () -> Unit,
    description: String? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { description?.let { Text(it) } },
        trailingContent = {
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    )
}