package es.csic.getsensordata.data_sensors.definition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import es.csic.getsensordata.R

/**
 * DataSensor's implementation for managed sensor, that is, those accessible via `SensorManager`.
 *
 * This class implements the `SensorEventListener` interface, so it has to include the methods
 * `onSensorChanged()` and `onAccuracyChanged()`.
 */
abstract class ManagedDataSensor(
    context: Context,
    type: DataSensorType,
    updateInterval: Double
) : DataSensor(context, type, updateInterval), SensorEventListener {

    companion object {
        const val defaultSamplingPeriodUs = SensorManager.SENSOR_DELAY_GAME
    }

    var samplingPeriodUs = defaultSamplingPeriodUs

    // Managed sensors don't offer extended status
    override val offersExtendedStatus = false
    override fun getExtendedStatusForScreen() = context.getString(R.string.emptyStatus)

    // Most of this kind of sensor's functionality relies on SensorManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    val sensor: Sensor? = sensorManager?.getDefaultSensor(type.value)
    override val isAvailable = sensor != null

    // Reference of the last event registered, used when retrieving the sensor's status
    var event: DataSensorEvent? = null

    override fun startReading() {
        sensorManager?.registerListener(this, sensor, samplingPeriodUs)
    }

    override fun stopReading() {
        sensorManager?.unregisterListener(this)
    }

    // region SensorEventListener

    /**
     * The status of the sensor has changed.
     *
     * Keep a copy of the event generated and inform then to the listener of this class of the
     * status change.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        this.event = DataSensorEvent(this, event, null, "")
        listener?.onDataSensorChanged(this)
    }

    /**
     * The accuracy of the sensor has changed.
     *
     * Implemented only to follow SensorEventListener' interface.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    // endregion
}
