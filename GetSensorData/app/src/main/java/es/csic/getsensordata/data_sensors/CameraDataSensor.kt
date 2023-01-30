package es.csic.getsensordata.data_sensors

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment
import es.csic.getsensordata.data_sensors.definition.DataSensor
import es.csic.getsensordata.data_sensors.definition.DataSensorType
import es.csic.getsensordata.preferences.Preferences
import java.util.*

class CameraDataSensor(context: Context, updateInterval: Double) : DataSensor(context, DataSensorType.Camera, updateInterval) {
    var camera = Camera(context as AppCompatActivity)
    private var preferences = Preferences(context)
    private var experiment: Experiment? = null

    override fun getPrefix(): String {
        return if (preferences.cameraEnabled) {
            "${type.prefix}${preferences.lensFacing}"
        } else {
            type.prefix
        }
    }

    override val isAvailable = isDataSensorAvailable()
    private fun isDataSensorAvailable(): Boolean {
        return preferences.cameraEnabled
    }

    override val offersExtendedStatus = false

    override fun getName(): String {
        return if (preferences.cameraEnabled) {
            val camerasFeatures = camera.getCamerasFeatures()
            var lensFacingName = ""
            for (cameraFeatures in camerasFeatures) {
                if (cameraFeatures.id == preferences.lensFacing) {
                    lensFacingName = cameraFeatures.lensFacing?.name!!
                    break
                }
            }
            lensFacingName
        } else {
            context.getString(R.string.camera_not_enabled)
        }
    }

    override fun getFeatures(): String {
        preferences = Preferences(context)
        return if (preferences.cameraEnabled) {
            val tag = preferences.tag
            val distance = preferences.distance
            val samplesOutputFolder = "$tag/$distance"
            val samplesOutputPath = context.getExternalFilesDir(samplesOutputFolder)
            val experiment = preferences.experimentType.name
            val samples = when (preferences.experimentType) {
                Experiment.Companion.ExperimentType.SingleTake -> "${preferences.samples}"
                Experiment.Companion.ExperimentType.DoubleTake -> "${preferences.samples}"
                Experiment.Companion.ExperimentType.Burst -> "${preferences.samples} (burst of ${preferences.burstSamples})"
                Experiment.Companion.ExperimentType.DoubleTakeBurst -> "${preferences.samples} (burst of ${preferences.burstSamples})"
            }
            val beaconCode = preferences.beaconCode
            val beaconMessageHeader = preferences.beaconMessageHeader
            val beaconMessageLength = preferences.beaconMessageLength

            """
                | ${context.getString(R.string.cameraFeaturesSamplesOutputPath)}: $samplesOutputPath
                | ${context.getString(R.string.cameraFeaturesExperiment)}: $experiment
                | ${context.getString(R.string.cameraFeaturesSamples)}: $samples
                | ${context.getString(R.string.beacon)}:
                | - ${context.getString(R.string.beaconCode)}: $beaconCode
                | - ${context.getString(R.string.beaconMessageHeader)}: $beaconMessageHeader
                | - ${context.getString(R.string.beaconMessageLength)}: $beaconMessageLength
            """.trimMargin()
        } else {
            context.getString(R.string.no_features)
        }
    }

    override fun getStatusForScreen(): String {
        Log.d(Tag, "getStatusForScreen()")

        counter += 1

        val timestamp = getSecondsFromEpoch()

        if (timestamp - previousSensorTimestampInSeconds > 0) {
            measurementFrequency = (0.9 * measurementFrequency + 0.1 / (timestamp - previousSensorTimestampInSeconds)).toFloat()
        } else {
            Log.e("${getPrefix()}${preferences.lensFacing}} SENSOR", "timestamp < previousTimestamp")
        }
        previousSensorTimestampInSeconds = timestamp

        return if ((timestamp - previousSecondsFromEpoch > updateInterval) && experiment != null) {
            val infinite = preferences.infinite
            val iteration = experiment!!.iteration
            if (!infinite) {
                val iterations = preferences.samples
                val templateForScreen = """
                    |   Progress: %d/%d
                    |                               Freq: %5.0f Hz
                """.trimMargin()
                previousSecondsFromEpoch = timestamp
                String.format(Locale.US, templateForScreen,
                        iteration + 1,
                        iterations,
                        measurementFrequency
                )
            } else {
                val templateForScreen = """
                    |   Progress: %d
                    |                               Freq: %5.0f Hz
                """.trimMargin()
                previousSecondsFromEpoch = timestamp
                String.format(Locale.US, templateForScreen,
                        iteration + 1,
                        measurementFrequency
                )
            }
        } else {
            ""
        }
    }

    override fun getExtendedStatusForScreen(): String {
        return ""
    }

    override fun getStatusForLog(): String = ""

    override fun connect(listener: DataSensorEventListener) {
//        TODO("Not yet implemented")
    }

    override fun disconnect() {
//        TODO("Not yet implemented")
    }

    override fun startReading() {
//        TODO("Not yet implemented")
    }

    override fun stopReading() {
//        TODO("Not yet implemented")
    }

    fun startSampling() {
        Log.d(Tag, "startSampling()")

        preferences = Preferences(context)
        val experiment = Experiment.getExperiment(
                context,
                camera,
                preferences
        )
        if (experiment != null) {
            experiment.infinite = preferences.infinite
            startExperiment(experiment, preferences)
        } else {
            Log.d(Tag, "- experiment cannot be null")
        }
    }

    private fun startExperiment(experiment: Experiment, preferences: Preferences) {
        Log.d(Tag, "startExperiment(experiment=$experiment, preferences=$preferences)")

        this.experiment = experiment
        experiment.onIterationComplete = { iteration ->
            Log.d(Tag, "Iteration $iteration complete")
//            requireActivity().runOnUiThread {
//                updateExperimentButtonText(
//                        experimentButtonTextId,
//                        iteration,
//                        preferences.experimentSamples
//                )
//            }

            Log.d(Tag, "fire listener $listener")
            listener?.onDataSensorChanged(this)
        }

        experiment.onExperimentComplete = {
            playFinishAlert()
//            experiment?.stop()
        }

        experiment.start()
    }

    fun stopExperiment() {
        Log.d(Tag, "stopExperiment()")
        experiment?.stop()
    }

    private fun playFinishAlert() {
        Log.d(Tag, "playFinishAlert()")

        if (preferences.experimentFinishAlert) {
            try {
                val notification = RingtoneManager.getActualDefaultRingtoneUri(
                        context,
                        RingtoneManager.TYPE_NOTIFICATION
                )
                val r: Ringtone = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private val Tag = CameraDataSensor::class.java.simpleName
    }
}
