package es.csic.getsensordata.data_sensors;

import android.content.Context;
import android.util.Log;

import es.csic.getsensordata.data_sensors.definition.DataSensorProxy;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class RFIDDataSensor extends DataSensorProxy {
    private static final String tag = "RFID";

    public RFIDDataSensor(Context context) {
        super(context, DataSensorType.RadioFrequencyIdentification, tag);

        Log.d(tag, "RFIDDataSensor(context=" + context + ")");
    }
}
