package es.csic.getsensordata.data_sensors.camera

import android.content.Context
import android.media.Image
import android.util.Log
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SampleType
import es.csic.getsensordata.data_sensors.camera.experiments.Experiment.Companion.SingleTakeHandler
import es.csic.getsensordata.data_sensors.camera.experiments.ExperimentSave
import java.util.concurrent.ArrayBlockingQueue


internal class ImageQueueSaver(
        private val context: Context,
        private val imageQueue: ArrayBlockingQueue<Image>,
        private val imageFormat: Int,
        private val folder: String,
        private val exifData: Map<String, String>
) : Runnable {

    override fun run() {
        Log.d(Tag, "run()")

        for ((index, image) in imageQueue.withIndex()) {
            val sampleId = Experiment.getSampleId(imageQueue.size, index)
            val file = ExperimentSave.createFile(context, folder, SampleType.Barcode.value, sampleId)

            val singleTakeHandler = object : SingleTakeHandler {
                override fun handle(image: Image): Runnable {
                    return ImageSaver(image, imageFormat, file, exifData)
                }
            }
            singleTakeHandler.handle(image).run()
        }
    }

    companion object {
        private val Tag = ImageQueueSaver::class.java.simpleName
    }
}
