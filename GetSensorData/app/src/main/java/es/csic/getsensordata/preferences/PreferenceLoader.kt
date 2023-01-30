package es.csic.getsensordata.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import es.csic.getsensordata.R


class PreferenceLoader(private val context: Context) {
    private val preferences: SharedPreferences

    init {
        Log.d(Tag, "init")

        PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun loadBoolean(keyId: Int, defaultValueId: Int): Boolean {
        Log.d(Tag, "loadBoolean(keyId=$keyId, defaultValueId=$defaultValueId)")

        return preferences.getBoolean(
                context.getString(keyId),
                context.getString(defaultValueId).toBoolean()
        )
    }

    fun loadInt(keyId: Int, defaultValueId: Int): Int {
        Log.d(Tag, "loadInt(keyId=$keyId, defaultValueId=$defaultValueId)")

        val defaultValueAsString = context.getString(defaultValueId)
        val defaultValue = defaultValueAsString.toIntOrNull() ?: 0
        val valueAsString =
                preferences.getString(context.getString(keyId), context.getString(defaultValueId))
        return valueAsString!!.toIntOrNull() ?: defaultValue
    }

    fun loadFloat(keyId: Int, defaultValueId: Int): Float {
        Log.d(Tag, "loadFloat(keyId=$keyId, defaultValueId=$defaultValueId)")

        val defaultValueAsString = context.getString(defaultValueId)
        val defaultValue = defaultValueAsString.toFloatOrNull() ?: 0.0f
        val valueAsString =
                preferences.getString(context.getString(keyId), context.getString(defaultValueId))
        return valueAsString!!.toFloatOrNull() ?: defaultValue
    }

    fun loadString(keyId: Int, defaultValueId: Int): String {
        Log.d(Tag, "loadString(keyId=$keyId, defaultValueId=$defaultValueId)")

        return preferences.getString(context.getString(keyId), context.getString(defaultValueId))
                .toString()
    }

    inline fun <reified T : Enum<T>> loadEnum(keyId: Int, defaultValueId: Int): T {
        val name = loadString(keyId, defaultValueId)
        return enumValueOf(name)
    }

    companion object {
        internal val Tag = PreferenceLoader::class.java.simpleName
    }
}
