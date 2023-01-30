package es.csic.getsensordata.data_sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

import es.csic.getsensordata.Microphone;
import es.csic.getsensordata.R;
import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class SoundDataSensor extends DataSensor {
    private static final String TAG = "Sound";
    private static final DataSensorType type = DataSensorType.Sound;
    private Microphone microphone;

    private boolean isAvailable;

    private String statusForScreen = "";
    private String statusForLog = "";

    public SoundDataSensor(@NonNull Context context, double updateInterval) {
        super(context, type, updateInterval);
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public boolean getOffersExtendedStatus() {
        return true;
    }

    public void connect() {
        try {
            AudioRecord.OnRecordPositionUpdateListener onRecordPositionUpdateListener;
            onRecordPositionUpdateListener = setHandler(this);
            microphone = new Microphone(onRecordPositionUpdateListener);
        } catch (SecurityException e) {
            microphone = null;
        }

        isAvailable = getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    public void disconnect() {
        if (microphone != null) {
            microphone.release();
        }
    }

    public void startReading() {
        if (microphone != null) {
            microphone.start_read_audio_Thread();
        }
    }

    public void stopReading() {
        if (microphone != null) {
            microphone.stop_audio_thread();
        }
    }

    @NonNull
    @Override
    public String getName() {
        String name;

        if (isAvailable) {
            name = getContext().getString(R.string.microphoneAvailable);
        } else {
            name = getContext().getString(R.string.microphoneNotAvailable);
        }

        return name;
    }

    @NonNull
    @Override
    public String getFeatures() {
        return getContext().getString(R.string.no_features);
    }

    @NonNull
    @Override
    public String getStatusForScreen() {
        return statusForScreen;
    }

    @NonNull
    @Override
    public String getExtendedStatusForScreen() {
        return "";
    }

    @NonNull
    @Override
    public String getStatusForLog() {
        return statusForLog;
    }

    private AudioRecord.OnRecordPositionUpdateListener setHandler(DataSensor dataSensor) {
        Log.d(TAG, "setAudioHandler()");

        return new AudioRecord.OnRecordPositionUpdateListener() {
            @Override
            public void onPeriodicNotification(AudioRecord recorder) {
                setCounter(getCounter() + 1);

                double timestamp = System.nanoTime();
                // initial timestamp set when user starts saving data to file
                if (timestamp >= getPreviousSensorTimestampInSeconds()) {
                    timestamp = timestamp - getPreviousSensorTimestampInSeconds();
                } else {
                    timestamp = (timestamp - getPreviousSensorTimestampInSeconds()) + Long.MAX_VALUE;
                }
                timestamp = timestamp * 1e-9; // from nanoseconds to seconds

                short[] buffer = microphone.buffer;
                double rms = 0;
                for (short value : buffer) {
                    rms += Math.pow(Math.abs(value), 2);
                }
                rms = Math.sqrt(rms / buffer.length);
                // Assumption: sensor saturates at 20 Pascals (120dB)
                double pressure = rms / Short.MAX_VALUE;
                // Human hearing threshold: 2E-5 Pascals
                double SPL = 20 * Math.log10(pressure / 2e-5);

                if (timestamp - getPreviousSensorTimestampInSeconds() > 0.005) {
                    float measurementFrequency = (float) (0.99 * getMeasurementFrequency() + 0.01 / (timestamp - getPreviousSensorTimestampInSeconds()));
                    setMeasurementFrequency(measurementFrequency);
                }

                if (timestamp - getPreviousSensorTimestampInSeconds() > getUpdateInterval()) {
                    statusForScreen = String.format(Locale.US, "\tRMS: \t\t\t%6.1f \n\tPressure: \t%6.2f \tmPa\n\tSPL: \t\t\t%6.1f \tdB\n\t\t\t\t\t\t\t\tFreq: %5.1f Hz", rms, pressure * 1000, SPL, getMeasurementFrequency());
                    setPreviousSensorTimestampInSeconds(timestamp);

                    if (getListener() != null) {
                        getListener().onDataSensorChanged(dataSensor);
                    }
                }

                statusForLog = String.format(Locale.US, "\nSOUN;%.3f;%.2f;%.5f;%.2f", timestamp, rms, pressure, SPL);
            }

            @Override
            public void onMarkerReached(AudioRecord recorder) {
            }
        };
    }
}
