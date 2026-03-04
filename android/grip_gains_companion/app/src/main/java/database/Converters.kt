package app.grip_gains_companion.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDoubleList(value: List<Double>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toDoubleList(value: String?): List<Double> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { it.toDoubleOrNull() }
    }
}