package tk.glucodata.ui.data

import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.ui.model.FoodDetails
import tk.glucodata.ui.model.FoodHit
import tk.glucodata.ui.model.FoodNutrient
import tk.glucodata.ui.model.Ingredient
import tk.glucodata.ui.model.IngredientSaveError
import tk.glucodata.ui.model.MealItem

/**
 * The native meal store (`meals.dat`): the ingredient catalog, the meals attached to carbs
 * entries, and the bundled food database.
 *
 * Everything here is a blocking JNI call into memory mapped data, so it is called off the main
 * thread and serialized on one lock. Meals are built from a list of [MealItem]s in one go when an
 * entry is saved, instead of being edited in place like the classic view did, so cancelling an
 * edit never leaves a half changed meal behind.
 */
object MealStore {
    private const val LOG_ID = "MealStore"
    private val lock = Any()

    /** `ingredients_t` holds 410 entries. */
    const val MAX_INGREDIENTS = 410

    /** `units_ingredient_t` holds 40 unit names. */
    private const val MAX_UNITS = 40

    /** `ingredient_t::name` is a `char[40]`, a unit name a `char[20]`, both holding UTF-8. */
    const val MAX_NAME_BYTES = 39
    const val MAX_UNIT_BYTES = 19

    /** `itemsinmeal` only accepts meals of fewer than 40 items. */
    const val MAX_MEAL_ITEMS = 39

    private const val MAX_FOOD_HITS = 300

    /** Meals are a phone feature, like in the classic view: the watch only mirrors them. */
    val available: Boolean get() = !Applic.isWearable && Applic.Nativesloaded

    fun ingredients(): List<Ingredient> = synchronized(lock) {
        if (!available) return emptyList()
        try {
            val count = Natives.ingredientNr()
            (0 until count).map { index ->
                Ingredient(
                    index = index,
                    name = Natives.ingredientName(index).orEmpty(),
                    unit = Natives.ingredientUnitName(index).orEmpty(),
                    carbsPerUnit = Natives.ingredientCarb(index),
                    deletable = Natives.ingredientdeleteable(index)
                )
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "ingredients", th)
            emptyList()
        }
    }

    /** The unit names ingredients already use, so a new one can reuse them. */
    fun units(): List<String> = synchronized(lock) {
        if (!available) return emptyList()
        try {
            Natives.getunits().orEmpty().filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Saves ingredient [index], or adds one when it is below zero. Returns the error, or null and
     * the index it ended up at.
     */
    fun saveIngredient(index: Int, name: String, unit: String, carbsPerUnit: Float): Pair<IngredientSaveError?, Int> =
        synchronized(lock) {
            val trimmedName = name.trim()
            val trimmedUnit = unit.trim()
            when {
                trimmedName.isEmpty() -> return IngredientSaveError.EMPTY to -1
                trimmedName.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> return IngredientSaveError.NAME_TOO_LONG to -1
                trimmedUnit.toByteArray(Charsets.UTF_8).size > MAX_UNIT_BYTES -> return IngredientSaveError.UNIT_TOO_LONG to -1
            }
            if (!available) return IngredientSaveError.TOO_MANY to -1
            try {
                val count = Natives.ingredientNr()
                if (index < 0 && count >= MAX_INGREDIENTS) return IngredientSaveError.TOO_MANY to -1
                val units = Natives.getunits().orEmpty().filter { it.isNotEmpty() }
                if (units.none { it.equals(trimmedUnit, ignoreCase = true) } && units.size >= MAX_UNITS) {
                    return IngredientSaveError.TOO_MANY_UNITS to -1
                }
                Natives.saveingredient(index, trimmedName, trimmedUnit, carbsPerUnit)
                null to if (index < 0) Natives.ingredientNr() - 1 else index
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "saveIngredient", th)
                IngredientSaveError.TOO_MANY to -1
            }
        }

    fun deleteIngredient(index: Int): Boolean = synchronized(lock) {
        if (!available) return false
        try {
            if (!Natives.ingredientdeleteable(index)) return false
            Natives.deleteingredient(index)
            true
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "deleteIngredient", th)
            false
        }
    }

