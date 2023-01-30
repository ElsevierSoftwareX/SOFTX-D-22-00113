package es.csic.getsensordata.fragments

import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import es.csic.getsensordata.R
import es.csic.getsensordata.preferences.setNumberKeyboardType
import es.csic.getsensordata.preferences.showCustomSummary


class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(Tag, "onCreatePreferences(savedInstanceState=$savedInstanceState, rootKey=$rootKey)")

        setPreferencesFromResource(R.xml.preferences, rootKey)

        showCustomSummary<ListPreference>(
                preferenceKeyId = R.string.updateFrequencyDelayTypeKey,
                summaryId = R.string.updateFrequencyDelayTypeSummary,
                valuesId = R.array.updateFrequencyDelayTypeValues,
                entriesId = R.array.updateFrequencyDelayTypeEntries
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.updateFrequencyCustomUpdateRateKey,
                summaryId = R.string.updateFrequencyCustomUpdateRateSummary,
                defaultValueId = R.string.updateFrequencyCustomUpdateRateDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.displayPreferencesFontSizeKey,
                summaryId = R.string.displayPreferencesFontSizeSummary,
                defaultValueId = R.string.displayPreferencesFontSizeDefaultValue
        )
    }

    override fun onStart() {
        Log.d(Tag, "onStart()")

        super.onStart()

        setNumberKeyboardType(R.string.updateFrequencyCustomUpdateRateKey)
        setNumberKeyboardType(R.string.displayPreferencesFontSizeKey)
        setNumberKeyboardType(
                R.string.cameraPreferencesPreviewTransparencyKey,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
    }

    override fun onResume() {
        Log.d(Tag, "onResume()")

        super.onResume()

        activity?.title = getString(R.string.preferencesTitle)
    }

    companion object {
        private val Tag = PreferencesFragment::class.java.simpleName
    }
}
