package app.grip_gains_companion.database

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromDoubleList(value: List<Double>?): String {
        // Return an empty JSON array string instead of null
        return value?.let { JSONArray(it).toString() } ?: "[]"
    }

    @TypeConverter
    fun toDoubleList(value: String?): List<Double> {
        if (value == null || value == "null") return emptyList()
        return try {
            val array = JSONArray(value)
            val list = mutableListOf<Double>()
            for (i in 0 until array.length()) {
                list.add(array.getDouble(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}