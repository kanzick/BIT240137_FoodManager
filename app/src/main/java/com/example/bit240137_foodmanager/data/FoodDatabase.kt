package com.example.bit240137_foodmanager.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "BIT240137_DishCategory")
data class DishCategory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "BIT240137_Id") val id: Long = 0,
    @ColumnInfo(name = "BIT240137_Name") val name: String,
    @ColumnInfo(name = "BIT240137_Description") val description: String
)

@Entity(
    tableName = "BIT240137_Dish",
    foreignKeys = [
        ForeignKey(
            entity = DishCategory::class,
            parentColumns = ["BIT240137_Id"],
            childColumns = ["BIT240137_DishCategoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["BIT240137_DishCategoryId", "BIT240137_Name"], unique = true)]
)
data class Dish(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "BIT240137_Id") val id: Long = 0,
    @ColumnInfo(name = "BIT240137_Name") val name: String,
    @ColumnInfo(name = "BIT240137_Price") val price: Double,
    @ColumnInfo(name = "BIT240137_PreparationTime") val preparationTime: Int,
    @ColumnInfo(name = "BIT240137_IsAvailable") val isAvailable: Boolean,
    @ColumnInfo(name = "BIT240137_Description") val description: String,
    @ColumnInfo(name = "BIT240137_DishCategoryId") val dishCategoryId: Long
)

@Entity(
    tableName = "BIT240137_DishImage",
    foreignKeys = [
        ForeignKey(
            entity = Dish::class,
            parentColumns = ["BIT240137_Id"],
            childColumns = ["BIT240137_DishId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("BIT240137_DishId")]
)
data class DishImage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "BIT240137_Id") val id: Long = 0,
    @ColumnInfo(name = "BIT240137_DishId") val dishId: Long,
    @ColumnInfo(name = "BIT240137_ImageUrl") val imageUrl: String,
    @ColumnInfo(name = "BIT240137_IsThumbnail") val isThumbnail: Boolean
)

data class DishWithDetails(
    @Embedded val dish: Dish,
    @Relation(
        parentColumn = "BIT240137_DishCategoryId",
        entityColumn = "BIT240137_Id"
    )
    val category: DishCategory,
    @Relation(
        parentColumn = "BIT240137_Id",
        entityColumn = "BIT240137_DishId"
    )
    val images: List<DishImage>
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM BIT240137_DishCategory ORDER BY BIT240137_Name COLLATE NOCASE")
    fun observeAll(): Flow<List<DishCategory>>

    @Query("SELECT * FROM BIT240137_DishCategory WHERE BIT240137_Id = :id LIMIT 1")
    suspend fun findById(id: Long): DishCategory?

    @Query("SELECT COUNT(*) FROM BIT240137_Dish WHERE BIT240137_DishCategoryId = :id")
    suspend fun dishCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM BIT240137_DishCategory")
    suspend fun count(): Int

    @Query(
        "SELECT COUNT(*) FROM BIT240137_DishCategory " +
            "WHERE BIT240137_Name = :name COLLATE NOCASE AND BIT240137_Id != :exceptId"
    )
    suspend fun duplicateName(name: String, exceptId: Long): Int

    @Insert
    suspend fun insert(category: DishCategory): Long

    @Query(
        "UPDATE BIT240137_DishCategory SET BIT240137_Name = :name, " +
            "BIT240137_Description = :description WHERE BIT240137_Id = :id"
    )
    suspend fun update(id: Long, name: String, description: String): Int

    @Query("DELETE FROM BIT240137_DishCategory WHERE BIT240137_Id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface DishDao {
    @Transaction
    @Query("SELECT * FROM BIT240137_Dish ORDER BY BIT240137_Name COLLATE NOCASE")
    fun observeAllWithDetails(): Flow<List<DishWithDetails>>

    @Query("SELECT COUNT(*) FROM BIT240137_Dish")
    suspend fun count(): Int

    @Query(
        "SELECT COUNT(*) FROM BIT240137_Dish WHERE BIT240137_DishCategoryId = :categoryId " +
            "AND BIT240137_Name = :name COLLATE NOCASE AND BIT240137_Id != :exceptId"
    )
    suspend fun duplicateName(categoryId: Long, name: String, exceptId: Long): Int

    @Insert
    suspend fun insert(dish: Dish): Long

    @androidx.room.Update
    suspend fun update(dish: Dish): Int

    @Query("DELETE FROM BIT240137_Dish WHERE BIT240137_Id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface DishImageDao {
    @Query("SELECT * FROM BIT240137_DishImage WHERE BIT240137_DishId = :dishId ORDER BY BIT240137_Id")
    fun observeForDish(dishId: Long): Flow<List<DishImage>>

    @Query("UPDATE BIT240137_DishImage SET BIT240137_IsThumbnail = 0 WHERE BIT240137_DishId = :dishId")
    suspend fun clearThumbnail(dishId: Long)

    @Insert
    suspend fun insert(image: DishImage): Long

    @Query(
        "UPDATE BIT240137_DishImage SET BIT240137_IsThumbnail = :isThumbnail " +
            "WHERE BIT240137_Id = :imageId AND BIT240137_DishId = :dishId"
    )
    suspend fun setThumbnail(dishId: Long, imageId: Long, isThumbnail: Boolean): Int

    @Query("DELETE FROM BIT240137_DishImage WHERE BIT240137_Id = :imageId AND BIT240137_DishId = :dishId")
    suspend fun delete(dishId: Long, imageId: Long): Int
}

@Database(
    entities = [DishCategory::class, Dish::class, DishImage::class],
    version = 2,
    exportSchema = false
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun dishDao(): DishDao
    abstract fun dishImageDao(): DishImageDao

    @Transaction
    open suspend fun addImage(image: DishImage): Long {
        if (image.isThumbnail) dishImageDao().clearThumbnail(image.dishId)
        return dishImageDao().insert(image)
    }

    @Transaction
    open suspend fun markThumbnail(dishId: Long, imageId: Long) {
        dishImageDao().clearThumbnail(dishId)
        dishImageDao().setThumbnail(dishId, imageId, true)
    }

    @Transaction
    open suspend fun addSampleDataIfNeeded() {
        val categories = categoryDao()
        if (categories.count() == 0) {
            val main = categories.insert(DishCategory(name = "Main course", description = "Hearty meals"))
            val soup = categories.insert(DishCategory(name = "Soup", description = "Warm bowls"))
            val dessert = categories.insert(DishCategory(name = "Dessert", description = "Sweet treats"))
            val dishes = dishDao()
            listOf(
                Dish(name = "Grilled chicken", price = 85_000.0, preparationTime = 25, isAvailable = true, description = "Herb-marinated chicken with vegetables.", dishCategoryId = main),
                Dish(name = "Beef noodles", price = 92_500.0, preparationTime = 20, isAvailable = true, description = "Slow-cooked beef with fresh noodles.", dishCategoryId = main),
                Dish(name = "Fried rice", price = 67_500.0, preparationTime = 12, isAvailable = true, description = "Wok-fried rice with seasonal vegetables.", dishCategoryId = main),
                Dish(name = "Pumpkin soup", price = 55_000.0, preparationTime = 18, isAvailable = false, description = "Creamy pumpkin soup with toasted seeds.", dishCategoryId = soup),
                Dish(name = "Chocolate cake", price = 42_500.0, preparationTime = 10, isAvailable = true, description = "Rich chocolate cake baked in-house.", dishCategoryId = dessert)
            ).forEach { dishes.insert(it) }
        }
    }

    companion object {
        @Volatile
        private var instance: FoodDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE BIT240137_Dish SET BIT240137_Price = 85000 " +
                        "WHERE BIT240137_Name = 'Grilled chicken' AND BIT240137_Price = 8.5"
                )
                db.execSQL(
                    "UPDATE BIT240137_Dish SET BIT240137_Price = 92500 " +
                        "WHERE BIT240137_Name = 'Beef noodles' AND BIT240137_Price = 9.25"
                )
                db.execSQL(
                    "UPDATE BIT240137_Dish SET BIT240137_Price = 67500 " +
                        "WHERE BIT240137_Name = 'Fried rice' AND BIT240137_Price = 6.75"
                )
                db.execSQL(
                    "UPDATE BIT240137_Dish SET BIT240137_Price = 55000 " +
                        "WHERE BIT240137_Name = 'Pumpkin soup' AND BIT240137_Price = 5.5"
                )
                db.execSQL(
                    "UPDATE BIT240137_Dish SET BIT240137_Price = 42500 " +
                        "WHERE BIT240137_Name = 'Chocolate cake' AND BIT240137_Price = 4.25"
                )
            }
        }

        fun get(context: Context): FoodDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodDatabase::class.java,
                    "bit240137_food_manager.db"
                ).addMigrations(migration1To2).build().also { instance = it }
            }
    }
}
