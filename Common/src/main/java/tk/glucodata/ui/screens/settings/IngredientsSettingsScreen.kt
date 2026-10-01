package tk.glucodata.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.components.IngredientEditorDialog
import tk.glucodata.ui.components.IngredientRow
import tk.glucodata.ui.data.MealStore
import tk.glucodata.ui.model.Ingredient
import tk.glucodata.ui.screens.ScreenLayout
import java.util.Locale

/**
 * The ingredient catalog meals are composed from: carbs per unit of each thing that is eaten,
 * optionally filled in from the bundled food database. A plain list, since it can hold hundreds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsSettingsScreen(onNavigateBack: () -> Unit) {
    var catalog by remember { mutableStateOf<List<Ingredient>>(emptyList()) }
    var reloads by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Ingredient?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(reloads) {
        catalog = withContext(Dispatchers.IO) { MealStore.ingredients() }
    }
    val shown = remember(catalog, query) {
        val needle = query.trim()
        catalog.filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    BackHandler(onBack = onNavigateBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meals_ingredients), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.loc_action_back))
                    }
                },
                actions = {
                    if (catalog.size < MealStore.MAX_INGREDIENTS) {
                        IconButton(onClick = { creating = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ingredient_new))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = ScreenLayout.Gutter,
                end = ScreenLayout.Gutter,
                top = ScreenLayout.TopPadding,
                bottom = 36.dp
            )
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.ingredient_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
            items(shown, key = { it.index }) { ingredient ->
                IngredientRow(ingredient = ingredient, onClick = { editing = ingredient })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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

    if (creating) {
        IngredientEditorDialog(
            ingredient = null,
            initialName = query.trim(),
            onDismiss = { creating = false },
            onSaved = {
                creating = false
                reloads++
            }
        )
    }
    editing?.let { ingredient ->
        IngredientEditorDialog(
            ingredient = ingredient,
            onDismiss = { editing = null },
            onSaved = {
                editing = null
                reloads++
            },
            onDeleted = {
                editing = null
                reloads++
            }
        )
    }
}
