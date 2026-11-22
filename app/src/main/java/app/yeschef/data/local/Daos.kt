package app.yeschef.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE slug = :slug")
    abstract fun observe(slug: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE slug = :slug")
    abstract suspend fun get(slug: String): RecipeEntity?

    @Query("SELECT * FROM recipes")
    abstract suspend fun all(): List<RecipeEntity>

    @Upsert
    abstract suspend fun upsert(recipes: List<RecipeEntity>)

    @Query("UPDATE recipes SET detailJson = :json WHERE slug = :slug")
    abstract suspend fun setDetail(slug: String, json: String)

    @Query("UPDATE recipes SET categories = :categories, tags = :tags WHERE slug = :slug")
    abstract suspend fun setOrganizers(slug: String, categories: String, tags: String)

    @Query("UPDATE recipes SET lastMade = :iso WHERE slug = :slug")
    abstract suspend fun setLastMade(slug: String, iso: String)

    @Query("DELETE FROM recipes WHERE id NOT IN (:keep)")
    abstract suspend fun deleteAllExcept(keep: List<String>)

    @Transaction
    open suspend fun replaceSummaries(fresh: List<RecipeEntity>) {
        val details = all().associate { it.id to it.detailJson }
        upsert(fresh.map { it.copy(detailJson = details[it.id]) })
        deleteAllExcept(fresh.map { it.id })
    }
}

@Dao
abstract class ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY name COLLATE NOCASE")
    abstract fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    abstract fun observeItems(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    abstract suspend fun item(id: String): ShoppingItemEntity?

    @Upsert
    abstract suspend fun upsertItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    abstract suspend fun deleteItem(id: String)

    @Query("UPDATE shopping_items SET pending = 0 WHERE id = :id")
    abstract suspend fun clearPending(id: String)

    @Upsert
    abstract suspend fun upsertLists(lists: List<ShoppingListEntity>)

    @Query("DELETE FROM shopping_lists WHERE id NOT IN (:keep)")
    abstract suspend fun deleteListsExcept(keep: List<String>)

    @Query("DELETE FROM shopping_items WHERE listId NOT IN (:keep) AND pending = 0")
    abstract suspend fun deleteItemsOfOtherLists(keep: List<String>)

    @Query("DELETE FROM shopping_items WHERE listId = :listId AND pending = 0")
    abstract suspend fun deleteSyncedItems(listId: String)

    /** Server rows never overwrite rows with unsent local changes. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(items: List<ShoppingItemEntity>)

    @Transaction
    open suspend fun replaceFromServer(lists: List<ShoppingListEntity>, items: Map<String, List<ShoppingItemEntity>>) {
        upsertLists(lists)
        val ids = lists.map { it.id }
        deleteListsExcept(ids)
        deleteItemsOfOtherLists(ids)
        for ((listId, listItems) in items) {
            deleteSyncedItems(listId)
            insertIgnore(listItems)
        }
    }

    @Query("SELECT * FROM pending_ops ORDER BY opId")
    abstract suspend fun pendingOps(): List<PendingOpEntity>

    @Query("SELECT COUNT(*) FROM pending_ops")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND checked = 1")
    abstract suspend fun checkedItems(listId: String): List<ShoppingItemEntity>

    @Query("DELETE FROM pending_ops WHERE itemId = :itemId")
    abstract suspend fun deleteOpsFor(itemId: String)

    @Query("SELECT COUNT(*) FROM pending_ops WHERE itemId = :itemId")
    abstract suspend fun pendingCountFor(itemId: String): Int

    @Insert
    abstract suspend fun enqueue(op: PendingOpEntity)

    @Query("DELETE FROM pending_ops WHERE opId = :opId")
    abstract suspend fun deleteOp(opId: Long)
}

@Dao
abstract class MealPlanDao {
    @Query("SELECT * FROM meal_plan WHERE date >= :from ORDER BY date, entryType")
    abstract fun observeFrom(from: String): Flow<List<MealPlanEntity>>

    @Query("DELETE FROM meal_plan")
    abstract suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entries: List<MealPlanEntity>)

    @Transaction
    open suspend fun replace(entries: List<MealPlanEntity>) {
        clear()
        insert(entries)
    }

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    abstract suspend fun entry(id: Int): MealPlanEntity?

    @Query("SELECT * FROM meal_plan WHERE date = :date")
    abstract suspend fun onDate(date: String): List<MealPlanEntity>

    @Query("UPDATE meal_plan SET date = :date, entryType = :entryType WHERE id = :id")
    abstract suspend fun move(id: Int, date: String, entryType: String)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    abstract suspend fun delete(id: Int)
}
