package es.csic.getsensordata.data_sensors

import android.content.Context
import android.util.Log
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.definition.DataSensorType
import es.csic.getsensordata.data_sensors.definition.ManagedDataSensor
import java.util.*

class GyroscopeDataSensor(context: Context, updateInterval: Double):
    ManagedDataSensor(context, DataSensorType.Gyroscope, updateInterval) {

    override fun getName(): String =
        if (sensor != null) {
            sensor.name
        } else {
            context.getString(R.string.gyroscope_sensor_not_detected)
        }

    override fun getFeatures(): String =
        if (sensor != null) {
            """
                | ${context.getString(R.string.manufacturer)}: ${sensor.vendor},
                | ${context.getString(R.string.version)}: ${sensor.version}, Type: ${sensor.type},
                | ${context.getString(R.string.resolution)}: ${sensor.resolution} rad/s,
                | ${context.getString(R.string.maximum_range)}: ${sensor.maximumRange} rad/s,
                | ${context.getString(R.string.power_consumption)}: ${sensor.power} mA,
                | ${context.getString(R.string.minimum_delay)}: ${sensor.minDelay}
            """.trimMargin()
        } else {
            context.getString(R.string.no_features)
        }

    override fun getStatusForScreen(): String {
        if (event == null) {
            return ""
        }

        counter += 1

        val eventTimestampInSeconds = getEventTimestampInSeconds(event)
        val secondsFromEpoch = getSecondsFromEpoch()

        if (eventTimestampInSeconds - previousSensorTimestampInSeconds > 0) {
            measurementFrequency = (0.99 * measurementFrequency + 0.01 / (eventTimestampInSeconds - previousSensorTimestampInSeconds)).toFloat()
        } else {
            Log.e("${getPrefix()} SENSOR", "timestamp < previousTimestamp")
        }
        previousSensorTimestampInSeconds = eventTimestampInSeconds

        return if (secondsFromEpoch - previousSecondsFromEpoch > updateInterval) {
            val templateForScreen = """
                |   Gyr(X): %10.5f  rad/s
                |   Gyr(Y): %10.5f  rad/s
                |   Gyr(Z): %10.5f  rad/s
                |                               Freq: %5.0f Hz
            """.trimMargin()
            previousSecondsFromEpoch = secondsFromEpoch
            String.format(Locale.US, templateForScreen,
                    event!!.values[0],
                    event!!.values[1],
                    event!!.values[2],
                    measurementFrequency
            )
        } else {
            ""
        }
    }

    override fun getStatusForLog(): String {
        val eventTimestampInSeconds = getEventTimestampInSeconds(event)
        val secondsFromEpoch = getSecondsFromEpoch()

        val templateForLog = "\n${getPrefix()};%.3f;%.3f;%.5f;%.5f;%.5f;%d"
        return String.format(Locale.US, templateForLog,
                secondsFromEpoch,
                eventTimestampInSeconds,
                event!!.values[0],
                event!!.values[1],
                event!!.values[2],
                event!!.accuracy
        )
    }
}
