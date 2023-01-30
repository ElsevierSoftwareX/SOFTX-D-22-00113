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

class CameraPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(Tag, "onCreatePreferences(savedInstanceState=$savedInstanceState, rootKey=$rootKey)")

        setPreferencesFromResource(R.xml.preferences_camera, rootKey)

        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesSamplingPreferencesTagKey,
                summaryId = R.string.cameraPreferencesSamplingPreferencesTagSummary,
                defaultValueId = R.string.cameraPreferencesSamplingPreferencesTagDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesSamplingPreferencesDistanceKey,
                summaryId = R.string.cameraPreferencesSamplingPreferencesDistanceSummary,
                defaultValueId = R.string.cameraPreferencesSamplingPreferencesDistanceDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesSamplingPreferencesSamplesKey,
                summaryId = R.string.cameraPreferencesSamplingPreferencesSamplesSummary,
                defaultValueId = R.string.cameraPreferencesSamplingPreferencesSamplesDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesSamplingPreferencesBurstSamplesKey,
                summaryId = R.string.cameraPreferencesSamplingPreferencesBurstSamplesSummary,
                defaultValueId = R.string.cameraPreferencesSamplingPreferencesBurstSamplesDefaultValue
        )
        showCustomSummary<ListPreference>(
                preferenceKeyId = R.string.cameraPreferencesSamplingPreferencesExperimentKey,
                summaryId = R.string.cameraPreferencesSamplingPreferencesExperimentSummary,
                valuesId = R.array.cameraPreferencesSamplingPreferencesExperimentValues,
                entriesId = R.array.cameraPreferencesSamplingPreferencesExperimentEntries
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesBeaconPreferencesCodeKey,
                summaryId = R.string.cameraPreferencesBeaconPreferencesCodeSummary,
                defaultValueId = R.string.cameraPreferencesBeaconPreferencesCodeDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesBeaconPreferencesMessageHeaderKey,
                summaryId = R.string.cameraPreferencesBeaconPreferencesMessageHeaderSummary,
                defaultValueId = R.string.cameraPreferencesBeaconPreferencesMessageHeaderDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesBeaconPreferencesMessageLengthKey,
                summaryId = R.string.cameraPreferencesBeaconPreferencesMessageLengthSummary,
                defaultValueId = R.string.cameraPreferencesBeaconPreferencesMessageLengthDefaultValue
        )
        showCustomSummary<EditTextPreference>(
                preferenceKeyId = R.string.cameraPreferencesPreviewTransparencyKey,
                summaryId = R.string.cameraPreferencesPreviewTransparencySummary,
                defaultValueId = R.string.cameraPreferencesPreviewTransparencyDefaultValue
        )
    }

    override fun onStart() {
        Log.d(Tag, "onStart()")

        super.onStart()

        setNumberKeyboardType(R.string.cameraPreferencesSamplingPreferencesDistanceKey)
        setNumberKeyboardType(R.string.cameraPreferencesSamplingPreferencesSamplesKey)
        setNumberKeyboardType(R.string.cameraPreferencesBeaconPreferencesCodeKey)
        setNumberKeyboardType(R.string.cameraPreferencesBeaconPreferencesMessageHeaderKey)
        setNumberKeyboardType(R.string.cameraPreferencesBeaconPreferencesMessageLengthKey)
        setNumberKeyboardType(
                R.string.cameraPreferencesPreviewTransparencyKey,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
    }

    override fun onResume() {
        Log.d(Tag, "onResume()")

        super.onResume()

        activity?.title = getString(R.string.cameraPreferencesTitle)
    }

    companion object {
        private val Tag = CameraPreferencesFragment::class.java.simpleName
    }
}
