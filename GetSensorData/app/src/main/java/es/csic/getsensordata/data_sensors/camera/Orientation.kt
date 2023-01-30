package es.csic.getsensordata.data_sensors.camera

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface


class Orientation(activity: Activity) : SensorEventListener {

    interface Listener {
        fun onOrientationChanged(pitch: Float, roll: Float)
    }

    private val mWindowManager: WindowManager = activity.window.windowManager
    private val mSensorManager: SensorManager =
        activity.getSystemService(Activity.SENSOR_SERVICE) as SensorManager

    private val mRotationSensor: Sensor?
    private var mLastAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    private var mListener: Listener? = null

    init {
        Log.d(Tag, "init")

        // Can be null if the sensor hardware is not available
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    fun startListening(listener: Listener) {
        Log.d(Tag, "startListening(listener=$listener)")

        if (mListener === listener) {
            return
        }
        mListener = listener
        if (mRotationSensor == null) {
            Log.w(Tag, "Rotation vector sensor not available; will not provide orientation data.")
            return
        }
        mSensorManager.registerListener(this, mRotationSensor, SENSOR_DELAY_MICROS)
    }

    fun stopListening() {
        Log.d(Tag, "stopListening()")

        mSensorManager.unregisterListener(this)
        mListener = null
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(Tag, "onAccuracyChanged(sensor=$sensor, accuracy=$accuracy)")

        mLastAccuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.v(Tag, "onSensorChanged(event=$event)")

        if (mListener == null) {
            return
        }
        if (mLastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }
        if (event.sensor == mRotationSensor) {
            updateOrientation(event.values)
        }
    }

    private fun updateOrientation(rotationVector: FloatArray) {
        Log.v(Tag, "updateOrientation(rotationVector=$rotationVector)")

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        val (worldAxisForDeviceAxisX, worldAxisForDeviceAxisY) = when (mWindowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Z)
            Surface.ROTATION_90 -> Pair(SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X)
            Surface.ROTATION_180 -> Pair(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z)
            Surface.ROTATION_270 -> Pair(SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X)
            else -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Z)
        }

        val adjustedRotationMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix, worldAxisForDeviceAxisX,
            worldAxisForDeviceAxisY, adjustedRotationMatrix
        )

        // Transform rotation matrix into azimuth/pitch/roll
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)

        // Convert radians to degrees
        val pitch = orientation[1] * -57
        val roll = orientation[2] * -57

        mListener?.onOrientationChanged(pitch, roll)
    }

    companion object {
        private val Tag = Orientation::class.java.simpleName
        private const val SENSOR_DELAY_MICROS = 16 * 1000 // 16 ms

        fun toExif(rotationDegrees: Int, mirrored: Boolean): Int {
            Log.d(Tag, "toExif(rotationDegrees=$rotationDegrees, mirrored=$mirrored)")
            return when {
                rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
                rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
                rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
                rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
                rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
                rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
                rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
                rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
                rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
                else -> ExifInterface.ORIENTATION_UNDEFINED
            }
        }
    }
}
