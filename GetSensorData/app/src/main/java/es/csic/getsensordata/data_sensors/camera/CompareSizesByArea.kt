package es.csic.getsensordata.data_sensors.camera

import android.util.Size
import java.util.*
import kotlin.math.sign


internal class CompareSizesByArea : Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size) =
        (lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height).sign
}
