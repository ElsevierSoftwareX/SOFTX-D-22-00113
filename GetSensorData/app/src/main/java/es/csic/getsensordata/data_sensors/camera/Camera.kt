package es.csic.getsensordata.data_sensors.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.camera.CameraFeatures.Companion.LensFacing
import es.csic.getsensordata.data_sensors.camera.CameraFeatures.Companion.SupportedHardwareLevel
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class Camera constructor(private val activity: AppCompatActivity) {
    lateinit var preview: Preview
    private val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId = "0"
    val characteristics = manager.getCameraCharacteristics(cameraId)

    var imageFormat = ImageFormat.JPEG
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var focusListener: OnFocusListener? = null

    private val openLock = Semaphore(1)
    private var device: CameraDevice? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var isClosed = true
    private var previewSurface: Surface? = null
    private var state = State.Preview
    private var previousAutoFocusState: Int? = null
    var sensorExposureTime = 0L
    var sensorSensitivity = 0

    private var burstMode = false
    var burstLength = 10

    var previewMode = PreviewMode.Default
        set(value) {
            if (value != PreviewMode.Default) {
                characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    ?: throw RuntimeException("Camera's sensor exposure time cannot be adjusted")

                characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                    ?: throw RuntimeException("Camera's sensor sensitivity cannot be adjusted")
            }

            field = value
        }

    var onCameraOpened: (() -> Unit)? = null
    var onPictureTaken: ((image: Image) -> Unit)? = null
    var onPictureBurstTaken: ((imageQueue: ArrayBlockingQueue<Image>) -> Unit)? = null

    // region Callbacks

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d(Tag, "CameraDevice.StateCallback.onOpened(cameraDevice=$cameraDevice)")

            device = cameraDevice
            openLock.release()
            isClosed = false
            onCameraOpened?.invoke()
        }

        override fun onClosed(cameraDevice: CameraDevice) {
            Log.d(Tag, "CameraDevice.StateCallback.onClosed(cameraDevice=$cameraDevice)")

            isClosed = true
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.d(Tag, "CameraDevice.StateCallback.onDisconnected(cameraDevice=$cameraDevice)")

            openLock.release()
            cameraDevice.close()
            device = null
            isClosed = true
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Log.d(
                Tag,
                "CameraDevice.StateCallback.onError(cameraDevice=$cameraDevice, error=$error)"
            )

            openLock.release()
            cameraDevice.close()
            device = null
            isClosed = true
        }
    }

    private val captureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d(Tag, "CameraCaptureSession.StateCallback.onConfigureFailed(session=$session)")

            throw RuntimeException("Configuration failed during capture session creation")
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(Tag, "CameraCaptureSession.StateCallback.onConfigured(session=$session)")

            if (isClosed) {
                return
            }

            captureSession = session
            startPreview()
        }
    }

    private val captureSessionCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {
            Log.v(Tag, "process(result=$result)")

            when (state) {
                State.Preview -> {
                    Log.v(Tag, "- State.PREVIEW")

                    val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
                    if (autoFocusState == previousAutoFocusState) {
                        return
                    }
                    previousAutoFocusState = autoFocusState
                    focusListener?.onFocusStateChanged(autoFocusState)
                }

                State.WaitingLock -> {
                    Log.d(Tag, "- State.WAITING_LOCK")

                    val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (autoFocusState == null) {
                        runPreCapture()
                    } else if (
                        autoFocusState == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                        autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        autoFocusState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                    ) {
                        val autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (autoExposureState == null || autoExposureState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            capture()
                        } else {
                            runPreCapture()
                        }
                    } else {
                        capture()
                    }
                }

                State.WaitingPrecapture -> {
                    Log.d(Tag, "- State.WAITING_PRECAPTURE")

                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null
                        || aeState == CaptureRequest.CONTROL_AE_STATE_PRECAPTURE
                        || aeState == CaptureRequest.CONTROL_AE_STATE_CONVERGED
                    ) {
                        state = State.WaitingNonPrecapture
                    }
                }

                State.WaitingNonPrecapture -> {
                    Log.d(Tag, "- State.WAITING_NON_PRECAPTURE")

                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureRequest.CONTROL_AE_STATE_PRECAPTURE) {
                        capture()
                    }
                }
                else -> {
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            Log.d(
                Tag,
                "CameraCaptureSession.CaptureCallback.onCaptureProgressed(session=$session, request=$request, partialResult=$partialResult)"
            )

            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            Log.v(
                Tag,
                "CameraCaptureSession.CaptureCallback.onCaptureCompleted(session=$session, request=$request, result=$result)"
            )

            process(result)
        }
    }

    // endregion

    // region public

    fun open() {
        Log.d(Tag, "open()")

        try {
            if (!openLock.tryAcquire(3L, TimeUnit.SECONDS)) {
                throw IllegalStateException("Camera launch failed")
            }

            if (device != null) {
                openLock.release()
                return
            }

            startBackgroundHandler()

            manager.openCamera(cameraId, deviceStateCallback, backgroundHandler)
        } catch (e: SecurityException) {

        }
    }

    fun close() {
        Log.d(Tag, "close()")

        try {
            if (openLock.tryAcquire(3, TimeUnit.SECONDS)) {
                isClosed = true
            }

            if (this::preview.isInitialized) {
                preview.close()
                val fragmentManager = activity.supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.remove(preview)
                fragmentTransaction.commit()
            }

            captureSession?.close()
            captureSession = null

            device?.close()
            device = null

            previewSurface?.release()
            previewSurface = null

            imageReader?.close()
            imageReader = null

            stopBackgroundHandler()
        } catch (e: InterruptedException) {
            Log.e(Tag, "Error closing camera $e")
        } finally {
            openLock.release()
        }
    }

    fun start(
        imageFormat: Int,
        cameraLocation: String,
        location: Preview.Location,
        showPreview: Boolean,
        showGuides: Boolean
    ) {
        Log.d(
            Tag,
            "start(imageFormat=$imageFormat, cameraLocation=$cameraLocation, location=$location, showPreview=$showPreview, showGuides=$showGuides)"
        )

        this.imageFormat = imageFormat
        cameraId = cameraLocation

        val fragmentManager = activity.supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()

        preview = Preview(activity, this, location, showPreview, showGuides)
        val arguments = Bundle()
        arguments.putBoolean("shouldYouCreateAChildFragment", true)
        preview.arguments = arguments
        fragmentTransaction.add(R.id.cameraPreviewFragmentContainer, preview)
        fragmentTransaction.commit()
    }

    fun start(previewSurface: Surface) {
        Log.d(Tag, "start(previewSurface=$previewSurface)")

        this.previewSurface = previewSurface
        val size = characteristics.getCaptureSize(CompareSizesByArea())
        imageReader = ImageReader.newInstance(size.width, size.height, imageFormat, ImageBufferSize)

        val surfaces = listOf(previewSurface, imageReader?.surface)

        device?.createCaptureSession(
            surfaces,
            captureSessionStateCallback,
            backgroundHandler
        )
    }

    fun takePicture(burstMode: Boolean = false) {
        Log.d(Tag, "takePicture(burstMode=$burstMode)")

        this.burstMode = burstMode

        if (device == null) {
            throw IllegalStateException("Camera device not ready")
        }

        if (isClosed) {
            Log.d(Tag, "- camera is closed")
            return
        }

        lockFocus()
    }

    fun restart() {
        preview.restart()
    }

    fun getCaptureSize(): Size {
        Log.d(Tag, "getCaptureSize()")

        return characteristics.getCaptureSize(CompareSizesByArea())
    }

    fun getSensorOrientation(): Int {
        Log.d(Tag, "getSensorOrientation()")

        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    fun chooseOptimalSize(
        textureViewWidth: Int,
        textureViewHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        aspectRatio: Size
    ): Size {
        Log.d(
            Tag,
            "chooseOptimalSize(textureViewWidth=$textureViewWidth, textureViewHeight=$textureViewHeight, maxWidth=$maxWidth, maxHeight=$maxHeight, aspectRatio=$aspectRatio)"
        )

        return characteristics.chooseOptimalSize(
            textureViewWidth,
            textureViewHeight,
            maxWidth,
            maxHeight,
            aspectRatio
        )
    }

    // endregion

    // region Get cameras features

    fun getCamerasFeatures(): List<CameraFeatures> {
        Log.d(Tag, "getCameraIdList()")

        val camerasFeatures = mutableListOf<CameraFeatures>()
        val cameraIdList = manager.cameraIdList
        for (cameraId in cameraIdList) {
            val cameraCharacteristics = manager.getCameraCharacteristics(cameraId)

            val infoSupportedHardwareLevelId = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val lensFacingId = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

            val supportedHardwareLevel = CameraFeatures.getEnum<SupportedHardwareLevel>(infoSupportedHardwareLevelId)
            val lensFacing = CameraFeatures.getEnum<LensFacing>(lensFacingId)

            val cameraFeatures = CameraFeatures(
                cameraId,
                supportedHardwareLevel,
                lensFacing
            )

            camerasFeatures.add(cameraFeatures)
        }

        return camerasFeatures.toList()
    }

    // endregion

    // region private

    private fun startBackgroundHandler() {
        Log.d(Tag, "startBackgroundHandler()")

        if (backgroundThread != null) {
            return
        }

        backgroundThread = HandlerThread("Camera-$cameraId").also { handlerThread ->
            handlerThread.start()
            backgroundHandler = Handler(handlerThread.looper)
        }
    }

    private fun stopBackgroundHandler() {
        Log.d(Tag, "stopBackgroundHandler()")

        backgroundThread?.quitSafely()

        try {
            // TODO: investigate why thread does not end when join is called
            // backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(Tag, "- stop background handler error $e")
        }
    }

    private fun startPreview() {
        Log.d(Tag, "startPreview()")

        try {
            if (!openLock.tryAcquire(1L, TimeUnit.SECONDS)) {
                return
            }

            if (isClosed) {
                return
            }

            state = State.Preview
            val builder = createPreviewRequestBuilder()
            builder?.build()?.let { captureRequest ->
                captureSession?.setRepeatingRequest(
                    captureRequest,
                    captureSessionCaptureCallback,
                    backgroundHandler
                )
            }
        } catch (e1: IllegalStateException) {

        } catch (e2: CameraAccessException) {

        } catch (e3: InterruptedException) {

        } finally {
            openLock.release()
        }
    }

    @Throws(CameraAccessException::class)
    private fun createPreviewRequestBuilder(): CaptureRequest.Builder? {
        Log.d(Tag, "createPreviewRequestBuilder()")

        val builder = device?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewSurface?.let { surface ->
            builder?.addTarget(surface)
        }
        enableDefaultModes(builder)

        return builder
    }

    private fun enableDefaultModes(builder: CaptureRequest.Builder?) {
        Log.d(Tag, "enableDefaultModes(builder=$builder)")

        if (builder == null) {
            return
        }

        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        if (characteristics.isContinuousAutoFocusSupported()) {
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        } else {
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
        }

        if (characteristics.isAutoExposureSupported(CaptureRequest.CONTROL_AE_MODE_ON)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (characteristics.isAutoWhiteBalanceSupported()) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        when (previewMode) {
            PreviewMode.Default -> {

            }
            PreviewMode.Barcode -> {
                setCameraCharacteristicsBarcode(characteristics, builder)
            }
            PreviewMode.Contour -> {
                setCameraCharacteristicsContour(characteristics, builder)
            }
        }

        builder.set(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )
    }

    private fun setCameraCharacteristicsBarcode(
        cameraCharacteristics: CameraCharacteristics,
        captureRequestBuilder: CaptureRequest.Builder
    ) {
        Log.d(
            Tag,
            "setCameraCharacteristicsBarcode(cameraCharacteristics=$cameraCharacteristics, captureRequestBuilder=$captureRequestBuilder)"
        )

        if (isContinuousAutoFocusSupported(cameraCharacteristics)) {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        } else {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
        }
        if (isAutoWhiteBalanceSupported(cameraCharacteristics)) {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
        }
        captureRequestBuilder.set(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)

        sensorExposureTime = getSensorExposureTimeForBarcode()
        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sensorExposureTime)

        sensorSensitivity = getSensorSensitivityForBarcode()
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity)
    }

    private fun setCameraCharacteristicsContour(
        cameraCharacteristics: CameraCharacteristics,
        captureRequestBuilder: CaptureRequest.Builder
    ) {
        Log.d(
            Tag,
            "setCameraCharacteristicsContour(cameraCharacteristics=$cameraCharacteristics, captureRequestBuilder=$captureRequestBuilder)"
        )

        if (isContinuousAutoFocusSupported(cameraCharacteristics)) {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        } else {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
        }
        if (isAutoWhiteBalanceSupported(cameraCharacteristics)) {
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
        }
        captureRequestBuilder.set(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)

        sensorExposureTime = getSensorExposureTimeForContour()
        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sensorExposureTime)

        sensorSensitivity = getSensorSensitivityForContour()
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity)
    }

    private fun getSensorExposureTimeForBarcode(): Long {
        Log.d(Tag, "getSensorExposureTimeForBarcode()")

        var sensorExposureTime = 0L

        val make = Build.MANUFACTURER
        val model = Build.MODEL
        Log.d(Tag, "- $make $model")

        if (make == DeviceMake.Huawei.value && model == DeviceModel.Elel29.value) {
            sensorExposureTime = (1.0 / 10000 * 1e+9).toLong()
        } else {
            val sensorExposureTimeRange =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (sensorExposureTimeRange != null) {
                sensorExposureTime = sensorExposureTimeRange.lower
            }
        }

        return sensorExposureTime
    }

    private fun getSensorSensitivityForBarcode(): Int {
        Log.d(Tag, "getSensorSensitivityForBarcode()")

        var sensorSensitivity = 0

        val make = Build.MANUFACTURER
        val model = Build.MODEL
        Log.d(Tag, "- $make $model")

        if (make == DeviceMake.Huawei.value && model == DeviceModel.Elel29.value) {
            sensorSensitivity = 50
        } else {
            val sensorSensitivityRange =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            if (sensorSensitivityRange != null) {
                sensorSensitivity = sensorSensitivityRange.upper
            }
        }

        return sensorSensitivity
    }

    private fun getSensorExposureTimeForContour(): Long {
        Log.d(Tag, "getSensorExposureTimeForContour()")

        var sensorExposureTime = 0L

        val sensorExposureTimeRange =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        if (sensorExposureTimeRange != null) {
            sensorExposureTime = (0.01 * 1e9).toLong()
        }

        return sensorExposureTime
    }

    private fun getSensorSensitivityForContour(): Int {
        Log.d(Tag, "getSensorSensitivityContour()")

        var sensorSensitivity = 0

        val sensorSensitivityRange =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        if (sensorSensitivityRange != null) {
            sensorSensitivity = sensorSensitivityRange.lower
        }

        return sensorSensitivity
    }

    private fun isContinuousAutoFocusSupported(cameraCharacteristics: CameraCharacteristics): Boolean {
        Log.d(Tag, "isContinuousAutoFocusSupported(cameraCharacteristics=$cameraCharacteristics)")
        var result = false
        val modes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (modes != null) {
            for (mode in modes) {
                if (mode == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                    result = true
                    break
                }
            }
        }
        return result
    }

    private fun isAutoWhiteBalanceSupported(cameraCharacteristics: CameraCharacteristics): Boolean {
        Log.d(Tag, "isAutoWhiteBalanceSupported(cameraCharacteristics=$cameraCharacteristics)")
        var result = false
        val modes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        if (modes != null) {
            for (mode in modes) {
                if (mode == CameraCharacteristics.CONTROL_AWB_MODE_AUTO) {
                    result = true
                    break
                }
            }
        }
        return result
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        Log.d(Tag, "lockFocus()")

        try {
            state = State.WaitingLock

            val builder = createPreviewRequestBuilder()

            if (!characteristics.isContinuousAutoFocusSupported()) {
                builder?.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START
                )
            }
            builder?.build()?.let {
                captureSession?.capture(it, captureSessionCaptureCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(Tag, "$e")
        }
    }

    private fun runPreCapture() {
        Log.d(Tag, "runPreCapture()")

        try {
            state = State.WaitingPrecapture

            val builder = createPreviewRequestBuilder()

            builder?.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            builder?.build()?.let {
                captureSession?.capture(it, captureSessionCaptureCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(Tag, "$e")
        }
    }

    private fun capture() {
        Log.d(Tag, "capture()")

        if (!burstMode) {
            capturePicture()
        } else {
            capturePictureBurst()
        }
    }

    private fun capturePicture() {
        Log.d(Tag, "capturePicture()")

        state = State.Taken
        try {
            imageReader?.setOnImageAvailableListener({ reader ->
                Log.d(Tag, "onImageAvailableListener()")

                reader.setOnImageAvailableListener(null, null)
                val image = reader.acquireNextImage()
                Log.d(Tag, "onImageAvailable()")

                onPictureTaken?.invoke(image)
            }, backgroundHandler)

            val builder = device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            enableDefaultModes(builder)
            imageReader?.surface?.let { surface ->
                builder?.addTarget(surface)
            }
            previewSurface?.let { surface ->
                builder?.addTarget(surface)
            }
            captureSession?.stopRepeating()
            builder?.build()?.let { captureRequest ->
                captureSession?.capture(
                    captureRequest,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            Log.d(Tag, "CameraCaptureSession.CaptureCallback.onCaptureCompleted")
                        }
                    },
                    backgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(Tag, "$e")
        }
    }

    private fun capturePictureBurst() {
        Log.d(Tag, "capturePictureBurst()")

        state = State.Taken
        try {
            val imageQueue = ArrayBlockingQueue<Image>(ImageBufferSize)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                Log.d(Tag, "- image available in queue: ${image.timestamp}")
                imageQueue.add(image)
                Log.d(Tag, "- imageQueue.size: ${imageQueue.size}")
                if (imageQueue.size == burstLength) {
                    onPictureBurstTaken?.invoke(imageQueue)
                }
            }, backgroundHandler)

            val captureRequestList: MutableList<CaptureRequest> = ArrayList()
            for (i in 0 until burstLength) {
                val captureRequest =
                    captureSession?.device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        ?.apply { imageReader?.surface?.let { addTarget(it) } }
                setCameraCharacteristicsBarcode(characteristics, captureRequest!!)
                captureRequestList.add(captureRequest.build())
            }

            val builder = device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            enableDefaultModes(builder)
            imageReader?.surface?.let { surface ->
                builder?.addTarget(surface)
            }
            previewSurface?.let { surface ->
                builder?.addTarget(surface)
            }
            captureSession?.stopRepeating()
            captureSession?.captureBurst(
                captureRequestList,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d(Tag, "CameraCaptureSession.CaptureCallback.onCaptureCompleted")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(Tag, "$e")
        }
    }

    // endregion

    companion object {
        private val Tag = Camera::class.java.simpleName

        private enum class DeviceMake(val value: String) {
            Huawei("HUAWEI")
        }

        private enum class DeviceModel(val value: String) {
            Elel29("ELE-L29")
        }

        private enum class State {
            Preview,
            WaitingLock,
            WaitingPrecapture,
            WaitingNonPrecapture,
            Taken
        }

        enum class PreviewMode {
            Default,
            Barcode,
            Contour
        }

        interface OnFocusListener {
            fun onFocusStateChanged(focusState: Int)
        }

        private const val ImageBufferSize = 25
    }
}
