package app.grip_gains_companion.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RawSessionEntity::class],
    version = 2, // Increment from 1 to 2
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
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raw_sessions.db"
                )
                    .fallbackToDestructiveMigration() // Wipes dev data to apply new schema
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}