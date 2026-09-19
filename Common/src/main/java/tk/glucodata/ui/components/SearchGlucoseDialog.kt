package tk.glucodata.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit

@Composable
fun SearchGlucoseDialog(
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onExecuteSearch: (under: Float, above: Float, label: Int, keyword: String) -> Unit
) {
    var underText by remember { mutableStateOf("") }
    var aboveText by remember { mutableStateOf("") }
    var keywordText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(-1) } // -1: Any, 0: Bolus, 1: Carbs, 2: Basal, 3: BG

    val defaultLow = if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"
    val defaultHigh = if (unit == GlucoseUnit.MMOL_L) "10.0" else "180"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.closename),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Find readings outside target or search logged events:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Glucose Range Thresholds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = underText,
                        onValueChange = { underText = it },
                        label = { Text("Below", fontSize = 12.sp) },
                        suffix = { Text(unit.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline) },
                        placeholder = { Text(defaultLow, fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = aboveText,
                        onValueChange = { aboveText = it },
                        label = { Text("Above", fontSize = 12.sp) },
                        suffix = { Text(unit.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline) },
                        placeholder = { Text(defaultHigh, fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick preset threshold pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = underText == defaultLow,
                        onClick = {
                            underText = if (underText == defaultLow) "" else defaultLow
                            if (underText.isNotEmpty()) aboveText = ""
                        },
                        label = { Text("Lows (< $defaultLow)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = aboveText == defaultHigh,
                        onClick = {
                            aboveText = if (aboveText == defaultHigh) "" else defaultHigh
                            if (aboveText.isNotEmpty()) underText = ""
                        },
                        label = { Text("Highs (> $defaultHigh)", fontSize = 11.sp) }
                    )
                }

                // Category filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Event Category:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == -1,
                            onClick = { selectedCategory = -1 },
                            label = { Text("All", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCategory == 0,
                            onClick = { selectedCategory = 0 },
                            label = { Text("Bolus", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCategory == 1,
                            onClick = { selectedCategory = 1 },
                            label = { Text("Carbs", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCategory == 2,
                            onClick = { selectedCategory = 2 },
                            label = { Text("Basal", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCategory == 3,
                            onClick = { selectedCategory = 3 },
                            label = { Text("BG", fontSize = 11.sp) }
                        )
                    }
                }

                // Keyword or food search
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("Notes or food name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Pizza, Coffee, Exercise", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val rawUnder = underText.replace(',', '.').toFloatOrNull() ?: 0f
                            val rawAbove = aboveText.replace(',', '.').toFloatOrNull() ?: 0f
                            val under = if (unit == GlucoseUnit.MMOL_L && rawUnder > 0f) GlucoseUnit.MMOL_L.toMgDl(rawUnder) else rawUnder
                            val above = if (unit == GlucoseUnit.MMOL_L && rawAbove > 0f) GlucoseUnit.MMOL_L.toMgDl(rawAbove) else rawAbove

                            onExecuteSearch(under, above, selectedCategory, keywordText.trim())
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.search))
                    }
                }
            }
        }
    }
}