    /** The items of the meal at [mealPointer], empty when it is not a valid meal. */
    fun readMeal(mealPointer: Int): List<MealItem> = synchronized(lock) {
        if (mealPointer <= 0 || !Applic.Nativesloaded) return emptyList()
        try {
            val count = Natives.getmealitemnr(mealPointer)
            val ingredients = Natives.ingredientNr()
            (0 until count).mapNotNull { position ->
                val ingredient = Natives.getitemingredient(mealPointer, position)
                val amount = Natives.getitemamount(mealPointer, position)
                if (ingredient !in 0 until ingredients || amount.isNaN()) null else MealItem(ingredient, amount)
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "readMeal", th)
            emptyList()
        }
    }

    /** The ingredient names of a meal, comma separated, for showing it in a list. */
    fun summary(mealPointer: Int): String = synchronized(lock) {
        if (mealPointer <= 0 || !Applic.Nativesloaded) return ""
        try {
            val count = Natives.getmealitemnr(mealPointer)
            (0 until count).mapNotNull { Natives.getitemingredientname(mealPointer, it) }.joinToString(", ")
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Writes [items] as a new meal and returns its pointer, or 0 for no meal. The meal only becomes
     * permanent once an entry is saved with the pointer (native `endmeal`).
     */
    fun createMeal(items: List<MealItem>): Int = synchronized(lock) {
        if (items.isEmpty() || !available) return 0
        try {
            val ingredients = Natives.ingredientNr()
            var pointer = Natives.getnewmealptr()
            items.take(MAX_MEAL_ITEMS)
                .filter { it.ingredient in 0 until ingredients && it.amount > 0f }
                .forEach { item -> pointer = Natives.changemealitem(pointer, -1, item.ingredient, item.amount) }
            if (Natives.getmealitemnr(pointer) > 0) pointer else 0
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "createMeal", th)
            0
        }
    }

    /** Frees a meal no entry uses any more. */
    fun deleteMeal(mealPointer: Int) {
        if (mealPointer <= 0 || !available) return
        synchronized(lock) {
            try {
                // Only a valid meal is touched: deleting walks back from the pointer.
                if (Natives.getmealitemnr(mealPointer) > 0) Natives.deletemeal(mealPointer)
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "deleteMeal", th)
            }
        }
    }

    /** The step meal totals are rounded to before they become the carbs amount. */
    fun roundTo(): Float = try { Natives.getroundto() } catch (_: Throwable) { 1f }

    fun setRoundTo(step: Float) {
        try { Natives.setroundto(step.coerceAtLeast(0f)) } catch (_: Throwable) {}
    }

    // --- Food database -------------------------------------------------------------------

    val foodDatabaseAvailable: Boolean
        get() = available && try { Natives.foodnr() > 0 } catch (_: Throwable) { false }

    fun searchFood(query: String): List<FoodHit> = synchronized(lock) {
        if (query.isBlank() || !available) return emptyList()
        var hits = 0L
        try {
            hits = Natives.foodsearch(query.trim())
            val count = minOf(Natives.foodhitnr(hits), MAX_FOOD_HITS)
            (0 until count).map { position ->
                FoodHit(Natives.getfoodid(hits, position), Natives.foodlabel(hits, position).orEmpty())
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "searchFood", th)
            emptyList()
        } finally {
            if (hits != 0L) try { Natives.freefoodptr(hits) } catch (_: Throwable) {}
        }
    }

    /**
     * The nutrients of a database food. Native gives them per 100 g in thousandths, with -1 for
     * "not listed", -2 for unknown and -3 for a trace; the first one is the carbohydrate.
     */
    fun foodDetails(id: Int): FoodDetails? = synchronized(lock) {
        if (!available) return null
        try {
            val values = Natives.getcomponents(id) ?: return null
            val names = Natives.getcomponentlabels().orEmpty()
            val units = Natives.getcomponentunits().orEmpty()
            val nutrients = values.indices.mapNotNull { index ->
                val raw = values[index]
                if (raw == -1) return@mapNotNull null
                FoodNutrient(
                    name = names.getOrNull(index).orEmpty(),
                    unit = units.getOrNull(index).orEmpty(),
                    amount = if (raw >= 0) raw / 1000f else null,
                    trace = raw == -3
                )
            }
            FoodDetails(
                id = id,
                name = Natives.idfoodlabel(id).orEmpty(),
                carbsPerGram = values.firstOrNull()?.takeIf { it > 0 }?.let { it / 100_000f } ?: 0f,
                unit = units.firstOrNull().orEmpty().ifBlank { "g" },
                nutrients = nutrients
            )
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "foodDetails", th)
            null
        }
    }
}
