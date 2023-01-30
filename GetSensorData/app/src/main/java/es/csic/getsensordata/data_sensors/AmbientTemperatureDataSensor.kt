package es.csic.getsensordata.data_sensors

import android.content.Context
import android.util.Log
import es.csic.getsensordata.R
import es.csic.getsensordata.data_sensors.definition.DataSensorType
import es.csic.getsensordata.data_sensors.definition.ManagedDataSensor
import java.util.*

class AmbientTemperatureDataSensor(context: Context, updateInterval: Double):
    ManagedDataSensor(context, DataSensorType.AmbientTemperature, updateInterval) {

    override fun getName(): String =
        if (sensor != null) {
            sensor.name
        } else {
            context.getString(R.string.ambient_temperature_sensor_not_detected)
        }

    override fun getFeatures(): String =
        if (sensor != null) {
            """
                | ${context.getString(R.string.manufacturer)}: ${sensor.vendor},
                | ${context.getString(R.string.version)}: ${sensor.version}, Type: ${sensor.type},
                | ${context.getString(R.string.resolution)}: ${sensor.resolution} units?,
                | ${context.getString(R.string.maximum_range)}: ${sensor.maximumRange} units?,
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
            measurementFrequency = (0.9 * measurementFrequency + 0.1 / (eventTimestampInSeconds - previousSensorTimestampInSeconds)).toFloat()
        } else {
            Log.e("${getPrefix()} SENSOR", "timestamp < previousTimestamp")
        }
        previousSensorTimestampInSeconds = eventTimestampInSeconds

        return if (secondsFromEpoch - previousSecondsFromEpoch > updateInterval) {
            val templateForScreen = """
                |   Ambient Temperature: %8.1f  ºC
                |                               Freq: %5.0f Hz
            """.trimMargin()
            String.format(Locale.US, templateForScreen,
                event!!.values[0],
                measurementFrequency
            )
        } else {
            ""
        }
    }

    override fun getStatusForLog(): String {
        val eventTimestampInSeconds = getEventTimestampInSeconds(event)
        val secondsFromEpoch = getSecondsFromEpoch()

        val templateForLog = "\n${getPrefix()};%.3f;%.3f;%.1f;%d"
        return String.format(Locale.US, templateForLog,
                secondsFromEpoch,
                eventTimestampInSeconds,
                event!!.values[0],
                event!!.accuracy
        )
    }
}
