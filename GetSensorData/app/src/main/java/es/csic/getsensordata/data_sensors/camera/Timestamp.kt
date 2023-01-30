package es.csic.getsensordata.data_sensors.camera

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*


class Timestamp {
    companion object {
        private val Tag = Timestamp::class.java.simpleName

        fun compose(): String {
            Log.d(Tag, "getTimestamp()")

            val pattern = "yyyy-MM-dd_HH-mm-ss"
            val simpleDateFormat = SimpleDateFormat(pattern, Locale.ROOT)
            return simpleDateFormat.format(Date())
        }
    }
}
