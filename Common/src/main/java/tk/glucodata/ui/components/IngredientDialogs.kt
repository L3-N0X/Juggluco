package tk.glucodata.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.data.MealStore
import tk.glucodata.ui.model.FoodDetails
import tk.glucodata.ui.model.FoodHit
import tk.glucodata.ui.model.FoodNutrient
import tk.glucodata.ui.model.Ingredient
import tk.glucodata.ui.model.IngredientSaveError
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.theme.LocalLogbookColors
import java.util.Locale

/** "12 g carbs per slice", the way an ingredient is described in lists. */
@Composable
internal fun ingredientDescription(ingredient: Ingredient): String = stringResource(
    R.string.meal_carbs_per_unit,
    stringResource(R.string.log_value_carbs, plainAmount(ingredient.carbsPerUnit, 2)),
    ingredient.unit.ifBlank { stringResource(R.string.ingredient_unit_default) }
)

/** An amount followed by the ingredient's unit, like "120 g" or "2 slice". */
internal fun amountWithUnit(amount: Float, unit: String): String = "${plainAmount(amount, 1)} $unit".trim()

/** Keeps [this] within [maxBytes] of UTF-8 without cutting a character in half. */
internal fun String.fitUtf8(maxBytes: Int): String {
    var end = length
    while (end > 0 && substring(0, end).toByteArray(Charsets.UTF_8).size > maxBytes) end--
    if (end > 0 && end < length && this[end - 1].isHighSurrogate()) end--
    return substring(0, end)
}

/** Accepts what a decimal keyboard types, with either separator. */
internal fun decimalInput(typed: String): String = typed.filter { it.isDigit() || it == '.' || it == ',' }

internal fun parseDecimal(text: String): Float? = text.replace(',', '.').toFloatOrNull()

