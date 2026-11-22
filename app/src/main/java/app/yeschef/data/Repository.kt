package app.yeschef.data

import app.yeschef.data.local.MealPlanEntity
import app.yeschef.data.local.PendingOpEntity
import app.yeschef.data.local.RecipeEntity
import app.yeschef.data.local.ShoppingItemEntity
import app.yeschef.data.local.ShoppingListEntity
import app.yeschef.data.local.YesChefDb
import app.yeschef.data.remote.MealieApi
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.remote.NewShoppingItem
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.ShoppingItemDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val at: Long) : SyncState
    /** Server unreachable (VPN down, no signal). Local data and queued edits are kept. */
    data class Offline(val message: String) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Single source of truth for the UI is the local Room database. The server is
 * pulled in full on each sync (Mealie has no "changed since" query for these
 * endpoints, and one person's data is small), after queued edits are pushed.
 */
class Repository(
    private val db: YesChefDb,
    private val apiProvider: () -> MealieApi?,
) {
    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // ---- Reads (always local) ----

    fun recipes(): Flow<List<RecipeEntity>> = db.recipes().observeAll()

    fun recipe(slug: String): Flow<Pair<RecipeEntity?, RecipeDetailDto?>> =
        db.recipes().observe(slug).map { entity ->
            entity to entity?.detailJson?.let { runCatching { MealieJson.decodeFromString<RecipeDetailDto>(it) }.getOrNull() }
        }

    fun shoppingLists(): Flow<List<ShoppingListEntity>> = db.shopping().observeLists()

    fun shoppingItems(listId: String): Flow<List<ShoppingItemEntity>> = db.shopping().observeItems(listId)

    fun mealPlan(from: LocalDate): Flow<List<MealPlanEntity>> = db.mealPlan().observeFrom(from.toString())

    fun pendingCount(): Flow<Int> = db.shopping().observePendingCount()

    // ---- Local edits (queued) ----

    suspend fun setChecked(itemId: String, checked: Boolean) {
        val dao = db.shopping()
        val item = dao.item(itemId) ?: return
        dao.upsertItem(item.copy(checked = checked, pending = true))
        // Items created offline are sent whole by their CREATE op, with their latest state.
        if (item.rawJson != null) {
            dao.enqueue(PendingOpEntity(kind = PendingOpEntity.SET_CHECKED, itemId = itemId, listId = item.listId))
        }
    }

    suspend fun addItem(listId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val id = "local-" + UUID.randomUUID()
        val dao = db.shopping()
        dao.upsertItem(
            ShoppingItemEntity(
                id = id, listId = listId, text = trimmed, note = trimmed, checked = false,
                position = Int.MAX_VALUE, labelId = null, labelName = null, labelColor = null,
                rawJson = null, pending = true,
            ),
        )
        dao.enqueue(PendingOpEntity(kind = PendingOpEntity.CREATE, itemId = id, listId = listId))
    }

    // ---- Sync ----

    suspend fun sync(): SyncState = syncMutex.withLock {
        val api = apiProvider() ?: return SyncState.Idle
        _syncState.value = SyncState.Syncing
        val result = try {
            pushPending(api)
            pull(api)
            SyncState.Synced(System.currentTimeMillis())
        } catch (e: IOException) {
            SyncState.Offline(e.message ?: "Can't reach the server. Is the VPN on?")
        } catch (e: HttpException) {
            if (e.code() == 401) SyncState.Failed("Mealie rejected the API token. Check it in Settings.")
            else SyncState.Failed("Mealie returned HTTP ${e.code()}")
        }
        _syncState.value = result
        result
    }

    /** Opening a recipe fetches it fresh if we can; the cached copy is shown meanwhile. */
    suspend fun refreshRecipe(slug: String) {
        val api = apiProvider() ?: return
        runCatching { cacheDetail(api, slug) }
    }

    private suspend fun cacheDetail(api: MealieApi, slug: String) {
        val detail = api.recipe(slug)
        db.recipes().setDetail(slug, MealieJson.encodeToString(detail))
    }

    private suspend fun pushPending(api: MealieApi) {
        val dao = db.shopping()
        for (op in dao.pendingOps()) {
            val item = dao.item(op.itemId)
            try {
                when {
                    item == null -> Unit
                    op.kind == PendingOpEntity.CREATE -> {
                        api.createShoppingItem(
                            NewShoppingItem(shoppingListId = op.listId, note = item.note ?: item.text, checked = item.checked),
                        )
                        dao.deleteItem(item.id) // the server copy arrives with the pull
                    }
                    op.kind == PendingOpEntity.SET_CHECKED && item.rawJson != null -> {
                        val raw = MealieJson.parseToJsonElement(item.rawJson).jsonObject
                        val updated = raw.withChecked(item.checked)
                        api.updateShoppingItem(item.id, updated)
                        dao.upsertItem(item.copy(rawJson = updated.toString()))
                    }
                }
            } catch (e: HttpException) {
                // 4xx other than auth means this edit can never succeed (e.g. the item was
                // deleted on the server). Drop it rather than block the queue forever.
                if (e.code() == 401 || e.code() >= 500) throw e
            }
            dao.deleteOp(op.opId)
            if (item != null && dao.pendingCountFor(item.id) == 0) dao.clearPending(item.id)
        }
    }

    private suspend fun pull(api: MealieApi) {
        // Recipes (summaries; details are cached on demand and for planned meals)
        val recipes = api.recipes().items.map {
            RecipeEntity(it.id, it.slug, it.name, it.description, it.totalTime, it.recipeYield, detailJson = null)
        }
        db.recipes().replaceSummaries(recipes)

        // Shopping lists
        val summaries = api.shoppingLists().items
        val lists = mutableListOf<ShoppingListEntity>()
        val items = mutableMapOf<String, List<ShoppingItemEntity>>()
        for (summary in summaries) {
            val list = api.shoppingList(summary.id)
            val order = list.labelSettings.associate { it.labelId to it.position }
            val recipeNames = list.recipeReferences.mapNotNull { ref -> ref.recipe?.name?.let { ref.recipeId to it } }.toMap()
            lists += ShoppingListEntity(
                id = list.id,
                name = list.name ?: "Shopping list",
                labelOrderJson = MealieJson.encodeToString(order),
                recipeNamesJson = MealieJson.encodeToString(recipeNames),
            )
            items[list.id] = list.listItems.mapNotNull { it.toEntity(list.id) }
        }
        db.shopping().replaceFromServer(lists, items)

        // Meal plan: last week through the next three weeks
        val today = LocalDate.now()
        val plan = api.mealPlans(today.minusDays(7).toString(), today.plusDays(21).toString()).items
        db.mealPlan().replace(
            plan.map {
                MealPlanEntity(
                    id = it.id, date = it.date, entryType = it.entryType, title = it.title, text = it.text,
                    recipeId = it.recipeId, recipeSlug = it.recipe?.slug, recipeName = it.recipe?.name,
                )
            },
        )

        // Pre-cache upcoming planned recipes so they open offline in the store or kitchen.
        val cached = db.recipes().all().filter { it.detailJson != null }.map { it.slug }.toSet()
        plan.filter { it.date >= today.toString() }
            .mapNotNull { it.recipe?.slug }
            .distinct()
            .filterNot { it in cached }
            .forEach { slug -> runCatching { cacheDetail(api, slug) } }
    }

    private fun JsonObject.toEntity(listId: String): ShoppingItemEntity? {
        val dto = runCatching { MealieJson.decodeFromJsonElement<ShoppingItemDto>(this) }.getOrNull() ?: return null
        val label = dto.label ?: dto.food?.label
        return ShoppingItemEntity(
            id = dto.id,
            listId = listId,
            text = itemText(dto.display, dto.note, dto.food?.name, dto.quantity),
            note = dto.note,
            checked = dto.checked,
            position = dto.position,
            labelId = label?.id,
            labelName = label?.name,
            labelColor = label?.color,
            rawJson = this.toString(),
            pending = false,
            recipeIds = dto.recipeReferences.mapNotNull { it.recipeId }.distinct().joinToString(","),
        )
    }
}
