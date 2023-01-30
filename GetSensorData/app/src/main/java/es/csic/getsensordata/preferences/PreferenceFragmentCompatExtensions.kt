package es.csic.getsensordata.preferences

import android.text.InputType
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

fun PreferenceFragmentCompat.setNumberKeyboardType(preferenceKey: Int, inputType: Int = InputType.TYPE_CLASS_NUMBER) {
    Log.d("Extension", "setNumberKeyboardType(preferenceKey=$preferenceKey)")

    val editTextPreference: EditTextPreference? =
            preferenceManager.findPreference(getString(preferenceKey))
    editTextPreference?.setOnBindEditTextListener { editText ->
        editText.inputType = inputType
        editText.setSelection(editText.text.length)
    }
}

fun <T : Preference?> PreferenceFragmentCompat.showCustomSummary(preferenceKeyId: Int, summaryId: Int, valuesId: Int, entriesId: Int) {
    Log.d("Extension", "showCustomSummary(preferenceKeyId=$preferenceKeyId, summaryId=$summaryId, valuesId=$valuesId, entriesId=$entriesId)")

    val preferenceKey = requireContext().getString(preferenceKeyId)
    val customPreference = preferenceManager.findPreference<T>(preferenceKey)
    customPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
        val values = resources.getStringArray(valuesId)
        val valueIndex = values.indexOf(newValue)
        val entries = resources.getStringArray(entriesId)
        val entry = entries[valueIndex]
        preference.summary = requireContext().getString(summaryId, entry)
        true
    }
}

fun <T : Preference?> PreferenceFragmentCompat.showCustomSummary(preferenceKeyId: Int, summaryId: Int, defaultValueId: Int) {
    Log.d("Extension", "showCustomSummary(preferenceKeyId=$preferenceKeyId, summaryId=$summaryId, defaultValueId=$defaultValueId)")

    val preferenceKey = requireContext().getString(preferenceKeyId)
    val customPreference = preferenceManager.findPreference<T>(preferenceKey)
    val defaultValue = requireContext().getString(defaultValueId)
    customPreference!!.summary = requireContext().getString(
            summaryId,
            preferenceManager.sharedPreferences.getString(preferenceKey, defaultValue)
    )
    customPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
        preference.summary = requireContext().getString(
                summaryId,
                newValue
        )
        true
    }
}
