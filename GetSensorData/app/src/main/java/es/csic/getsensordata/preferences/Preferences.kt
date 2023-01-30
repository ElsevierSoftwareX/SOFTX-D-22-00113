package es.csic.getsensordata.preferences

import android.content.Context
import android.util.Log
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.camera.Preview
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.ExperimentType


class Preferences {
    val cameraEnabled: Boolean
    val lensFacing: String
    val tag: String
    val distance: Int
    val samples: Int
    val infinite: Boolean
    val burstSamples: Int
    val beaconCode: Int
    val beaconMessageHeader: String
    val beaconMessageLength: Int
    val experimentType: ExperimentType
    val experimentFinishAlert: Boolean
    val previewEnabled: Boolean
    val previewLocation: Preview.Location
    val previewTransparency: Float
    val showGuides: Boolean

    constructor(context: Context) {
        Log.d(Tag, "constructor(context=$context)")

        val preferenceLoader = PreferenceLoader(context)

        cameraEnabled = preferenceLoader.loadBoolean(
                R.string.cameraPreferencesCameraEnabledKey,
                R.string.cameraPreferencesCameraEnabledDefaultValue
        )
        lensFacing = preferenceLoader.loadString(
                R.string.cameraPreferencesLensFacingKey,
                R.string.cameraPreferencesLensFacingDefaultValue
        )
        tag = preferenceLoader.loadString(
                R.string.cameraPreferencesSamplingPreferencesTagKey,
                R.string.cameraPreferencesSamplingPreferencesTagDefaultValue
        )
        distance = preferenceLoader.loadInt(
                R.string.cameraPreferencesSamplingPreferencesDistanceKey,
                R.string.cameraPreferencesSamplingPreferencesDistanceDefaultValue
        )
        samples = preferenceLoader.loadInt(
                R.string.cameraPreferencesSamplingPreferencesSamplesKey,
                R.string.cameraPreferencesSamplingPreferencesSamplesDefaultValue
        )
        infinite = preferenceLoader.loadBoolean(
                R.string.cameraPreferencesSamplingPreferencesInfiniteKey,
                R.string.cameraPreferencesSamplingPreferencesInfiniteDefaultValue
        )
        burstSamples = preferenceLoader.loadInt(
                R.string.cameraPreferencesSamplingPreferencesBurstSamplesKey,
                R.string.cameraPreferencesSamplingPreferencesBurstSamplesDefaultValue
        )
        beaconCode = preferenceLoader.loadInt(
                R.string.cameraPreferencesBeaconPreferencesCodeKey,
                R.string.cameraPreferencesBeaconPreferencesCodeDefaultValue
        )
        beaconMessageHeader = preferenceLoader.loadString(
                R.string.cameraPreferencesBeaconPreferencesMessageHeaderKey,
                R.string.cameraPreferencesBeaconPreferencesMessageHeaderDefaultValue
        )
        beaconMessageLength = preferenceLoader.loadInt(
                R.string.cameraPreferencesBeaconPreferencesMessageLengthKey,
                R.string.cameraPreferencesBeaconPreferencesMessageLengthDefaultValue
        )
        experimentType = preferenceLoader.loadEnum(
                R.string.cameraPreferencesSamplingPreferencesExperimentKey,
                R.string.cameraPreferencesSamplingPreferencesExperimentDefaultValue
        )
        experimentFinishAlert = preferenceLoader.loadBoolean(
                R.string.cameraPreferencesSamplingPreferencesFinishAlertKey,
                R.string.cameraPreferencesSamplingPreferencesFinishAlertDefaultValue
        )
        previewEnabled = preferenceLoader.loadBoolean(
                R.string.cameraPreferencesPreviewEnabledKey,
                R.string.cameraPreferencesPreviewEnabledDefaultValue
        )
        previewLocation = preferenceLoader.loadEnum(
                R.string.cameraPreferencesPreviewLocationKey,
                R.string.cameraPreferencesPreviewLocationDefaultValue
        )
        previewTransparency = preferenceLoader.loadFloat(
                R.string.cameraPreferencesPreviewTransparencyKey,
                R.string.cameraPreferencesPreviewTransparencyDefaultValue
        )
        showGuides = preferenceLoader.loadBoolean(
                R.string.cameraPreferencesPreviewShowGuidesKey,
                R.string.cameraPreferencesPreviewShowGuidesDefaultValue
        )
    }

    constructor(preferences: Preferences) {
        Log.d(Tag, "constructor(preferences=$preferences)")

        cameraEnabled = preferences.cameraEnabled
        lensFacing = preferences.lensFacing
        tag = preferences.tag
        distance = preferences.distance
        infinite = preferences.infinite
        samples = preferences.samples
        burstSamples = preferences.burstSamples
        beaconCode = preferences.beaconCode
        beaconMessageHeader = preferences.beaconMessageHeader
        beaconMessageLength = preferences.beaconMessageLength
        experimentType = preferences.experimentType
        experimentFinishAlert = preferences.experimentFinishAlert
        previewEnabled = preferences.previewEnabled
        previewLocation = preferences.previewLocation
        previewTransparency = preferences.previewTransparency
        showGuides = preferences.showGuides
    }

    companion object {
        private val Tag = Preferences::class.java.simpleName
    }
}
