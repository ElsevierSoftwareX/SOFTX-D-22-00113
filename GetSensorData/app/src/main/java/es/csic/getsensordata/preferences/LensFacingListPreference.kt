package es.csic.getsensordata.preferences

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.data_sensors.camera.CameraFeatures


class LensFacingListPreference(context: Context?, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private val camerasFeatures: List<CameraFeatures>
    private val lensFacingEntries: Array<CharSequence>
    private val lensFacingValues: Array<CharSequence>

    init {
        Log.d(Tag, "init")

        val camera = Camera(context as AppCompatActivity)
        camerasFeatures = camera.getCamerasFeatures()

        val entries = mutableListOf<CharSequence>()
        val values = mutableListOf<CharSequence>()

        for (cameraFeature in camerasFeatures) {
            val entry = "${cameraFeature.lensFacing} (Id ${cameraFeature.id})"
            val value = cameraFeature.id
            entries.add(entry)
            values.add(value)
        }

        lensFacingEntries = entries.toTypedArray()
        lensFacingValues = values.toTypedArray()

        Log.d(Tag, "- lensFacingEntries: $lensFacingEntries")
        Log.d(Tag, "- lensFacingValues: $lensFacingValues")
    }

    override fun getEntries(): Array<CharSequence> {
        Log.d(Tag, "getEntries()")

        return lensFacingEntries
    }

    override fun getEntryValues(): Array<CharSequence> {
        Log.d(Tag, "getEntryValues()")

        return lensFacingValues
    }

    override fun findIndexOfValue(value: String?): Int {
        Log.d(Tag, "findIndexOfValue(value=$value)")

        return if(value != null) {
            lensFacingValues.indexOf(value)
        } else {
            0
        }
    }

    override fun getSummary(): CharSequence {
        Log.d(Tag, "getSummary()")

        val index = lensFacingValues.indexOf(value)
        val cameraFeature = camerasFeatures[index]
        val lensFacing = cameraFeature.lensFacing?.name?.toLowerCase()
        val summaryParameter = "$lensFacing $value"

        return context.getString(R.string.cameraPreferencesLensFacingSummary, summaryParameter)
    }

    companion object {
        private val Tag = LensFacingListPreference::class.java.simpleName
    }
}
