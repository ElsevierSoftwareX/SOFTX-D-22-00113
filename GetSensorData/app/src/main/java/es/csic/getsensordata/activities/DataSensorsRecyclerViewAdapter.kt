package es.csic.getsensordata.activities

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import es.csic.getsensordata.R

private const val ITEM_VIEW_TYPE_DATA_SENSORS_HEADER = 0
private const val ITEM_VIEW_TYPE_DATA_SENSOR = 1
private const val ITEM_VIEW_TYPE_DATA_SENSOR_SMARTPHONE_DETAILS = 2

class DataSensorsRecyclerViewAdapter(val dataSensorsRecyclerViewItems: DataSensorsRecyclerViewItems) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var colorSensorAvailable = 0x000000
    var colorSensorNotAvailable = 0x000000
    var showSensorFeatures = false
    var showSensorRealTimeData = false

    companion object {
        private val Tag = DataSensorsRecyclerViewAdapter::class.java.simpleName
    }

    // region RecyclerView Management

    override fun getItemCount(): Int {
        Log.d(Tag, "getItemCount()")

        return dataSensorsRecyclerViewItems.size
    }

    override fun getItemViewType(position: Int): Int {
        Log.d(Tag, "getItemViewType(position=$position)")

        return when (position) {
            0, 16 -> ITEM_VIEW_TYPE_DATA_SENSORS_HEADER
            in 1..15, in 17..18 -> ITEM_VIEW_TYPE_DATA_SENSOR
            19 -> ITEM_VIEW_TYPE_DATA_SENSOR_SMARTPHONE_DETAILS
            else -> throw ClassCastException("Position out of range: $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d(Tag, "onCreateViewHolder(parent=$parent, viewType=$viewType)")

        return when (viewType) {
            ITEM_VIEW_TYPE_DATA_SENSORS_HEADER -> DataSensorsHeaderViewHolder.from(parent)
            ITEM_VIEW_TYPE_DATA_SENSOR -> DataSensorViewHolder.from(parent)
            ITEM_VIEW_TYPE_DATA_SENSOR_SMARTPHONE_DETAILS -> DataSensorSmartphoneDetailsViewHolder.from(
                parent
            )
            else -> throw ClassCastException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        Log.d(Tag, "onBindViewHolder(holder=$holder, position=$position, payloads=$payloads)")

        when (holder) {
            is DataSensorsHeaderViewHolder -> {
                val dataSensorsHeaderViewHolder: DataSensorsHeaderViewHolder = holder
                val dataSensorsRecyclerViewItem = dataSensorsRecyclerViewItems[position] as DataSensorsRecyclerViewItemHeader
                val header = dataSensorsRecyclerViewItem.header
                dataSensorsHeaderViewHolder.bind(header)
            }
            is DataSensorViewHolder -> {
                val dataSensorViewHolder: DataSensorViewHolder = holder
                dataSensorViewHolder.colorSensorAvailable = colorSensorAvailable
                dataSensorViewHolder.colorSensorNotAvailable = colorSensorNotAvailable
                val dataSensorsRecyclerViewItem = dataSensorsRecyclerViewItems[position]
                val description: String
                var features = ""
                var status = ""
                var extendedStatus = ""
                var isAvailable = false
                var showExtendedStatus = false
                if (dataSensorsRecyclerViewItem is DataSensorsRecyclerViewItemDataSensor) {
                    val dataSensor = dataSensorsRecyclerViewItem.dataSensor
                    description = dataSensor.getDescription()
                    features = dataSensor.getFeatures()
                    isAvailable = dataSensor.isAvailable
                    showExtendedStatus = dataSensor.offersExtendedStatus
                    status = dataSensor.getStatusForScreen()
                    extendedStatus = dataSensor.getExtendedStatusForScreen()
                    if (payloads.size == 0) {
                        status = dataSensor.getStatusForScreen()
                    } else {
                        if (showSensorRealTimeData) {
                            status = payloads.first() as String
                        }
                    }
                } else {
                    val dataSensorsRecyclerViewItemDummyDataSensor = dataSensorsRecyclerViewItem as DataSensorsRecyclerViewItemDummyDataSensor
                    description = dataSensorsRecyclerViewItemDummyDataSensor.dummyText
                }
                dataSensorViewHolder.bind(
                    description,
                    features,
                    status,
                    extendedStatus,
                    isAvailable,
                    showSensorFeatures,
                    showSensorRealTimeData,
                    showExtendedStatus
                )
            }
            is DataSensorSmartphoneDetailsViewHolder -> {
                val dataSensorSmartphoneDetailsViewHolder: DataSensorSmartphoneDetailsViewHolder =
                    holder
                val dataSensorsRecyclerViewItem = dataSensorsRecyclerViewItems[position]
                val dataSensorsRecyclerViewItemDummyDataSensor = dataSensorsRecyclerViewItem as DataSensorsRecyclerViewItemDummyDataSensor
                val smartphoneDetails = dataSensorsRecyclerViewItemDummyDataSensor.dummyText
                dataSensorSmartphoneDetailsViewHolder.bind(smartphoneDetails)
            }
        }
    }

    // endregion

    // region View Holders

    class DataSensorsHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val headerTextView: TextView = itemView.findViewById(R.id.dataSensorsHeaderTextView)

        fun bind(header: String) {
            Log.d(Tag, "bind(header=$header)")

            headerTextView.text = header
        }

        companion object {
            private val Tag = DataSensorsHeaderViewHolder::class.java.simpleName

            fun from(parent: ViewGroup): DataSensorsHeaderViewHolder {
                Log.d(Tag, "from(parent=$parent)")

                val layoutInflater = LayoutInflater.from(parent.context)
                val view = layoutInflater.inflate(R.layout.data_sensors_header, parent, false)
                return DataSensorsHeaderViewHolder(view)
            }
        }
    }

    class DataSensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var colorSensorAvailable = 0x000000
        var colorSensorNotAvailable = 0x000000

        private val descriptionTextView: TextView =
            itemView.findViewById(R.id.dataSensorDescriptionTextView)
        private val featuresTextView: TextView =
            itemView.findViewById(R.id.dataSensorFeaturesTextView)
        private val statusTextView: TextView =
            itemView.findViewById(R.id.dataSensorStatusTextView)
        private val extendedStatusTextView: TextView =
            itemView.findViewById(R.id.dataSensorExtendedStatusTextView)

        fun bind(
            description: String,
            features: String,
            status: String,
            extendedStatus: String,
            isAvailable: Boolean,
            showSensorFeatures: Boolean,
            showSensorRealTimeData: Boolean,
            showExtendedStatus: Boolean
        ) {
            Log.d(
                Tag,
                "bind(" +
                        "description=$description, " +
                        "features=$features, " +
                        "status=$status, " +
                        "extendedStatus=$extendedStatus, " +
                        "isAvailable=$isAvailable, " +
                        "showSensorFeatures=$showSensorFeatures, " +
                        "showSensorRealTimeData=$showSensorRealTimeData, " +
                        "showExtendedStatus=$showExtendedStatus)"
            )

            descriptionTextView.text = description
            featuresTextView.text = features
            statusTextView.text = status
            extendedStatusTextView.text = extendedStatus

            if (isAvailable) {
                descriptionTextView.setBackgroundColor(colorSensorAvailable)
            } else {
                descriptionTextView.setBackgroundColor(colorSensorNotAvailable)
            }

            if (showSensorFeatures) {
                featuresTextView.visibility = View.VISIBLE
            } else {
                featuresTextView.visibility = View.GONE
            }

            if (showSensorRealTimeData) {
                statusTextView.visibility = View.VISIBLE
                if (showExtendedStatus) {
                    extendedStatusTextView.visibility = View.VISIBLE
                }
            } else {
                statusTextView.visibility = View.GONE
                extendedStatusTextView.visibility = View.GONE
            }
        }

        companion object {
            private val Tag = DataSensorViewHolder::class.java.simpleName

            fun from(parent: ViewGroup): DataSensorViewHolder {
                Log.d(Tag, "from(parent=$parent)")

                val layoutInflater = LayoutInflater.from(parent.context)
                val view = layoutInflater.inflate(R.layout.data_sensor, parent, false)
                return DataSensorViewHolder(view)
            }
        }
    }

    class DataSensorSmartphoneDetailsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val smartphoneDetailsTextView: TextView =
            itemView.findViewById(R.id.dataSensorSmartphoneDetails)

        fun bind(smartphoneDetails: String) {
            Log.d(Tag, "bind(smartphoneDetails=$smartphoneDetails)")

            smartphoneDetailsTextView.text = smartphoneDetails
        }

        companion object {
            private val Tag = DataSensorSmartphoneDetailsViewHolder::class.java.simpleName

            fun from(parent: ViewGroup): DataSensorSmartphoneDetailsViewHolder {
                Log.d(Tag, "from(parent=$parent)")

                val layoutInflater = LayoutInflater.from(parent.context)
                val view =
                    layoutInflater.inflate(R.layout.data_sensor_smartphone_details, parent, false)
                return DataSensorSmartphoneDetailsViewHolder(view)
            }
        }
    }

    // endregion
}
