package app.grip_gains_companion.database

import androidx.room.TypeConverter

class DoubleListConverter {
    @TypeConverter
    fun fromList(list: List<Double>): String {
        return list.joinToString(",") { if (it.isNaN()) "NaN" else it.toString() }
    }

    @TypeConverter
    fun toList(data: String): List<Double> {
        if (data.isBlank()) return emptyList()
        return data.split(",").map {
            if (it == "NaN") Double.NaN else it.toDoubleOrNull() ?: 0.0
        }
    }
}