/**
 * Creates or edits an ingredient of the catalog. The food database can fill it in, and an
 * ingredient native allows to remove (the newest one, while no meal uses it) can be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientEditorDialog(
    ingredient: Ingredient?,
    onDismiss: () -> Unit,
    onSaved: (Int) -> Unit,
    onDeleted: () -> Unit = {},
    initialName: String = ""
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(ingredient?.name ?: initialName) }
    var unit by remember { mutableStateOf(ingredient?.unit ?: "g") }
    var carbsText by remember { mutableStateOf(ingredient?.carbsPerUnit?.let { plainAmount(it, 3) }.orEmpty()) }
    var error by remember { mutableStateOf<IngredientSaveError?>(null) }
    var units by remember { mutableStateOf<List<String>>(emptyList()) }
    var foodDatabase by remember { mutableStateOf(false) }
    var showFoodDatabase by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (loadedUnits, hasDatabase) = withContext(Dispatchers.IO) { MealStore.units() to MealStore.foodDatabaseAvailable }
        units = loadedUnits
        foodDatabase = hasDatabase
    }

    val carbs = parseDecimal(carbsText)
    val nameBytes = name.trim().toByteArray(Charsets.UTF_8).size
    val unitBytes = unit.trim().toByteArray(Charsets.UTF_8).size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (ingredient == null) R.string.ingredient_new else R.string.ingredient_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.ingredient_name)) },
                    singleLine = true,
                    isError = nameBytes > MealStore.MAX_NAME_BYTES || error == IngredientSaveError.EMPTY,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it; error = null },
                    label = { Text(stringResource(R.string.ingredient_unit)) },
                    placeholder = { Text(stringResource(R.string.ingredient_unit_hint)) },
                    singleLine = true,
                    isError = unitBytes > MealStore.MAX_UNIT_BYTES,
                    modifier = Modifier.fillMaxWidth()
                )
                if (units.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        units.forEach { option ->
                            FilterChip(
                                selected = option.equals(unit.trim(), ignoreCase = true),
                                onClick = { unit = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = decimalInput(it) },
                    label = { Text(stringResource(R.string.ingredient_carbs_per_unit)) },
                    suffix = { Text(stringResource(R.string.unit_carbs_short)) },
                    singleLine = true,
                    isError = carbsText.isNotEmpty() && carbs == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (foodDatabase) {
                    TextButton(onClick = { showFoodDatabase = true }) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.ingredient_lookup))
                    }
                }
                if (ingredient != null && !ingredient.deletable) {
                    Text(
                        text = stringResource(R.string.ingredient_delete_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let {
                    Text(
                        text = stringResource(ingredientErrorRes(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = carbs != null && carbs >= 0f,
                onClick = {
                    scope.launch {
                        val (saveError, index) = withContext(Dispatchers.IO) {
                            MealStore.saveIngredient(ingredient?.index ?: -1, name, unit, carbs ?: 0f)
                        }
                        if (saveError == null) onSaved(index) else error = saveError
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (ingredient?.deletable == true) {
                    TextButton(onClick = {
                        scope.launch {
                            if (withContext(Dispatchers.IO) { MealStore.deleteIngredient(ingredient.index) }) onDeleted()
                        }
                    }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    if (showFoodDatabase) {
        FoodDatabaseDialog(
            onDismiss = { showFoodDatabase = false },
            onPick = { food ->
                showFoodDatabase = false
                name = food.name.fitUtf8(MealStore.MAX_NAME_BYTES)
                unit = food.unit.fitUtf8(MealStore.MAX_UNIT_BYTES)
                carbsText = plainAmount(food.carbsPerGram, 3)
                error = null
            }
        )
    }
}

private fun ingredientErrorRes(error: IngredientSaveError): Int = when (error) {
    IngredientSaveError.EMPTY -> R.string.ingredient_error_empty
    IngredientSaveError.NAME_TOO_LONG -> R.string.ingredient_error_name_long
    IngredientSaveError.UNIT_TOO_LONG -> R.string.ingredient_error_unit_long
    IngredientSaveError.TOO_MANY -> R.string.ingredient_error_too_many
    IngredientSaveError.TOO_MANY_UNITS -> R.string.ingredient_error_units
}

/**
 * Picks an ingredient for a meal, searching the catalog by name. New ingredients are made here
 * too, and existing ones edited, so a meal never has to be left to fix the catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientPickerDialog(
    catalog: List<Ingredient>,
    onDismiss: () -> Unit,
    onCatalogChanged: () -> Unit,
    onPick: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Ingredient?>(null) }
    var creating by remember { mutableStateOf(false) }

    FullScreenDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(
                title = { Text(stringResource(R.string.meal_add_ingredient), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.closename))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.ingredient_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.Gutter)
            )
            val shown = remember(catalog, query) {
                val needle = query.trim()
                catalog.filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenLayout.Gutter, vertical = 8.dp)
            ) {
                if (catalog.size < MealStore.MAX_INGREDIENTS) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(16.dp))
                            Text(
                                text = if (query.isBlank()) stringResource(R.string.ingredient_new)
                                else stringResource(R.string.ingredient_new_named, query.trim()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(shown, key = { it.index }) { ingredient ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IngredientRow(
                        ingredient = ingredient,
                        onClick = { onPick(ingredient.index) },
                        onEdit = { editing = ingredient }
                    )
                }
                if (catalog.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ingredient_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        IngredientEditorDialog(
            ingredient = null,
            initialName = query.trim(),
            onDismiss = { creating = false },
            onSaved = { index ->
                creating = false
                onCatalogChanged()
                onPick(index)
            }
        )
    }
    editing?.let { ingredient ->
        IngredientEditorDialog(
            ingredient = ingredient,
            onDismiss = { editing = null },
            onSaved = {
                editing = null
                onCatalogChanged()
            },
            onDeleted = {
                editing = null
                onCatalogChanged()
            }
        )
    }
}

/** A catalog row: name and carbs per unit, with an edit action when [onEdit] is given. */
@Composable
internal fun IngredientRow(
    ingredient: Ingredient,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ingredientDescription(ingredient),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.ingredient_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Searches the bundled nutrient database and shows what a food contains per 100 g. Picking one
 * hands it back so an ingredient can be made from it (carbs per gram).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDatabaseDialog(
    onDismiss: () -> Unit,
    onPick: (FoodDetails) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<FoodHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<FoodDetails?>(null) }
    var showZero by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        // Wait for a pause in typing; native matches the whole database on every search.
        delay(300)
        hits = withContext(Dispatchers.IO) { MealStore.searchFood(query) }
        searched = true
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val back = { if (details != null) details = null else onDismiss() }
    FullScreenDialog(onDismiss = back) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(
                title = {
                    Text(
                        text = details?.name ?: stringResource(R.string.food_db_title),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.loc_action_back))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            val food = details
            if (food == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.food_db_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.Gutter)
                        .focusRequester(focus)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = ScreenLayout.Gutter, vertical = 8.dp)
                ) {
                    items(hits, key = { it.id }) { hit ->
                        Text(
                            text = hit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        details = withContext(Dispatchers.IO) { MealStore.foodDetails(hit.id) }
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    if (searched && hits.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.food_db_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            } else {
                FoodDetailsContent(
                    food = food,
                    showZero = showZero,
                    onShowZeroChange = { showZero = it },
                    onUse = { onPick(food) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FoodDetailsContent(
    food: FoodDetails,
    showZero: Boolean,
    onShowZeroChange: (Boolean) -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenLayout.Gutter, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(
                R.string.food_db_carbs_per_100,
                stringResource(R.string.log_value_carbs, plainAmount(food.carbsPerGram * 100f, 1))
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalLogbookColors.current.carbs.primary
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_db_use))
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.food_db_per_100),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.food_db_show_zero),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Switch(checked = showZero, onCheckedChange = onShowZeroChange)
        }
        Spacer(Modifier.height(4.dp))
        food.nutrients
            .filter { showZero || it.amount == null || it.amount > 0f }
            .forEach { nutrient -> NutrientRow(nutrient) }
    }
}

@Composable
private fun NutrientRow(nutrient: FoodNutrient) {
    val amount = nutrient.amount
    val value = when {
        nutrient.trace -> stringResource(R.string.food_db_trace)
        amount == null -> stringResource(R.string.food_db_unknown)
        else -> "${if (amount < 0.1f) plainAmount(amount, 3) else plainAmount(amount, 1)} ${nutrient.unit}".trim()
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = nutrient.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A dialog filling its own window edge to edge, like the entry editor. */
@Composable
internal fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        EditorWindowSetup()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box { content() }
        }
    }
}
