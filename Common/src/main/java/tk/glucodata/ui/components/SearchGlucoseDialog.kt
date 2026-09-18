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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search Glucose & Logs", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Find readings outside target or search logged events:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Glucose Range Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = underText,
                        onValueChange = { underText = it },
                        label = { Text("Below (${unit.label})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = aboveText,
                        onValueChange = { aboveText = it },
                        label = { Text("Above (${unit.label})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Preset search buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = underText == (if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"),
                        onClick = {
                            underText = if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"
                            aboveText = ""
                        },
                        label = { Text("Find Lows", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = aboveText == (if (unit == GlucoseUnit.MMOL_L) "10.0" else "180"),
                        onClick = {
                            aboveText = if (unit == GlucoseUnit.MMOL_L) "10.0" else "180"
                            underText = ""
                        },
                        label = { Text("Find Highs", fontSize = 11.sp) }
                    )
                }

                // Category selector
                Text(
                    text = "Log Category:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == -1,
                        onClick = { selectedCategory = -1 },
                        label = { Text("Any", fontSize = 11.sp) }
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
                        selected = selectedCategory == 3,
                        onClick = { selectedCategory = 3 },
                        label = { Text("BG", fontSize = 11.sp) }
                    )
                }

                // Keyword / Note search
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("Keyword or food (e.g. Pizza, Coffee)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawUnder = underText.replace(',', '.').toFloatOrNull() ?: 0f
                    val rawAbove = aboveText.replace(',', '.').toFloatOrNull() ?: 0f
                    val under = if (unit == GlucoseUnit.MMOL_L && rawUnder > 0f) GlucoseUnit.MMOL_L.toMgDl(rawUnder) else rawUnder
                    val above = if (unit == GlucoseUnit.MMOL_L && rawAbove > 0f) GlucoseUnit.MMOL_L.toMgDl(rawAbove) else rawAbove

                    onExecuteSearch(under, above, selectedCategory, keywordText)
                    onDismiss()
                }
            ) {
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
