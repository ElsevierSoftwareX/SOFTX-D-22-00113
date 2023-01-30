package es.csic.getsensordata.data_sensors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import es.csic.getsensordata.R;
import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class WiFiDataSensor extends DataSensor {
    private static final String TAG = "WiFi";
    private static final DataSensorType type = DataSensorType.WiFi;

    private WifiManager wiFiManager;
    private BroadcastReceiver wiFiBroadcastReceiver;
    private TimerTask wiFiTimerTask;
    private Timer wiFiTimer;
    private final Handler wiFiHandler = new Handler();

    private boolean isAvailable = false;
    private String name = "";
    private String features = "";
    private String statusForScreen = "";
    private String extendedStatusForScreen = "";
    private String statusForLog = "";

    public WiFiDataSensor(@NonNull Context context, double updateInterval) {
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
        super.connect(listener);

        wiFiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wiFiManager != null) {
            if (!wiFiManager.isWifiEnabled()) {
                if (wiFiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLING) {
                    wiFiManager.setWifiEnabled(true);
                }

                isAvailable = false;
                name = getContext().getString(R.string.wifiOff);
                features = getContext().getString(R.string.wifiNotAvailable);
            } else {
                isAvailable = true;
                name = getContext().getString(R.string.wifiOn);
                features = getContext().getString(R.string.wifiMACAddress, wiFiManager.getConnectionInfo().getMacAddress());
            }
        } else {
            isAvailable = false;
            name = getContext().getString(R.string.wifiNotAvailable);
            features = getContext().getString(R.string.no_features);
        }

        setWiFiScanHandler(this);

        // Set timer at 1 Hz
        wiFiTimer = new Timer("WiFi Timer");
        wiFiTimer.scheduleAtFixedRate(wiFiTimerTask, 2000, 2000);
    }

    public void disconnect() {
        wiFiTimer.cancel();
    }

    public void startReading() {
        if (wiFiManager != null) {
            getContext().registerReceiver(wiFiBroadcastReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        }
    }

    public void stopReading() {
        if (wiFiManager != null) {
            getContext().unregisterReceiver(wiFiBroadcastReceiver);
        }
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

    private void setWiFiScanHandler(DataSensor dataSensor) {
        wiFiBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setCounter(getCounter() + 1);

                double timestamp = System.nanoTime();
                // initial timestamp set when user starts saving data to file
                if (timestamp >= getPreviousSensorTimestampInSeconds()) {
                    timestamp = timestamp - getPreviousSensorTimestampInSeconds();
                } else {
                    timestamp = (timestamp - getPreviousSensorTimestampInSeconds()) + Long.MAX_VALUE;
                }
                timestamp = timestamp * 1e-9; // from nanoseconds to seconds

                // See WiFi Scan results
                List<ScanResult> scanResults = wiFiManager.getScanResults();
                int wiFiAccessPoints = scanResults.size();
                StringBuilder statusForScreenStringBuilder = new StringBuilder();
                StringBuilder statusForLogStringBuilder = new StringBuilder();
                for (ScanResult scanResult : scanResults) {
                    String ssid = scanResult.SSID;
                    String bssid = scanResult.BSSID;
                    int frequency = scanResult.frequency;
                    long scanResultTimestamp = scanResult.timestamp;
                    double sensorTimeStamp = ((double) (scanResultTimestamp)) * 1E-6;
                    int rss = scanResult.level;
                    statusForScreenStringBuilder.append("\n\t- ").append(ssid).append(",\t").append(bssid).append(",\tRSS:").append(rss).append(" dBm");
                    statusForLogStringBuilder.append(String.format(Locale.US, "\nWIFI;%.3f;%.3f;%s;%s;%d;%d", timestamp, sensorTimeStamp, ssid, bssid, frequency, rss));
                }
                extendedStatusForScreen = "\tNumber of Wifi APs: " + wiFiAccessPoints + statusForScreenStringBuilder;
                statusForScreen = statusForScreenStringBuilder.toString();

                if (timestamp - getPreviousSensorTimestampInSeconds() > 0.005) {
                    float measurementFrequency = (float) (0.99 * getMeasurementFrequency() + 0.01 / (timestamp - getPreviousSensorTimestampInSeconds()));
                    setMeasurementFrequency(measurementFrequency);
                }

                if (wiFiManager.isWifiEnabled()) {
                    isAvailable = true;
                    name = getContext().getString(R.string.wifiOn);
                    features = getContext().getString(R.string.wifiMACAddress, wiFiManager.getConnectionInfo().getMacAddress());
                }

                WifiInfo wifiinfo = wiFiManager.getConnectionInfo();
                if (wifiinfo.getBSSID() != null) {
                    statusForScreen = String.format(Locale.US, "\tConnected to: %s\n\tBSSID: %s\n\tRSSI: %d dBm \n\tLinkSpeed: %d Mbps\n\t\t\t\t\t\t\t\tFreq: %5.1f Hz", wifiinfo.getSSID(), wifiinfo.getBSSID(), wifiinfo.getRssi(), wifiinfo.getLinkSpeed(), getMeasurementFrequency());
                } else {
                    statusForScreen = getContext().getString(R.string.wifiNoConnection);
                }

                if (getListener() != null) {
                    getListener().onDataSensorChanged(dataSensor);
                }
            }
        };

        wiFiTimerTask = new TimerTask() {
            public void run() {
                wiFiHandler.post(new Runnable() {
                    public void run() {
                        if (wiFiManager != null && wiFiManager.isWifiEnabled()) {
                            wiFiManager.startScan();
                        }
                    }
                });
            }
        };
    }
}
