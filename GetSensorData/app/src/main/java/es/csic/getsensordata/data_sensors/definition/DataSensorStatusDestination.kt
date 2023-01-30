package es.csic.getsensordata.data_sensors.definition

/**
 * Different destinations for the status for a `DataSensor` instance. Up to know, only the screen
 * and a log infrastructure are available.
 *
 */
enum class DataSensorStatusDestination {
    Screen,
    Log
}
