package es.csic.getsensordata.data_sensors.definition;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

/**
 * Proxy for the DataSensor class, for those sensors not yet adopting the new class hierarchy.
 *
 * The user of this class should provide values to mimic how a DataSensor works, so it can be
 * included in the DataSensor's workflow.
 *
 * The goal of this class is to quickly disappear, as it is only a hack to keep both past and
 * future working together.
 */
public class DataSensorProxy extends DataSensor {
    private final String tag;

    protected String name = "";
    protected String features = "";
    protected String statusForScreen = "";
    protected String extendedStatusForScreen = "";
    protected String statusForLog = "";
    protected boolean isAvailable = false;
    protected boolean offersExtendedStatus = false;

    // region DataSensorProxy Management

    public DataSensorProxy(Context context, DataSensorType type, String tag) {
        super(context, type, 0);

        this.tag = tag;

        Log.d(tag, "DataSensorProxy(" +
                "context=" + context + ", " +
                "type=" + type + ", " +
                "tag=" + tag + ")");
    }

    /**
     * Enable the proxy sensor.
     */
    public void enable() {
        Log.d(tag, "enable()");

        isAvailable = true;
    }

    /**
     * Disable the proxy sensor.
     */
    public void disable() {
        Log.d(tag, "disable()");

        isAvailable = false;
    }

    /**
     * Set the proxy sensor's name.
     */
    public void setName(String name) {
        Log.d(tag, "setName(name=" + name + ")");

        this.name = name;
    }

    /**
     * Set the proxy sensor's features.
     */
    public void setFeatures(String features) {
        Log.d(tag, "setFeatures(features=" + features + ")");

        this.features = features;
    }

    /**
     * Set the proxy sensor's status for screen.
     */
    public void setStatusForScreen(String statusForScreen) {
        Log.d(tag, "setStatusForScreen(" +
                "statusForScreen=" + statusForScreen + ")");

        this.statusForScreen = statusForScreen;
    }

    /**
     * Set the proxy sensor's extended status for screen.
     */
    public void setExtendedStatusForScreen(String extendedStatusForScreen) {
        Log.d(tag, "setExtendedStatusForScreen(" +
                "extendedStatusForScreen=" + extendedStatusForScreen + ")");

        this.extendedStatusForScreen = extendedStatusForScreen;
    }

    // endregion

    // region DataSensor Management

    @Override
    public boolean isAvailable() {
        Log.d(tag, "isAvailable()");

        return isAvailable;
    }

    @Override
    public boolean getOffersExtendedStatus() {
        Log.d(tag, "getOffersExtendedStatus()");

        return offersExtendedStatus;
    }

    @NonNull
    @Override
    public String getName() {
        Log.d(tag, "getName()");

        return name;
    }

    @NotNull
    @Override
    public String getFeatures() {
        Log.d(tag, "getFeatures()");

        return features;
    }

    @NonNull
    @Override
    public String getStatusForScreen() {
        Log.d(tag, "getStatusForScreen()");

        return statusForScreen;
    }

    @NonNull
    @Override
    public String getExtendedStatusForScreen() {
        Log.d(tag, "getExtendedStatusForScreen()");

        return extendedStatusForScreen;
    }

    @NonNull
    @Override
    public String getStatusForLog() {
        Log.d(tag, "getStatusForLog()");

        return statusForLog;
    }

    @Override
    public void startReading() {

    }

    @Override
    public void stopReading() {

    }

    // endregion
}
