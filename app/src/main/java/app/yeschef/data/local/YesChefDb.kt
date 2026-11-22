package app.yeschef.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        MealPlanEntity::class,
        PendingOpEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class YesChefDb : RoomDatabase() {
    abstract fun recipes(): RecipeDao
    abstract fun shopping(): ShoppingDao
    abstract fun mealPlan(): MealPlanDao

    companion object {
        fun build(context: Context): YesChefDb =
            Room.databaseBuilder(context, YesChefDb::class.java, "yeschef.db")
                // Everything here is a cache of the server except unsent edits; fine for v1.
                .fallbackToDestructiveMigration()
                .build()
    }
}
