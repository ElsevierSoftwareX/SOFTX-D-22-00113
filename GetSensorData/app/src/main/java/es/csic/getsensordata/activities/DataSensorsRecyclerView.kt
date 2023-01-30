package es.csic.getsensordata.activities

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import es.csic.getsensordata.data_sensors.definition.DataSensor

/**
 * Custom recycler view to expose sensor data.
 *
 * Differences with a standard recycler view is:
 *
 * 1. It can show/hide text views inside each cell depending on the boolean values
 * `showSensorFeatures` and `showSensorRealTimeData`.
 *
 * 2. It can update the contents of individual cells.
 */
class DataSensorsRecyclerView : RecyclerView {

    // region Constructors

    constructor(context: Context) :
            super(context)

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)

    init {
        itemAnimator = null
    }

    // endregion

    var showSensorFeatures = false
        set(value) {
            Log.d(Tag, "showSensorFeatures.set(value=$value)")

            field = value
            val dataSensorsRecyclerViewAdapter = adapter as DataSensorsRecyclerViewAdapter
            dataSensorsRecyclerViewAdapter.showSensorFeatures = value
            notifyItemRangeChanged()
        }

    var showSensorRealTimeData = false
        set(value) {
            Log.d(Tag, "showSensorRealTimeData.set(value=$value)")

            field = value
            val dataSensorsRecyclerViewAdapter = adapter as DataSensorsRecyclerViewAdapter
            dataSensorsRecyclerViewAdapter.showSensorRealTimeData = value
            notifyItemRangeChanged()
        }

    fun updateStatus(position: Int, status: String) {
        Log.d(Tag, "updateStatus(position=$position, status=$status)")

        adapter?.notifyItemChanged(position, status)
    }

    fun notifyDataSensorChanged(dataSensor: DataSensor) {
        Log.d(Tag, "notifyDataSensorChanged(dataSensor=$dataSensor)")

        val dataSensorsRecyclerViewAdapter = adapter as DataSensorsRecyclerViewAdapter
        val position = dataSensorsRecyclerViewAdapter.dataSensorsRecyclerViewItems.indexOf(dataSensor)
        adapter?.notifyItemChanged(position)
    }

    /**
     * Tell the adapter to update a range of items.
     */
    private fun notifyItemRangeChanged() {
        /*

        Ideally, use `adapter?.notifyItemRangeChanged()` to tell the adapter the first item to
        update, and how many items after it should also be updated. Use this code to get values
        needed:

        ```
        val layoutManager = layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        val visibleItems = lastVisibleItemPosition - firstVisibleItemPosition + 1
        ```

        Unfortunately, as this method also changes the recycler view height, some items not visible
        could not be updated and, after the change, appear to the user without the requested
        changes.

        The solution is to tell the adapter that the first item to update is the first in the
        recycler view and that every item after it should be updated: that is, every item.

        Then, why not use `adapter?.notifyDataSetChanged()`? Because:

        1. It doesn't animate the transition by default.
        2. It shows a performance warning in Android Studio.

        I'm sure this could be enhanced when we learn more about recycler views.

        */
        Log.d(Tag, "notifyItemRangeChanged()")

//        val layoutManager = layoutManager as LinearLayoutManager
//        adapter?.notifyItemRangeChanged(0, layoutManager.itemCount)
        adapter?.notifyDataSetChanged()
    }

    companion object {
        private val Tag = DataSensorsRecyclerView::class.java.simpleName
    }
}
