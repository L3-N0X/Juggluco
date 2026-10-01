package tk.glucodata.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.data.MealStore
import tk.glucodata.ui.model.Ingredient
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.MealItem
import tk.glucodata.ui.model.NumberStore
import tk.glucodata.ui.model.RecentMeal
import tk.glucodata.ui.model.mealCarbs
import tk.glucodata.ui.model.roundMealCarbs
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.screens.SectionTitle
import tk.glucodata.ui.theme.LocalLogbookColors

private const val RECENT_MEAL_COUNT = 6

/**
 * Builds the meal behind a carbs entry from ingredients, adding up the carbs.
 *
 * Opened from the carbs tile of the entry editor, in its own window above it. Nothing is written
 * here: [onDone] hands back the items, the (possibly grown) catalog and the total rounded to the
 * meal rounding step, and the meal is stored when the entry is saved. An empty meal offers the
 * recent ones to start from, which is how a meal is repeated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealComposer(
    initialItems: List<MealItem>,
    logs: List<LogRecord>,
    onDismiss: () -> Unit,
    onDone: (items: List<MealItem>, catalog: List<Ingredient>, carbs: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<MealItem>().apply { addAll(initialItems) } }
    var catalog by remember { mutableStateOf<List<Ingredient>>(emptyList()) }
    var roundTo by remember { mutableFloatStateOf(1f) }
    var recentMeals by remember { mutableStateOf<List<RecentMeal>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var editedItem by remember { mutableStateOf<Int?>(null) }
    var addedIngredient by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(MealStore.ingredients(), MealStore.roundTo(), recentMeals(logs))
        }
        catalog = loaded.first
        roundTo = loaded.second
        recentMeals = loaded.third
    }
    fun reloadCatalog() {
        scope.launch { catalog = withContext(Dispatchers.IO) { MealStore.ingredients() } }
    }

    val carbs = mealCarbs(items, catalog)
    val rounded = roundMealCarbs(carbs, roundTo)
    val carbsColor = LocalLogbookColors.current.carbs.primary

    FullScreenDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(
                title = { Text(stringResource(R.string.meal_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.closename))
                    }
                },
                actions = {
                    TextButton(onClick = { onDone(items.toList(), catalog, rounded) }) {
                        Text(stringResource(R.string.meal_use))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenLayout.Gutter, vertical = 8.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.meal_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
                itemsIndexed(items) { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    MealItemRow(
                        item = item,
                        ingredient = catalog.getOrNull(item.ingredient),
                        onClick = { editedItem = index }
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = { picking = true },
                        enabled = items.size < MealStore.MAX_MEAL_ITEMS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.meal_add_ingredient))
                    }
                }
                if (items.isEmpty() && recentMeals.isNotEmpty()) {
                    item {
                        SectionTitle(
                            text = stringResource(R.string.meal_recent),
                            modifier = Modifier.padding(top = 28.dp, bottom = 4.dp)
                        )
                    }
                    items(recentMeals) { meal ->
                        RecentMealRow(
                            meal = meal,
                            catalog = catalog,
                            onClick = { items.addAll(meal.items) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.Gutter, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.log_short_carbs),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (items.isNotEmpty() && rounded != carbs) {
                        Text(
                            text = stringResource(
                                R.string.meal_rounded,
                                stringResource(R.string.log_value_carbs, plainAmount(carbs, 1)),
                                stringResource(R.string.log_value_carbs, plainAmount(roundTo, 2))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.log_value_carbs, plainAmount(rounded, 1)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = carbsColor
                )
            }
        }
    }

    if (picking) {
        IngredientPickerDialog(
            catalog = catalog,
            onDismiss = { picking = false },
            onCatalogChanged = ::reloadCatalog,
            onPick = { index ->
                picking = false
                addedIngredient = index
            }
        )
    }

    addedIngredient?.let { index ->
        // A just created ingredient may not be in the catalog yet; it arrives with the reload.
        catalog.getOrNull(index)?.let { ingredient ->
            MealItemDialog(
                ingredient = ingredient,
                amount = null,
                restOfMeal = carbs,
                onDismiss = { addedIngredient = null },
                onSave = { amount ->
                    items.add(MealItem(index, amount))
                    addedIngredient = null
                },
                onDelete = null
            )
        }
    }

    editedItem?.let { position ->
        val item = items.getOrNull(position)
        val ingredient = item?.let { catalog.getOrNull(it.ingredient) }
        if (item == null || ingredient == null) {
            LaunchedEffect(position) { editedItem = null }
        } else {
            MealItemDialog(
                ingredient = ingredient,
                amount = item.amount,
                restOfMeal = carbs - item.amount * ingredient.carbsPerUnit,
                onDismiss = { editedItem = null },
                onSave = { amount ->
                    items[position] = item.copy(amount = amount)
                    editedItem = null
                },
                onDelete = {
                    items.removeAt(position)
                    editedItem = null
                }
            )
        }
    }
}

/** The last few different meals logged on this device, newest first. */
private fun recentMeals(logs: List<LogRecord>): List<RecentMeal> {
    val seen = HashSet<String>()
    return logs.asSequence()
        .filter { it.hasMeal && it.nativeSource?.store == NumberStore.HERE }
        .filter { seen.add(it.mealSummary) }
        .take(RECENT_MEAL_COUNT)
        .map { RecentMeal(MealStore.readMeal(it.mealPointer), it.timestamp) }
        .filter { it.items.isNotEmpty() }
        .toList()
}

