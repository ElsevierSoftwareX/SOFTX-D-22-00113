package es.csic.getsensordata.data_sensors.camera.experiments

import android.content.Context
import android.media.Image
import android.util.Log
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.data_sensors.camera.Camera.Companion.PreviewMode
import es.csic.getsensordata.data_sensors.camera.ImageSaver
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SampleType
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SingleTakeHandler
import es.csic.getsensordata.preferences.Preferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class DoubleTakeExperimentSave(
        context: Context,
        camera: Camera,
        preferences: Preferences
) : ExperimentSave(context, camera, preferences) {

    override fun start() = GlobalScope.launch {
        Log.d(Tag, "start()")

        startBackgroundHandler()

        val cameraPreviewMode = camera.previewMode
        iteration = 0
        while (keepTakingSamples()) {
            if (abort) {
                break
            }

            camera.previewMode = Camera.Companion.PreviewMode.Contour
            val contour = takeSample(iteration)
            camera.previewMode = PreviewMode.Barcode
            val barcode = takeSample(iteration)
            save(contour, SampleType.Contour.value, iteration)
            save(barcode, SampleType.Barcode.value, iteration)

            onIterationComplete?.invoke(iteration)

            iteration += 1
        }

        camera.previewMode = cameraPreviewMode
        camera.restart()
        onExperimentComplete?.invoke()
    }

    override fun getSaveImageHandler(samplePrefix: String, iteration: Int): SingleTakeHandler {
        Log.d(Tag, "getSaveImageHandler(samplePrefix=$samplePrefix, iteration=$iteration)")

        val sampleId = getSampleId(samples, iteration)
        val file = createFile(context, "$tag/$distance", samplePrefix, sampleId)

        return object : SingleTakeHandler {
            override fun handle(image: Image): Runnable {
                val exifData = getExifData()
                return ImageSaver(image, camera.imageFormat, file, exifData)
            }
        }
    }

    companion object {
        private val Tag = DoubleTakeExperimentSave::class.java.simpleName
    }
}
