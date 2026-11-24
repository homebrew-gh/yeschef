package app.yeschef.data

import app.yeschef.data.local.MealPlanEntity
import app.yeschef.data.local.PendingOpEntity
import app.yeschef.data.local.RecipeEntity
import app.yeschef.data.local.ShoppingItemEntity
import app.yeschef.data.local.ShoppingListEntity
import app.yeschef.data.local.YesChefDb
import app.yeschef.data.remote.LastMadeBody
import kotlinx.serialization.json.JsonPrimitive
import app.yeschef.data.remote.MealieApi
import app.yeschef.data.remote.OrganizerDto
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.remote.NewShoppingItem
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.ShoppingItemDto
import app.yeschef.data.remote.ParseIngredientsBody
import app.yeschef.data.cook.scaleIngredient
import app.yeschef.data.cook.CookNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val importApiProvider: () -> MealieApi? = apiProvider,
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

    /** Takes everything ticked off this list. Works offline; deletes reach Mealie on the next sync. */
    suspend fun clearChecked(listId: String) {
        val dao = db.shopping()
        for (item in dao.checkedItems(listId)) {
            dao.deleteItem(item.id)
            if (item.rawJson == null) {
                dao.deleteOpsFor(item.id) // never reached the server, so nothing to delete there
            } else {
                dao.enqueue(PendingOpEntity(kind = PendingOpEntity.DELETE_ITEM, itemId = item.id, listId = listId))
            }
        }
    }

    /** Unticks everything on this list, for lists that get reused (staples). */
    suspend fun uncheckAll(listId: String) {
        for (item in db.shopping().checkedItems(listId)) setChecked(item.id, false)
    }

    // List management needs the server: Mealie owns the list ids.

    /** Creates a list on the server and returns its id. */
    suspend fun createList(name: String): String? {
        val api = apiProvider() ?: error("Not connected")
        val created = api.createShoppingList(JsonObject(mapOf("name" to JsonPrimitive(name.trim()))))
        sync()
        return created.string("id")
    }

    suspend fun renameList(id: String, name: String) {
        val api = apiProvider() ?: error("Not connected")
        val raw = api.shoppingListRaw(id)
        api.updateShoppingList(id, JsonObject(raw + ("name" to JsonPrimitive(name.trim()))))
        sync()
    }

    suspend fun deleteList(id: String) {
        val api = apiProvider() ?: error("Not connected")
        api.deleteShoppingList(id)
        sync()
    }

    /**
     * Sets a recipe's categories (its chapter) and tags by name. Shown at once and
     * sent with the next sync; names Mealie doesn't have yet are created then.
     */
    suspend fun setOrganizers(slug: String, categories: List<String>, tags: List<String>) {
        db.recipes().setOrganizers(slug, categories.joinToString("\n"), tags.joinToString("\n"))
        val payload = OrganizerChange(categories, tags)
        db.shopping().enqueue(
            PendingOpEntity(kind = PendingOpEntity.SET_ORGANIZERS, itemId = slug, listId = MealieJson.encodeToString(payload)),
        )
    }

    private suspend fun pushOrganizers(api: MealieApi, slug: String, json: String) {
        val change = MealieJson.decodeFromString<OrganizerChange>(json)
        suspend fun resolve(
            names: List<String>,
            existing: suspend () -> List<OrganizerDto>,
            create: suspend (JsonObject) -> OrganizerDto,
        ): JsonArray {
            val known = if (names.isEmpty()) emptyMap() else existing().associateBy { it.name.lowercase() }
            return JsonArray(
                names.map { name ->
                    val o = known[name.lowercase()] ?: create(JsonObject(mapOf("name" to JsonPrimitive(name))))
                    JsonObject(mapOf("id" to JsonPrimitive(o.id), "name" to JsonPrimitive(o.name), "slug" to JsonPrimitive(o.slug)))
                },
            )
        }
        val categories = resolve(change.c, { api.categories().items }, { api.createCategory(it) })
        val tags = resolve(change.t, { api.tags().items }, { api.createTag(it) })
        api.patchRecipe(slug, JsonObject(mapOf("recipeCategory" to categories, "tags" to tags)))
    }

    /**
     * Saves a hand edit of a recipe. Shown at once and sent with the next sync, so it
     * works offline. Mealie re-reads new and changed ingredient lines when it gets them,
     * so they scale and go on the shopping list like any other.
     */
    suspend fun saveRecipe(slug: String, detail: RecipeDetailDto, draft: RecipeDraft): Boolean {
        val change = draft.changesFrom(detail) ?: return false
        val updated = detail.withChange(change)
        db.recipes().setDetail(slug, MealieJson.encodeToString(updated))
        db.recipes().setSummary(slug, updated.name, updated.description, updated.totalTime, updated.recipeYield)
        db.shopping().enqueue(
            PendingOpEntity(kind = PendingOpEntity.SAVE_RECIPE, itemId = detail.id, listId = MealieJson.encodeToString(change)),
        )
        return true
    }

    /** The slug a recipe has now (Mealie changes it when the recipe is renamed). */
    suspend fun slugOf(recipeId: String): String? = db.recipes().byId(recipeId)?.slug

    private suspend fun pushRecipe(api: MealieApi, json: String) {
        val change = MealieJson.decodeFromString<RecipeChange>(json)
        val raw = api.recipeRaw(change.recipeId)
        val oldSlug = raw.string("slug")
        val parsed = parseLines(api, change)
        val saved = api.patchRecipeJson(change.recipeId, buildRecipePatch(raw, change, parsed))
        val detail = MealieJson.decodeFromJsonElement<RecipeDetailDto>(saved)
        db.recipes().setSlugAndDetail(detail.id, detail.slug, MealieJson.encodeToString(detail))
        if (oldSlug != null && oldSlug != detail.slug) db.shopping().renameRecipeOps(oldSlug, detail.slug)
    }

    /**
     * Runs Mealie's parser over the new and changed lines. Foods and units it doesn't
     * know yet are created, as Mealie's own parse page does. If the parser fails, the
     * lines are saved as plain text.
     */
    private suspend fun parseLines(api: MealieApi, change: RecipeChange): Map<Int, JsonObject> {
        val lines = change.ingredients ?: return emptyMap()
        val todo = change.linesToParse()
        if (todo.isEmpty()) return emptyMap()
        val results = try {
            api.parseIngredients(ParseIngredientsBody(todo.map { lines[it].text }))
        } catch (e: HttpException) {
            if (e.code() == 401) throw e
            emptyList()
        }
        val foods = Lookup({ api.foods().items }, { api.createFood(it) })
        val units = Lookup({ api.units().items }, { api.createUnit(it) })
        return todo.mapIndexed { n, index ->
            val ing = results.getOrNull(n)?.get("ingredient") as? JsonObject
            val food = ing?.get("food")?.let { if (it.needsId()) foods.resolve(it as JsonObject) else it }
            val unit = ing?.get("unit")?.let { if (it.needsId()) units.resolve(it as JsonObject) else it }
            index to parsedIngredient(ing, lines[index].text, food, unit)
        }.toMap()
    }

    /** Finds a food or unit by name (or plural/abbreviation), creating it once if Mealie has none. */
    private class Lookup(val all: suspend () -> List<JsonObject>, val create: suspend (JsonObject) -> JsonObject) {
        private var known: MutableList<JsonObject>? = null
        suspend fun resolve(o: JsonObject): JsonObject {
            val name = o.string("name")!!.trim()
            val list = known ?: all().toMutableList().also { known = it }
            list.firstOrNull { k ->
                listOf("name", "pluralName", "abbreviation", "pluralAbbreviation").any { k.string(it)?.equals(name, ignoreCase = true) == true }
            }?.let { return it }
            return create(JsonObject(mapOf("name" to JsonPrimitive(name)))).also { list += it }
        }
    }

    /**
     * Adds a recipe's ingredients to a shopping list, scaled, leaving out the ones in
     * [skip] (indexes into the recipe's ingredients). The lines show on the list at once;
     * Mealie merges them with what's there on the next sync.
     */
    suspend fun addRecipeToList(listId: String, detail: RecipeDetailDto, scale: Double, skip: Set<Int>) {
        val keep = detail.recipeIngredient.withIndex().filter { (i, ing) -> i !in skip && !isHeadingOnly(ing) }
        if (keep.isEmpty()) return
        val dao = db.shopping()
        val localIds = keep.map { (_, ing) ->
            val text = scaleIngredient(ingredientLineText(ing), scale)
            val id = "local-" + UUID.randomUUID()
            dao.upsertItem(
                ShoppingItemEntity(
                    id = id, listId = listId, text = text, note = text, checked = false,
                    position = Int.MAX_VALUE, labelId = null, labelName = null, labelColor = null,
                    rawJson = null, pending = true, recipeIds = detail.id,
                ),
            )
            id
        }
        val addition = ListAddition(
            list = listId, scale = scale,
            refs = keep.mapNotNull { it.value.referenceId }, indexes = keep.filter { it.value.referenceId == null }.map { it.index },
            local = localIds,
        )
        dao.enqueue(PendingOpEntity(kind = PendingOpEntity.ADD_RECIPE_TO_LIST, itemId = detail.id, listId = MealieJson.encodeToString(addition)))
    }

    private suspend fun pushListAddition(api: MealieApi, recipeId: String, json: String) {
        val add = MealieJson.decodeFromString<ListAddition>(json)
        val raw = api.recipeRaw(recipeId)
        val ingredients = (raw["recipeIngredient"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val picked = selectIngredients(ingredients, add.refs.toSet(), add.indexes.toSet())
        if (picked.isNotEmpty()) {
            api.addRecipesToList(
                add.list,
                JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "recipeId" to JsonPrimitive(recipeId),
                                "recipeIncrementQuantity" to JsonPrimitive(add.scale),
                                "recipeIngredients" to JsonArray(picked),
                            ),
                        ),
                    ),
                ),
            )
        }
        // The server's lines arrive with the pull; until then the stand-ins showed them.
        add.local.forEach { db.shopping().deleteItem(it) }
    }

    /**
     * Saves cook-mode notes to the recipe's timeline in Mealie as one "Cook notes" entry.
     * Queued like other edits, so it works offline and goes with the next sync.
     */
    suspend fun saveCookNotes(recipeId: String, message: String) {
        val note = TimelineNote(java.time.OffsetDateTime.now().toString(), message)
        db.shopping().enqueue(
            PendingOpEntity(kind = PendingOpEntity.COOK_NOTES, itemId = recipeId, listId = MealieJson.encodeToString(note)),
        )
    }

    /** Marks a recipe as cooked now. Shown at once; sent to Mealie with the next sync. */
    suspend fun markCooked(slug: String) {
        val now = java.time.OffsetDateTime.now().toString()
        db.recipes().setLastMade(slug, now)
        db.shopping().enqueue(PendingOpEntity(kind = PendingOpEntity.LAST_MADE, itemId = slug, listId = now))
    }

    // ---- Meal plan edits (queued) ----
    // Shown at once and sent with the next sync, like shopping edits. Entries added
    // offline have a negative id until Mealie has them; edits to those just change the
    // local row, which the queued add sends as it is at sync time.

    /** Moves the given entries (a meal: main and sides) to another day and meal. */
    suspend fun moveMeal(entries: List<MealPlanEntity>, date: LocalDate, entryType: String) {
        val dao = db.mealPlan()
        for (e in entries) {
            dao.move(e.id, date.toString(), entryType)
            if (e.id >= 0) {
                db.shopping().enqueue(
                    PendingOpEntity(
                        kind = PendingOpEntity.MEAL_MOVE, itemId = e.id.toString(),
                        listId = MealieJson.encodeToString(MealMove(date.toString(), entryType)),
                    ),
                )
            }
        }
    }

    suspend fun removeMeal(entries: List<MealPlanEntity>) {
        for (e in entries) {
            db.mealPlan().delete(e.id)
            if (e.id < 0) db.shopping().deleteOpsFor(e.id.toString())
            else db.shopping().enqueue(PendingOpEntity(kind = PendingOpEntity.MEAL_DELETE, itemId = e.id.toString(), listId = ""))
        }
    }

    /** Swaps everything planned on two days, keeping each entry's meal. */
    suspend fun swapDays(a: LocalDate, b: LocalDate) {
        if (a == b) return
        val onA = db.mealPlan().onDate(a.toString())
        val onB = db.mealPlan().onDate(b.toString())
        for (e in onA) moveMeal(listOf(e), b, e.entryType)
        for (e in onB) moveMeal(listOf(e), a, e.entryType)
    }

    /** Plans recipes on a day, e.g. a main and its sides as dinner. */
    suspend fun addToPlan(date: LocalDate, entryType: String, recipes: List<RecipeEntity>) {
        for (r in recipes) {
            val id = -(1 + (UUID.randomUUID().mostSignificantBits ushr 33).toInt())
            db.mealPlan().insert(listOf(MealPlanEntity(id, date.toString(), entryType, null, null, r.id, r.slug, r.name)))
            db.shopping().enqueue(PendingOpEntity(kind = PendingOpEntity.MEAL_ADD, itemId = id.toString(), listId = ""))
        }
    }

    private suspend fun pushMealOp(api: MealieApi, op: PendingOpEntity) {
        val id = op.itemId.toIntOrNull() ?: return
        when (op.kind) {
            PendingOpEntity.MEAL_DELETE -> api.deleteMealPlan(id)
            PendingOpEntity.MEAL_MOVE -> {
                val move = MealieJson.decodeFromString<MealMove>(op.listId)
                val raw = api.mealPlanRaw(id)
                api.updateMealPlan(id, JsonObject(raw + mapOf("date" to JsonPrimitive(move.d), "entryType" to JsonPrimitive(move.t))))
            }
            PendingOpEntity.MEAL_ADD -> {
                val e = db.mealPlan().entry(id) ?: return
                val body = buildMap<String, kotlinx.serialization.json.JsonElement> {
                    put("date", JsonPrimitive(e.date))
                    put("entryType", JsonPrimitive(e.entryType))
                    put("title", JsonPrimitive(e.title.orEmpty()))
                    put("text", JsonPrimitive(e.text.orEmpty()))
                    e.recipeId?.let { put("recipeId", JsonPrimitive(it)) }
                }
                api.createMealPlan(JsonObject(body))
                db.mealPlan().delete(id) // the server copy arrives with the pull
            }
        }
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
        // Don't overwrite an edit that hasn't reached Mealie yet.
        val id = db.recipes().get(slug)?.id
        if (id != null && db.shopping().pendingKindCount(id, PendingOpEntity.SAVE_RECIPE) > 0) return
        runCatching { cacheDetail(api, slug) }
    }

    /**
     * Uploads a new photo (JPEG bytes) for a recipe. Needs the server, so it is not
     * queued offline. Returns the new image version for cache-busting.
     */
    suspend fun uploadRecipeImage(slug: String, jpeg: ByteArray): String {
        val api = apiProvider() ?: throw IOException("Not connected to a server")
        val part = MultipartBody.Part.createFormData(
            "image", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()),
        )
        val response = api.uploadRecipeImage(slug, part, "jpg".toRequestBody("text/plain".toMediaType()))
        runCatching { cacheDetail(api, slug) }
        return response.image ?: System.currentTimeMillis().toString()
    }

    // ---- Adding recipes (needs the server) ----

    /** Whether Mealie has an AI provider set up, and one for images. Off when it can't be read. */
    suspend fun aiStatus(): AiStatus {
        val api = apiProvider() ?: return AiStatus.Off
        return runCatching { AiStatus.from(api.groupSelf()) }.getOrDefault(AiStatus.Off)
    }

    /**
     * Imports a recipe page into Mealie and syncs it down; returns the new recipe's slug.
     * When Mealie's own reader can't find a recipe on the page and [aiFallback] is set, the
     * group's AI reads the page instead ([onStage] is told, so the UI can say so).
     */
    suspend fun importFromUrl(url: String, aiFallback: Boolean, onStage: (ImportStage) -> Unit = {}): String {
        val api = importApiProvider() ?: throw IOException("Not connected to a server")
        onStage(ImportStage.ReadingPage)
        val slug = try {
            api.createRecipeFromUrl(RecipeImport.urlBody(url)).use { RecipeImport.slugOf(it.string()) }
        } catch (e: HttpException) {
            if (!aiFallback || e.code() != 400) throw e
            onStage(ImportStage.AiReadingPage)
            api.createRecipeWithAi(listOf(MultipartBody.Part.createFormData("url", url)))
                .use { RecipeImport.slugOf(it.string()) }
        }
        sync()
        return slug
    }

    /** Has the group's AI write up a recipe from photos (JPEG bytes) of it; returns the slug. */
    suspend fun importFromPhotos(jpegs: List<ByteArray>): String {
        val api = importApiProvider() ?: throw IOException("Not connected to a server")
        val parts = jpegs.mapIndexed { i, bytes ->
            MultipartBody.Part.createFormData("images", "page-${i + 1}.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        }
        val slug = api.createRecipeWithAi(parts).use { RecipeImport.slugOf(it.string()) }
        sync()
        return slug
    }

    private suspend fun cacheDetail(api: MealieApi, slug: String) {
        val detail = api.recipe(slug)
        db.recipes().setDetail(slug, MealieJson.encodeToString(detail))
    }

    private suspend fun pushPending(api: MealieApi) {
        val dao = db.shopping()
        // One at a time from the front of the queue: a pushed edit can rewrite the ones
        // after it (a renamed recipe gets a new slug).
        while (true) {
            val op = dao.firstPendingOp() ?: break
            val item = dao.item(op.itemId)
            try {
                when {
                    op.kind == PendingOpEntity.LAST_MADE -> api.setLastMade(op.itemId, LastMadeBody(op.listId))
                    op.kind == PendingOpEntity.COOK_NOTES -> pushCookNotes(api, op.itemId, op.listId)
                    op.kind == PendingOpEntity.DELETE_ITEM -> api.deleteShoppingItem(op.itemId)
                    op.kind == PendingOpEntity.SET_ORGANIZERS -> pushOrganizers(api, op.itemId, op.listId)
                    op.kind == PendingOpEntity.SAVE_RECIPE -> pushRecipe(api, op.listId)
                    op.kind == PendingOpEntity.ADD_RECIPE_TO_LIST -> pushListAddition(api, op.itemId, op.listId)
                    op.kind in MEAL_OPS -> pushMealOp(api, op)
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
                dropped(api, op)
            }
            dao.deleteOp(op.opId)
            if (item != null && dao.pendingCountFor(item.id) == 0) dao.clearPending(item.id)
        }
    }

    private suspend fun pushCookNotes(api: MealieApi, recipeId: String, payload: String) {
        val note = MealieJson.decodeFromString<TimelineNote>(payload)
        api.createTimelineEvent(
            JsonObject(
                mapOf(
                    "recipeId" to JsonPrimitive(recipeId),
                    "subject" to JsonPrimitive(CookNotes.SUBJECT),
                    "eventType" to JsonPrimitive("comment"),
                    "eventMessage" to JsonPrimitive(note.message),
                    "timestamp" to JsonPrimitive(note.timestamp),
                ),
            ),
        )
    }

    /** Undoes what a dropped edit showed locally, so the app matches Mealie again. */
    private suspend fun dropped(api: MealieApi, op: PendingOpEntity) {
        when (op.kind) {
            PendingOpEntity.SAVE_RECIPE -> {
                val id = MealieJson.decodeFromString<RecipeChange>(op.listId).recipeId
                db.recipes().byId(id)?.let { runCatching { cacheDetail(api, it.slug) } }
            }
            PendingOpEntity.ADD_RECIPE_TO_LIST ->
                MealieJson.decodeFromString<ListAddition>(op.listId).local.forEach { db.shopping().deleteItem(it) }
        }
    }

    private suspend fun pull(api: MealieApi) {
        // Recipes (summaries; details are cached on demand and for planned meals)
        val recipes = api.recipes().items.map {
            RecipeEntity(
                it.id, it.slug, it.name, it.description, it.totalTime, it.recipeYield, detailJson = null,
                categories = it.recipeCategory.mapNotNull { c -> c.name }.joinToString("\n"),
                tags = it.tags.mapNotNull { t -> t.name }.joinToString("\n"),
                rating = it.rating, lastMade = it.lastMade, dateAdded = it.dateAdded,
            )
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

/** A queued "add recipe to shopping list": which lines (by reference id, else index), how scaled, and the local stand-in rows. */
@kotlinx.serialization.Serializable
private data class ListAddition(
    val list: String,
    val scale: Double,
    val refs: List<String>,
    val indexes: List<Int>,
    val local: List<String>,
)
private val MEAL_OPS = setOf(PendingOpEntity.MEAL_MOVE, PendingOpEntity.MEAL_DELETE, PendingOpEntity.MEAL_ADD)

/** A queued meal plan move: date and meal. */
@kotlinx.serialization.Serializable
private data class MealMove(val d: String, val t: String)

/** A queued category/tag change, by name. Short keys keep the queue row small. */
@kotlinx.serialization.Serializable
private data class OrganizerChange(val c: List<String>, val t: List<String>)

/** Queued cook notes: when they were saved and the timeline message. */
@kotlinx.serialization.Serializable
private data class TimelineNote(val timestamp: String, val message: String)
