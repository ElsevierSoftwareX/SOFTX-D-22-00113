package es.csic.getsensordata.data_sensors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import es.csic.getsensordata.R;
import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class GNSSDataSensor extends DataSensor implements LocationListener, GpsStatus.Listener {
    private static final int MY_PERMISSION_ACCESS_COARSE_LOCATION = 11;
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 12;

    private static final String TAG = "GNSS";
    private static final DataSensorType type = DataSensorType.GlobalNavigationSatelliteSystem;
    private LocationManager locationManager = null;
    private boolean isAvailable = false;
    private String name = "";
    private String features = "";
    private String statusForScreen = "";
    private String extendedStatusForScreen = "";
    private String statusForLog = "";
    private GpsStatus gpsStatus = null;
    private int satellitesInView = 0;
    private int satellitesInUse = 0;

    public GNSSDataSensor(@NonNull Context context, double updateInterval) {
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

    @Override
    public void connect(@NonNull DataSensorEventListener listener) {
        locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

        if (locationManager != null) {
            LocationProvider provider = null;

            StringBuilder featuresStringBuilder = new StringBuilder();
            List<String> locationProviders = locationManager.getProviders(true);
            for (String locationProvider : locationProviders) {
                int locationProviderIndex = locationProviders.indexOf(locationProvider);
                try {
                    // Emulator crashes here
                    provider = locationManager.getProvider(locationProvider);
                } catch (Exception exception) {
                    Log.d(TAG, exception.toString());
                    features = getContext().getString(R.string.locationListenerGnssNoLocationProviders);
                }
                if (provider != null) {
                    featuresStringBuilder.
                            append(" - ").
                            append("Location Provider ").append(locationProviderIndex).append(": ").append(locationProvider.toUpperCase()).append(", ").
                            append("Accuracy: ").append(provider.getAccuracy()).append(", \n   ").
                            append("Supports Altitude: ").append(provider.supportsAltitude()).append(", ").
                            append("Power Cons.: ").append(provider.getPowerRequirement()).append(" mA" + "\n");
                }
            }

            isAvailable = true;
            name = getContext().getString(R.string.gnssDescription);
            features = featuresStringBuilder.toString();
        } else {
            isAvailable = false;
            name = getContext().getString(R.string.gnssNotDetected);
            features = getContext().getString(R.string.no_features);
        }
    }

    @Override
    public void disconnect() {

    }

    public void startReading() {
        // Register location manager
        if (locationManager != null) {
            if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity)getContext(), new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSION_ACCESS_COARSE_LOCATION);
                ActivityCompat.requestPermissions((Activity)getContext(), new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_ACCESS_FINE_LOCATION);
                ((Activity) getContext()).finish();
            }
            locationManager.addGpsStatusListener(this);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, 0, this);
                Log.d(TAG, "GPS provider requested");
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 200, 0, this);
                Log.i(TAG, "NETWORK provider requested");
            }
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 200, 0, this);
                Log.i(TAG, "PASSIVE provider requested");
            }
        }
        Log.i(TAG, "locationManager registered again");
    }

    public void stopReading() {
        locationManager.removeUpdates(this);
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getFeatures() {
        return features;
    }

    @NonNull
    @Override
    public String getStatusForScreen() {
        return statusForScreen;
    }

    @NonNull
    public String getExtendedStatusForScreen() {
        return extendedStatusForScreen;
    }

    @NonNull
    @Override
    public String getStatusForLog() {
        return statusForLog;
    }

    // region LocationListener

    public void onProviderDisabled(String provider) {
        Log.d(TAG, "onProviderDisabled(provider=" + provider + ")");

        isAvailable = false;
        statusForScreen = getContext().getString(R.string.locationListenerGnssProviderDisabled);

        if (getListener() != null) {
            getListener().onDataSensorChanged(this);
        }
    }

    public void onProviderEnabled(String provider) {
        Log.d(TAG, "onProviderEnabled(provider=" + provider + ")");

        isAvailable = true;
        statusForScreen = getContext().getString(R.string.locationListenerGnssProviderEnabled);

        if (getListener() != null) {
            getListener().onDataSensorChanged(this);
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "onStatusChanged(provider=" + provider + ", status=" + status + ", extras=" + extras + ")");

        statusForScreen = getContext().getString(R.string.locationListenerGnssProviderStatus, status);

        if (getListener() != null) {
            getListener().onDataSensorChanged(this);
        }
    }

    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged(location=" + location + ")");

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        float bearing = location.getBearing();
        float accuracy = location.getAccuracy();
        float speed = location.getSpeed();
        double sensorTimeStamp = (double) (location.getTime()) / 1000; // in seconds, from 1/1/1970
        String provider = location.getProvider();

        setCounter(getCounter() + 1);

        double timestamp = System.nanoTime();
        // initial timestamp set when user starts saving data to file
        if (timestamp >= getPreviousSensorTimestampInSeconds()) {
            timestamp = timestamp - getPreviousSensorTimestampInSeconds();
        } else {
            timestamp = (timestamp - getPreviousSensorTimestampInSeconds()) + Long.MAX_VALUE;
        }
        timestamp = timestamp * 1e-9; // from nanoseconds to seconds

        if (timestamp - getPreviousSensorTimestampInSeconds() > 0.005) {
            float measurementFrequency = (float) (0.99 * getMeasurementFrequency() + 0.01 / (timestamp - getPreviousSensorTimestampInSeconds()));
            setMeasurementFrequency(measurementFrequency);
        }

        if (timestamp - getPreviousSensorTimestampInSeconds() > getUpdateInterval()) {
            String status = String.format(Locale.US, "\tLatitude: \t%10.6f \tdegrees\n\tLongitude: \t%10.6f \tdegrees\n", latitude, longitude);
            String appendix;

            if (location.hasAltitude()) {
                appendix = String.format(Locale.US, "\tAltitude: \t%6.1f \t m\n", altitude);
            } else {
                appendix = "\tAltitude: \t\t? \tm\n";
            }
            status += appendix;

            if (location.hasAccuracy()) {
                appendix = String.format(Locale.US, "\tAccuracy: \t%8.3f \tm\n", accuracy);
            } else {
                appendix = "\tAccuracy: \t\t? \tm\n";
            }
            status += appendix;

            if (location.hasBearing()) {
                appendix = String.format(Locale.US, "\tBearing: \t\t%8.3f \tdegrees\n", bearing);
            } else {
                appendix = "\tBearing: \t\t? \tdegrees\n";
            }
            status += appendix;

            if (location.hasSpeed()) {
                appendix = String.format(Locale.US, "\tSpeed: \t%8.3f \tm\n", speed);
            } else {
                appendix = "\tSpeed: \t\t? \tm\n";
            }
            status += appendix;

            status += String.format(Locale.US, "\tTime: \t%8.3f \ts\n", sensorTimeStamp);

            status += String.format(Locale.US, "\t(Provider: \t%s;  Freq: %5.0f Hz)\n", provider.toUpperCase(), getMeasurementFrequency());

            statusForScreen = status;

            if (getListener() != null) {
                getListener().onDataSensorChanged(this);
            }

            setPreviousSensorTimestampInSeconds(timestamp);
        }

        statusForLog = String.format(Locale.US, "\nGNSS;%.3f;%.3f;%.6f;%.6f;%.3f;%.3f;%.1f;%.1f;%d;%d", timestamp, sensorTimeStamp, latitude, longitude, altitude, bearing, accuracy, speed, satellitesInView, satellitesInUse);
    }

    @SuppressLint("MissingPermission")
    public void onGpsStatusChanged(int event) {
        Log.d(TAG, "onGpsStatusChanged(event=" + event + ")");

        gpsStatus = locationManager.getGpsStatus(gpsStatus);

        if (gpsStatus != null) {
            Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            int satellitesInView = 0;
            int satellitesInUse = 0;
            StringBuilder strGpsStats = new StringBuilder();
            while (sat.hasNext()) {
                GpsSatellite satellite = sat.next();
                strGpsStats.append("\n\t- PRN:").append(satellite.getPrn()).append(", Used:").append(satellite.usedInFix()).append(", SNR:").append(satellite.getSnr()).append(", Az:").append(satellite.getAzimuth()).append("º,\n\t   Elev: ").append(satellite.getElevation()).append("º, Alma: ").append(satellite.hasAlmanac()).append(", Ephem: ").append(satellite.hasEphemeris());
                satellitesInView++;
                if (satellite.usedInFix()) {
                    satellitesInUse = satellitesInUse + 1;
                }
            }
            String text = "\tSatellites in View: " + satellitesInView + ", Satellites in Use: " + satellitesInUse + strGpsStats;

            this.satellitesInView = satellitesInView;
            this.satellitesInUse = satellitesInUse;

            extendedStatusForScreen = text;

            if (getListener() != null) {
                getListener().onDataSensorChanged(this);
            }
        }
    }

    // endregion
}
