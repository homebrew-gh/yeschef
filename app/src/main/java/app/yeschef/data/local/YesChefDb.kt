package app.yeschef.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        MealPlanEntity::class,
        PendingOpEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class YesChefDb : RoomDatabase() {
    abstract fun recipes(): RecipeDao
    abstract fun shopping(): ShoppingDao
    abstract fun mealPlan(): MealPlanDao

    companion object {
        fun build(context: Context): YesChefDb =
            Room.databaseBuilder(context, YesChefDb::class.java, "yeschef.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Everything here is a cache of the server except unsent edits.
                .fallbackToDestructiveMigration()
                .build()
    }
}

/** v2: recipe tags on shopping items. Keeps queued edits. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN recipeNamesJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN recipeIds TEXT NOT NULL DEFAULT ''")
    }
}

/** v3: cookbook fields on recipes (categories, tags, rating, last cooked, date added). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN categories TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipes ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN lastMade TEXT")
        db.execSQL("ALTER TABLE recipes ADD COLUMN dateAdded TEXT")
    }
}
