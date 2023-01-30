package es.csic.getsensordata.data_sensors.camera.experiments

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.os.Build
import android.util.Log
import android.view.WindowManager
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.data_sensors.camera.Orientation
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.BurstHandler
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SingleTakeHandler
import es.csic.getsensordata.preferences.Preferences
import java.io.File
import java.util.*
import java.util.concurrent.ArrayBlockingQueue


abstract class ExperimentSave(
    context: Context,
    camera: Camera,
    preferences: Preferences
) : Experiment(context, camera, preferences) {

    // Save Samples

    internal fun save(
        image: Image?,
        samplePrefix: String,
        iteration: Int
    ) {
        Log.d(Tag, "save(" +
                "image=$image, " +
                "samplePrefix=$samplePrefix, " +
                "iteration=$iteration)")

        if (image != null) {
            val saveImageHandler = getSaveImageHandler(samplePrefix, iteration)
            if (saveImageHandler != null) {
                backgroundHandler?.post(saveImageHandler.handle(image))
            } else {
                Log.d(Tag, "- saveImageHandler image cannot be null, not saving")
            }
        } else {
            Log.d(Tag, "- barcode image cannot be null, not saving")
        }
    }

    internal open fun getSaveImageHandler(
        samplePrefix: String,
        iteration: Int
    ): SingleTakeHandler? {
        Log.d(Tag, "getSaveImageHandler(" +
                "samplePrefix=$samplePrefix, " +
                "iteration=$iteration)")

        return null
    }

    internal fun save(
        burst: ArrayBlockingQueue<Image>?,
        samplePrefix: String,
        iteration: Int
    ) {
        Log.d(Tag, "saveImages(" +
                "burst=$burst, " +
                "samplePrefix=$samplePrefix, " +
                "iteration=$iteration)")

        if (burst != null) {
            val saveBurstHandler = getSaveBurstHandler(iteration)
            if (saveBurstHandler != null) {
                backgroundHandler?.post(saveBurstHandler.handle(burst))
            } else {
                Log.d(Tag, "- saveBurstHandler cannot be null, not saving")
            }
        } else {
            Log.d(Tag, "- burst cannot be null, not saving")
        }
    }

    internal open fun getSaveBurstHandler(
        iteration: Int
    ): BurstHandler? {
        Log.d(Tag, "getSaveBurstHandler(" +
                "iteration=$iteration)")

        return null
    }

    // endregion

    // region Miscellaneous

    @OptIn(ExperimentalStdlibApi::class)
    fun getExifData(): Map<String, String> {
        Log.d(Tag, "getExifData()")

        val exifData = mutableMapOf<String, String>()

        val sensorOrientation =
            camera.characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        val display =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        val mirrored =
            camera.characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val exifOrientation = Orientation.toExif(rotation + sensorOrientation, mirrored)

        val make = Build.MANUFACTURER.toLowerCase(Locale.ROOT).capitalize(Locale.ROOT)
        val model = Build.MODEL.toLowerCase(Locale.ROOT).capitalize(Locale.ROOT)
        val software = "vlips"
        val denominator = 10000
        val focalLengths =
            camera.characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focalLength = focalLengths!![0] * denominator
        val subjectDistance = (distance / 100.0)
        val sensorExposureTime = camera.sensorExposureTime * 1e-9
        val sensorSensitivity = camera.sensorSensitivity

        exifData["Orientation"] = exifOrientation.toString()
        exifData["Make"] = make
        exifData["Model"] = model
        exifData["Software"] = software
        exifData["FocalLength"] = "$focalLength/$denominator"
        exifData["SubjectDistance"] = "$subjectDistance"
        exifData["ExposureTime"] = "$sensorExposureTime"
        exifData["ISOSpeed"] = "$sensorSensitivity"
        exifData["ImageDescription"] = "Beacon Code: $beaconCode"

        return exifData.toMap()
    }

    // endregion

    companion object {
        private val Tag = ExperimentSave::class.java.simpleName

        fun createFile(
            context: Context,
            samplesOutputFolder: String,
            samplePrefix: String,
            sampleId: String = ""
        ): File {
            Log.d(Tag, "createFile(" +
                    "context=$context, " +
                    "samplesOutputFolder=$samplesOutputFolder, " +
                    "samplePrefix=$samplesOutputFolder, " +
                    "sampleId=$sampleId)")

            return if (sampleId != "") {
                File(
                    context.getExternalFilesDir(samplesOutputFolder),
                    "${samplePrefix}_${sampleId}.jpg"
                )
            } else {
                File(
                    context.getExternalFilesDir(samplesOutputFolder),
                    "${samplePrefix}.jpg"
                )
            }
        }
    }
}
