package es.csic.getsensordata.data_sensors;

import android.content.Context;
import android.util.Log;

import es.csic.getsensordata.data_sensors.definition.DataSensorProxy;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class IMUXDataSensor extends DataSensorProxy {
    private static final String tag = "IMUX";

    public IMUXDataSensor(Context context) {
        super(context, DataSensorType.InertialMeasurementUnitIMUX, tag);

        Log.d(tag, "IMUXDataSensor(context=" + context + ")");
    }
}
