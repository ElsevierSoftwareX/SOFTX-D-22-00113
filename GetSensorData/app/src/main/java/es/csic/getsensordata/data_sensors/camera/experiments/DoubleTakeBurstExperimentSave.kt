package es.csic.getsensordata.data_sensors.camera.experiments

import android.content.Context
import android.media.Image
import android.util.Log
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.data_sensors.camera.ImageQueueSaver
import es.csic.getsensordata.data_sensors.camera.ImageSaver
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.BurstHandler
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SampleType
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SingleTakeHandler
import es.csic.getsensordata.preferences.Preferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ArrayBlockingQueue


class DoubleTakeBurstExperimentSave(
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
            camera.previewMode = Camera.Companion.PreviewMode.Barcode
            val burst = takeBurst(iteration)
            save(contour, SampleType.Contour.value, iteration)
            save(burst, SampleType.Barcode.value, iteration)

            onIterationComplete?.invoke(iteration)

            iteration += 1
        }

        camera.previewMode = cameraPreviewMode
        camera.restart()
        onExperimentComplete?.invoke()
    }

    override fun getSaveImageHandler(samplePrefix: String, iteration: Int): SingleTakeHandler {
        Log.d(Tag, "getSaveImageHandler(samplePrefix=$samplePrefix, iteration=$iteration)")

        val file = createFile(context, "$tag/$distance/$iteration", samplePrefix)

        return object : SingleTakeHandler {
            override fun handle(image: Image): Runnable {
                val exifData = getExifData()
                return ImageSaver(image, camera.imageFormat, file, exifData)
            }
        }
    }

    override fun getSaveBurstHandler(iteration: Int): BurstHandler {
        Log.d(Tag, "getSaveBurstHandler(iteration=$iteration)")

        return object : BurstHandler {
            override fun handle(burst: ArrayBlockingQueue<Image>): Runnable {
                val exifData = getExifData()
                return ImageQueueSaver(
                    context,
                    burst,
                    camera.imageFormat,
                    "$tag/$distance/$iteration",
                    exifData
                )
            }
        }
    }

    companion object {
        private val Tag = DoubleTakeBurstExperimentSave::class.java.simpleName
    }
}
