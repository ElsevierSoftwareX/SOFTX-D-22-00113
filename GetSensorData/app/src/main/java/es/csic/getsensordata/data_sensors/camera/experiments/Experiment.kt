package es.csic.getsensordata.data_sensors.camera.experiments

import android.content.Context
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import es.csic.getsensordata.data_sensors.camera.Camera
import es.csic.getsensordata.preferences.Preferences
import kotlinx.coroutines.Job
import java.util.concurrent.ArrayBlockingQueue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


abstract class Experiment(
        val context: Context,
        val camera: Camera,
        val preferences: Preferences
) {
    protected val tag = preferences.tag
    protected val distance = preferences.distance
    protected val samples = preferences.samples
    private val burstSamples = preferences.burstSamples
    protected val beaconCode = preferences.beaconCode
    protected val beaconMessageHeader = preferences.beaconMessageHeader
    protected val beaconMessageLength = preferences.beaconMessageLength

    var iteration = 0
    var infinite = false
    protected var abort = false

    var onIterationComplete: ((Int) -> Unit)? = null
    var onExperimentComplete: (() -> Unit)? = null

    private var backgroundThread: HandlerThread? = null
    protected var backgroundHandler: Handler? = null

    abstract fun start(): Job

    fun stop() {
        Log.d(Tag, "stop()")

        if (infinite) {
            abort = true
        }

        stopBackgroundHandler()
    }

    // region Take Samples

    internal fun keepTakingSamples(): Boolean {
        Log.d(Tag, "keepTakingSamples()")

        return if (!infinite) {
            iteration += 1
            iteration >= samples
        } else {
            true
        }
    }

    internal suspend fun takeSample(iteration: Int): Image? = suspendCoroutine { continuation ->
        Log.d(Tag, "takeSample(iteration=$iteration)")

        camera.onPictureTaken = { image ->
            continuation.resume(image)
        }
        camera.takePicture()
    }

    internal suspend fun takeBurst(iteration: Int): ArrayBlockingQueue<Image> =
            suspendCoroutine { continuation ->
                Log.d(Tag, "takeBurst(iteration=$iteration)")

                camera.burstLength = burstSamples
                camera.onPictureBurstTaken = { imageQueue ->
                    continuation.resume(imageQueue)
                }
                camera.takePicture(burstMode = true)
            }

    // endregion

    // region Background Handler

    internal fun startBackgroundHandler() {
        Log.d(Tag, "startBackgroundHandler()")

        if (backgroundThread != null) {
            Log.d(Tag, "- background handler already exists")
            return
        }

        backgroundThread = HandlerThread("$Tag Background Handler").also { handlerThread ->
            handlerThread.start()
            backgroundHandler = Handler(handlerThread.looper)
        }
    }

    private fun stopBackgroundHandler() {
        Log.d(Tag, "stopBackgroundHandler()")

        backgroundThread?.quitSafely()

        try {
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(Tag, "- stop background handler error $e")
        }
    }

    // endregion

    companion object {
        private val Tag = Experiment::class.java.simpleName

        enum class ExperimentType(val value: Int) {
            SingleTake(0),
            DoubleTake(1),
            Burst(2),
            DoubleTakeBurst(3)
        }

        enum class SampleType(val value: String) {
            Barcode("barcode"),
            Contour("contour")
        }

        enum class ExperimentAction(val value: Int) {
            Save(0),
            Decode(1)
        }

        internal interface SingleTakeHandler {
            fun handle(image: Image): Runnable
        }

        internal interface DoubleTakeHandler {
            fun handle(contour: Image, barcode: Image): Runnable
        }

        internal interface BurstHandler {
            fun handle(burst: ArrayBlockingQueue<Image>): Runnable
        }

        internal interface DoubleTakeBurstHandler {
            fun handle(contour: Image, burst: ArrayBlockingQueue<Image>): Runnable
        }

        fun getExperiment(
                context: Context,
                camera: Camera,
                preferences: Preferences
        ): Experiment? {
            Log.d(Tag, "getExperiment(" +
                    "context=$context, " +
                    "camera=$camera, " +
                    "preferences=$preferences)")

            return when (preferences.experimentType) {
                ExperimentType.SingleTake ->
                    SingleTakeExperimentSave(
                            context,
                            camera,
                            preferences
                    )
                ExperimentType.DoubleTake ->
                    DoubleTakeExperimentSave(
                            context,
                            camera,
                            preferences
                    )
                ExperimentType.Burst ->
                    BurstExperimentSave(
                            context,
                            camera,
                            preferences
                    )
                ExperimentType.DoubleTakeBurst ->
                    DoubleTakeBurstExperimentSave(
                            context,
                            camera,
                            preferences
                    )
            }
        }

        fun getSampleId(samples: Int, sampleCounter: Int): String {
            Log.d(Tag, "getSampleId(" +
                    "samples=$samples, " +
                    "sampleCounter=$sampleCounter)")

            val samplesAsString = (samples - 1).toString()
            return sampleCounter.toString().padStart(samplesAsString.length, '0')
        }
    }
}
