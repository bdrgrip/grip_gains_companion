package app.grip_gains_companion.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RawSessionEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class) // Add this line
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawSessionDao(): RawSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "grip_gains_database"
                )
                    .fallbackToDestructiveMigration() // Add this line
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}