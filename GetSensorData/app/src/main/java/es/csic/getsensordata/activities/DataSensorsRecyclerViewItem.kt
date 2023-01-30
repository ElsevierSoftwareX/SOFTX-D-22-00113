package es.csic.getsensordata.activities

import es.csic.getsensordata.data_sensors.definition.DataSensor

interface DataSensorsRecyclerViewItem

class DataSensorsRecyclerViewItemHeader(val header: String) : DataSensorsRecyclerViewItem
class DataSensorsRecyclerViewItemDataSensor(val dataSensor: DataSensor) : DataSensorsRecyclerViewItem
class DataSensorsRecyclerViewItemDummyDataSensor(val dummyText: String) : DataSensorsRecyclerViewItem
