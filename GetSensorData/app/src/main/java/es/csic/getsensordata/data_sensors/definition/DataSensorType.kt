package es.csic.getsensordata.data_sensors.definition

import android.hardware.Sensor

/**
 * Different types of data sensors this app can work with.
 *
 * Managed data sensors should use `Sensor` type constants as values. Unmanaged data sensors should
 * use integer values. Make sure each item of the enumeration has a different value.
 *
 * The parameters `brand` and `model` should be used to make it easier to differentiate between
 * similar sensors like, for example, a plethora of different IMUs.
 *
 * @param value: unique integer identifier for each item on the enumeration.
 * @param prefix: unique string included every time sensor information is shown or stored.
 * @param brand: optional. Brand of the sensor.
 * @param model: optional. Model of the sensor.
 */
enum class DataSensorType(
    val value: Int,
    val prefix: String,
    val brand: String? = null,
    val model: String? = null
) {
    Accelerometer(
        value = Sensor.TYPE_ACCELEROMETER,
        prefix = "ACCE"
    ),
    Gyroscope(
        value = Sensor.TYPE_GYROSCOPE,
        prefix = "GYRO"
    ),
    MagneticField(
        value = Sensor.TYPE_MAGNETIC_FIELD,
        prefix = "MAGN"
    ),
    Pressure(
        value = Sensor.TYPE_PRESSURE,
        prefix = "PRES"
    ),
    Light(
        value = Sensor.TYPE_LIGHT,
        prefix = "LIGH"
    ),
    Proximity(
        value = Sensor.TYPE_PROXIMITY,
        prefix = "PROX"
    ),
    RelativeHumidity(
        value = Sensor.TYPE_RELATIVE_HUMIDITY,
        prefix = "HUMI"
    ),
    AmbientTemperature(
        value = Sensor.TYPE_AMBIENT_TEMPERATURE,
        prefix = "TEMP"
    ),
    RotationVector(
        value = Sensor.TYPE_ROTATION_VECTOR,
        prefix = "AHRS"
    ),
    GlobalNavigationSatelliteSystem(
        value = 1001,
        prefix = "GNSS"
    ),
    WiFi(
        value = 1002,
        prefix = "WIFI"
    ),
    Bluetooth(
        value = 1003,
        prefix = "BLUE"
    ),
    BluetoothLowEnergy(
        value = 1004,
        prefix = "BLE4"
    ),
    Sound(
        value = 1005,
        prefix = "SOUN"
    ),
    Camera(
        value = 1006,
        prefix = "CAM"
    ),
    RadioFrequencyIdentification(
        value = 1007,
        prefix = "RFID",
        brand = "RF Code",
        model = "M220"
    ),
    InertialMeasurementUnitIMUX(
        value = 1008,
        prefix = "IMUX",
        brand = "Xsens",
        model = "MTI-G-28A53G35"
    )
}
