package es.csic.getsensordata.activities

import es.csic.getsensordata.data_sensors.definition.DataSensor

class DataSensorsRecyclerViewItems(private val dataSensorsRecyclerViewItems: Array<DataSensorsRecyclerViewItem>) {

    val size
        get() = dataSensorsRecyclerViewItems.size

    operator fun get(index: Int): DataSensorsRecyclerViewItem {
        return dataSensorsRecyclerViewItems[index]
    }

    fun indexOf(dataSensor: DataSensor): Int {
        val dataSensorsRecyclerViewItem = dataSensorsRecyclerViewItems.find {
            it is DataSensorsRecyclerViewItemDataSensor && it.dataSensor == dataSensor
        }
        return if (dataSensorsRecyclerViewItem != null) {
            dataSensorsRecyclerViewItems.indexOf(dataSensorsRecyclerViewItem)
        } else {
            -1
        }
    }
}