@Composable
private fun MealItemRow(item: MealItem, ingredient: Ingredient?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient?.name ?: stringResource(R.string.log_type_custom),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = amountWithUnit(item.amount, ingredient?.unit.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.log_value_carbs, plainAmount(item.amount * (ingredient?.carbsPerUnit ?: 0f), 1)),
            style = MaterialTheme.typography.titleMedium,
            color = LocalLogbookColors.current.carbs.primary
        )
    }
}

@Composable
private fun RecentMealRow(meal: RecentMeal, catalog: List<Ingredient>, onClick: () -> Unit) {
    val names = meal.items.mapNotNull { catalog.getOrNull(it.ingredient)?.name }.joinToString(", ")
    val whenText = DateUtils.getRelativeTimeSpanString(
        meal.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = names,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.log_value_carbs, plainAmount(mealCarbs(meal.items, catalog), 0)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * How much of [ingredient] goes into the meal. The amount and its carbs are linked both ways, so
 * either can be typed: "120 g of bread" or "the bread is 36 g carbs".
 */
@Composable
private fun MealItemDialog(
    ingredient: Ingredient,
    amount: Float?,
    restOfMeal: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
    onDelete: (() -> Unit)?
) {
    val perUnit = ingredient.carbsPerUnit
    var amountText by remember { mutableStateOf(amount?.let { plainAmount(it, 2) }.orEmpty()) }
    var carbsText by remember { mutableStateOf(amount?.let { plainAmount(it * perUnit, 1) }.orEmpty()) }
    val parsedAmount = parseDecimal(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ingredient.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = ingredientDescription(ingredient),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { typed ->
                        amountText = decimalInput(typed)
                        carbsText = parseDecimal(amountText)?.let { plainAmount(it * perUnit, 1) }.orEmpty()
                    },
                    label = { Text(stringResource(R.string.meal_item_amount)) },
                    suffix = { Text(ingredient.unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { typed ->
                        carbsText = decimalInput(typed)
                        if (perUnit > 0f) {
                            amountText = parseDecimal(carbsText)?.let { plainAmount(it / perUnit, 2) }.orEmpty()
                        }
                    },
                    enabled = perUnit > 0f,
                    label = { Text(stringResource(R.string.log_short_carbs)) },
                    suffix = { Text(stringResource(R.string.unit_carbs_short)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.meal_total_with_item,
                        stringResource(
                            R.string.log_value_carbs,
                            plainAmount(restOfMeal + (parsedAmount ?: 0f) * perUnit, 1)
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedAmount != null && parsedAmount > 0f,
                onClick = { parsedAmount?.let(onSave) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
