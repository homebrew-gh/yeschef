package app.yeschef.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val totalTime: String?,
    val recipeYield: String?,
    /** Full recipe JSON from Mealie, cached once the recipe has been opened or planned. */
    val detailJson: String?,
    /** Mealie category names, newline-separated. Categories are the cookbook's chapters. */
    val categories: String = "",
    /** Mealie tag names, newline-separated (protein, style). */
    val tags: String = "",
    val rating: Double? = null,
    /** ISO date-time it was last cooked, or null if never. */
    val lastMade: String? = null,
    val dateAdded: String? = null,
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON object of labelId -> position, from the list's label settings. */
    val labelOrderJson: String,
    /** JSON object of recipeId -> recipe name, for recipes added to this list. */
    val recipeNamesJson: String = "{}",
)

@Entity(tableName = "shopping_items", indices = [Index("listId")])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val text: String,
    val note: String?,
    val checked: Boolean,
    val position: Int,
    val labelId: String?,
    val labelName: String?,
    val labelColor: String?,
    /** The item exactly as Mealie returned it; null for items created offline. */
    val rawJson: String?,
    /** True while a local change has not reached the server yet. */
    val pending: Boolean,
    /** Comma-separated ids of the recipes this item came from. */
    val recipeIds: String = "",
)

@Entity(tableName = "meal_plan")
data class MealPlanEntity(
    @PrimaryKey val id: Int,
    /** ISO date, yyyy-MM-dd. */
    val date: String,
    val entryType: String,
    val title: String?,
    val text: String?,
    val recipeId: String?,
    val recipeSlug: String?,
    val recipeName: String?,
)

/** A queued edit, replayed in order against Mealie when the server is reachable. */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val kind: String,
    val itemId: String,
    val listId: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SET_CHECKED = "set_checked"
        const val CREATE = "create"
        /** Mark a recipe cooked: itemId is the slug, listId holds the ISO timestamp. */
        const val LAST_MADE = "last_made"
        /** Delete a shopping item on the server; the local row is already gone. */
        const val DELETE_ITEM = "delete_item"
    }
}
