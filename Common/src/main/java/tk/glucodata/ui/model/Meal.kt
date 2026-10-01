package tk.glucodata.ui.model

/** An entry of the native ingredient catalog: carbs per unit of something that is eaten. */
data class Ingredient(
    val index: Int,
    val name: String,
    val unit: String,
    val carbsPerUnit: Float,
    /** Native only removes the last ingredient, and only while no meal uses it. */
    val deletable: Boolean
)

/** One line of a meal: an amount of an ingredient, in the ingredient's unit. */
data class MealItem(val ingredient: Int, val amount: Float)

/** A meal logged before, offered to start a new one from. */
data class RecentMeal(val items: List<MealItem>, val timestamp: Long)

/** A food in the bundled nutrient database. */
data class FoodHit(val id: Int, val name: String)

/** One nutrient of a database food, per 100 g. [amount] is null when the value is not known. */
data class FoodNutrient(val name: String, val unit: String, val amount: Float?, val trace: Boolean)

data class FoodDetails(
    val id: Int,
    val name: String,
    /** Carbohydrate per gram, which is what an ingredient made from this food stores. */
    val carbsPerGram: Float,
    val unit: String,
    val nutrients: List<FoodNutrient>
)

/** Why an ingredient could not be saved. */
enum class IngredientSaveError { EMPTY, NAME_TOO_LONG, UNIT_TOO_LONG, TOO_MANY, TOO_MANY_UNITS }

/** Carbs in [items], looking each ingredient up in [catalog]. */
fun mealCarbs(items: List<MealItem>, catalog: List<Ingredient>): Float =
    items.sumOf { item ->
        ((catalog.getOrNull(item.ingredient)?.carbsPerUnit ?: 0f) * item.amount).toDouble()
    }.toFloat()

/** [carbs] rounded the way native rounds a meal total; a step of zero or less keeps it as is. */
fun roundMealCarbs(carbs: Float, step: Float): Float =
    if (step > 0f) Math.round(carbs / step) * step else carbs
