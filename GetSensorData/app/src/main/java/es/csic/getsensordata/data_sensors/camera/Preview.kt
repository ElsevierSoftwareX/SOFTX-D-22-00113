package es.csic.getsensordata.data_sensors.camera

import android.app.Activity
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import es.csic.getsensordata.R
import es.csic.getsensordata.databinding.FragmentPreviewBinding
import kotlin.math.max


class Preview(
        private val activity: Activity,
        private val camera: Camera,
        private val location: Location,
        private val showPreview: Boolean,
        private val showGuides: Boolean
) : Fragment(), Orientation.Listener {

    enum class Location(val value: Int) {
        Fit(0),
        TopLeft(1),
        TopRight(2),
        BottomLeft(3),
        BottomRight(4)
    }

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(Tag, "onSurfaceTextureAvailable(texture=$texture, width=$width, height=$height)")

            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(
                    Tag,
                    "onSurfaceTextureSizeChanged(texture=$texture, width=$width, height=$height)"
            )

            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            Log.d(Tag, "onSurfaceTextureDestroyed(texture=$texture)")

            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
//            Log.v(Tag, "onSurfaceTextureUpdated(texture=$texture)")
        }
    }

    private lateinit var textureView: AutoFitTextureView
    private lateinit var previewSize: Size
    private lateinit var orientation: Orientation

    var alpha = 1.0f
        set(value) {
            field = when {
                value < 0 -> 0f
                value > 1 -> 1f
                else -> value
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(
            Tag,
            "onCreateView(inflater=$inflater, container=$container, savedInstanceState=$savedInstanceState)"
        )

        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(Tag, "onViewCreated(view=$view, savedInstanceState=$savedInstanceState)")

        super.onViewCreated(view, savedInstanceState)

        textureView = view.findViewById(R.id.autoFitTextureView)
        orientation = Orientation(requireActivity())
    }

    override fun onResume() {
        Log.d(Tag, "onResume()")

        super.onResume()

        updateLocation()

        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }

        if (showPreview) {
            textureView.alpha = alpha
            manageGuides(showGuides)
        } else {
            textureView.alpha = 0f
            manageGuides(false)
        }
    }

    fun close() {
        Log.d(Tag, "close()")

        textureView.surfaceTextureListener = null
        orientation.stopListening()
    }

    private fun updateLocation() {
        Log.d(Tag, "updateLocation()")

        val previewContainerConstraintLayout: ConstraintLayout =
                activity.findViewById(R.id.previewContainerConstraintLayout)
        val previewConstraintLayout: ConstraintLayout =
                activity.findViewById(R.id.previewConstraintLayout)

        val constraintSet = ConstraintSet()
        constraintSet.clone(previewContainerConstraintLayout)

        constraintSet.constrainWidth(previewConstraintLayout.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constrainHeight(previewConstraintLayout.id, ConstraintSet.WRAP_CONTENT)

        when (location) {
            Location.TopLeft -> {
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.BOTTOM)
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.END)
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        16
                )
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        16
                )
                constraintSet.constrainPercentWidth(previewConstraintLayout.id, 0.25F)
                constraintSet.constrainPercentHeight(previewConstraintLayout.id, 0.25F)
            }
            Location.TopRight -> {
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.BOTTOM)
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END,
                        16
                )
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.START)
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        16
                )
                constraintSet.constrainPercentWidth(previewConstraintLayout.id, 0.25F)
                constraintSet.constrainPercentHeight(previewConstraintLayout.id, 0.25F)
            }
            Location.BottomLeft -> {
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                        16
                )
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.END)
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        16
                )
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.TOP)
                constraintSet.constrainPercentWidth(previewConstraintLayout.id, 0.25F)
                constraintSet.constrainPercentHeight(previewConstraintLayout.id, 0.25F)
            }
            Location.BottomRight -> {
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                        16
                )
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END,
                        16
                )
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.START)
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.TOP)
                constraintSet.constrainPercentWidth(previewConstraintLayout.id, 0.25F)
                constraintSet.constrainPercentHeight(previewConstraintLayout.id, 0.25F)
            }
            Location.Fit -> {
                constraintSet.clear(previewConstraintLayout.id, ConstraintSet.BOTTOM)
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END
                )
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START
                )
                constraintSet.connect(
                        previewConstraintLayout.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP
                )
            }
        }

        constraintSet.applyTo(previewContainerConstraintLayout)
    }

    private fun openCamera(width: Int, height: Int) {
        Log.d(Tag, "openCamera(width=$width, height=$height)")

        try {
            configureCameraOutputs(width, height, camera)
            configureTransform(width, height)
            camera.onCameraOpened = {
                Log.d(Tag, "- onCameraOpened()")
                val surfaceTexture = textureView.surfaceTexture
                surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                camera.start(Surface(surfaceTexture))
            }
            camera.open()
        } catch (e: CameraAccessException) {
            Log.e(Tag, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun configureCameraOutputs(width: Int, height: Int, camera: Camera) {
        Log.d(Tag, "setUpCameraOutputs(width=$width, height=$height, camera=$camera)")

        try {
            val largest = camera.getCaptureSize()

            val displayRotation = activity.windowManager.defaultDisplay.rotation
            val sensorOrientation = camera.getSensorOrientation()
            val swappedDimensions = areDimensionsSwapped(sensorOrientation, displayRotation)

            val displaySize = Point()
            activity.windowManager.defaultDisplay.getSize(displaySize)

            Log.d(Tag, "- display size: ${displaySize.x}x${displaySize.y}")
            Log.d(Tag, "- capture size: $largest ")

            previewSize = if (swappedDimensions) {
                camera.chooseOptimalSize(
                        height,
                        width,
                        displaySize.y,
                        displaySize.x,
                        largest
                )
            } else {
                camera.chooseOptimalSize(
                        width,
                        height,
                        displaySize.x,
                        displaySize.y,
                        largest
                )
            }

            textureView.setAspectRatio(previewSize.height, previewSize.width)
        } catch (e: CameraAccessException) {
            Log.e(Tag, e.toString())
        } catch (e: NullPointerException) {
            throw RuntimeException("Camera2 API not supported.", e)
        }
    }

    private fun areDimensionsSwapped(sensorOrientation: Int, displayRotation: Int): Boolean {
        Log.d(
                Tag,
                "areDimensionsSwapped(sensorOrientation=$sensorOrientation, displayRotation=$displayRotation)"
        )

        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(Tag, "Display rotation is invalid: $displayRotation")
            }
        }

        return swappedDimensions
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        Log.d(Tag, "configureTransform(viewWidth=$viewWidth, viewHeight=$viewHeight)")

        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width
            )
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    fun restart() {
        Log.d(Tag, "restart()")

        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
        camera.start(Surface(surfaceTexture))
    }

    // region Guides

    private fun manageGuides(showGuides: Boolean) {
        Log.d(Tag, "manageGuides(showGuides=$showGuides)")

        if (showGuides) {
            orientation.startListening(this)
            binding.horizontalGuideView1.visibility = View.VISIBLE
            binding.horizontalGuideView2Left.visibility = View.VISIBLE
            binding.levelView.visibility = View.VISIBLE
            binding.horizontalGuideView2Right.visibility = View.VISIBLE
            binding.verticalGuideView1.visibility = View.VISIBLE
            binding.verticalGuideView2.visibility = View.VISIBLE
        } else {
            orientation.stopListening()
            binding.horizontalGuideView1.visibility = View.INVISIBLE
            binding.horizontalGuideView2Left.visibility = View.INVISIBLE
            binding.levelView.visibility = View.INVISIBLE
            binding.horizontalGuideView2Right.visibility = View.INVISIBLE
            binding.verticalGuideView1.visibility = View.INVISIBLE
            binding.verticalGuideView2.visibility = View.INVISIBLE
        }
    }

    override fun onOrientationChanged(pitch: Float, roll: Float) {
//        Log.v(Tag, "onOrientationChanged($pitch=$pitch, $roll=$roll)")

        val levelView = binding.levelView

        levelView.rotation = roll

        val height = levelView.x
        val totalVisiblePitchDegrees = 45f * 2 // +/- 45 degrees
        levelView.translationY = pitch / totalVisiblePitchDegrees * height
    }

    // endregion

    companion object {
        private val Tag = Preview::class.java.simpleName
    }
}
