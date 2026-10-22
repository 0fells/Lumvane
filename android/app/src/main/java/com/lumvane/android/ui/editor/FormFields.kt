package com.lumvane.android.ui.editor

import com.lumvane.android.ui.components.AppCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lumvane.android.R
import com.lumvane.android.ui.components.DropdownField

@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = !collapsible,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp).then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (collapsible) {
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
            }
            if (expanded) content()
        }
    }
}

@Composable
fun TextF(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun IntF(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
    var text by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    TextF(
        label, text,
        onChange = { t ->
            val f = t.filter { it.isDigit() || it == '-' }
            text = f
            onChange(f.toIntOrNull() ?: 0)
        },
        modifier = modifier, keyboardType = KeyboardType.Number, supporting = supporting,
    )
}

@Composable
fun LongF(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(if (value == 0L) "" else value.toString()) }
    TextF(
        label, text,
        onChange = { t ->
            val f = t.filter { it.isDigit() || it == '-' }
            text = f
            onChange(f.toLongOrNull() ?: 0)
        },
        modifier = modifier, keyboardType = KeyboardType.Number,
    )
}

@Composable
fun SwitchF(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChoiceF(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    display: (String) -> String = { it },
) {
    val def = stringResource(R.string.value_default)
    DropdownField(
        label = label,
        value = value,
        options = options.map { it to (if (it.isEmpty()) def else display(it)) },
        onSelect = onChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ListF(
    label: String,
    value: List<String>,
    onChange: (List<String>) -> Unit,
    separator: Char = ',',
    minLines: Int = 1,
) {
    var text by remember { mutableStateOf(value.joinToString(if (separator == '\n') "\n" else ", ")) }
    TextF(
        label, text,
        onChange = { t ->
            text = t
            onChange(t.split(separator).map { it.trim() }.filter { it.isNotEmpty() })
        },
        singleLine = separator != '\n' && minLines <= 1,
        minLines = minLines,
    )
}

@Composable
fun TriStateF(label: String, unspecified: Boolean, enabled: Boolean, onChange: (unspecified: Boolean, enabled: Boolean) -> Unit) {
    val keep = stringResource(R.string.tri_keep_default)
    val on = stringResource(R.string.tri_on)
    val off = stringResource(R.string.tri_off)
    val current = if (unspecified) "keep" else if (enabled) "on" else "off"
    DropdownField(
        label = label,
        value = current,
        options = listOf("keep" to keep, "on" to on, "off" to off),
        onSelect = { v ->
            when (v) {
                "keep" -> onChange(true, false)
                "on" -> onChange(false, true)
                else -> onChange(false, false)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
