package es.csic.getsensordata.data_sensors;

import android.content.Context;
import android.util.Log;

import es.csic.getsensordata.data_sensors.definition.DataSensorProxy;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class BLEDataSensor extends DataSensorProxy {
    private static final String tag = "BLE";

    public BLEDataSensor(Context context) {
        super(context, DataSensorType.BluetoothLowEnergy, tag);

        Log.d(tag, "BLEDataSensor(context=" + context + ")");
    }
}
