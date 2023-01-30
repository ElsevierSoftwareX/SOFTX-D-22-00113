package es.csic.getsensordata.data_sensors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import es.csic.getsensordata.R;
import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

public class BluetoothDataSensor extends DataSensor {
    private static final String tag = "Bluetooth";
    private static final DataSensorType type = DataSensorType.Bluetooth;

    private boolean bluetoothWasOff = false;
    public BluetoothAdapter bluetoothAdapter;
    private TimerTask bluetoothTimerTask;
    private Timer bluetoothTimer;
    private BroadcastReceiver bluetoothDiscoveryMonitor;
    private BroadcastReceiver bluetoothDiscoveryResult;
    private final Handler bluetoothHandler = new Handler();
    private final ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();

    private boolean isAvailable = false;
    private String name = "";
    private String features = "";
    private String statusForScreen = "";
    private String extendedStatusForScreen = "";
    private String statusForLog = "";
    private final Boolean discoverBluetooth = false;

    public BluetoothDataSensor(@NonNull Context context, double updateInterval) {
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

    public void connect() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        isAvailable = false;
        name = getContext().getString(R.string.bluetoothAdapterNotAvailable);
        features = getContext().getString(R.string.no_features);

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                while (bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
                    if (bluetoothAdapter.getState() != BluetoothAdapter.STATE_TURNING_ON) {
                        bluetoothAdapter.enable();
                        bluetoothWasOff = true;
                    }
                }
            }

            if (bluetoothAdapter.isEnabled()) {
                isAvailable = true;
                name = getContext().getString(R.string.bluetoothOn);

                String address = bluetoothAdapter.getAddress();
                String bluetoothAdapterName = bluetoothAdapter.getName();
                features = " Bluetooth Name: " + bluetoothAdapterName + " Address: " + address;
            }

            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            StringBuilder statusStringBuilder = new StringBuilder();
            statusStringBuilder.
                    append("\tBonded Bluetooth devices: ").
                    append(bondedDevices.size());
            for (BluetoothDevice bondedDevice : bondedDevices) {
                statusStringBuilder.
                        append("\n\t- ").
                        append(bondedDevice.getName()).
                        append(" Address: ").
                        append(bondedDevice.getAddress());
            }
            statusForScreen = statusStringBuilder.toString();

            setHandler(this);

            bluetoothTimer = new Timer("Bluetooth Timer");
            bluetoothTimer.schedule(bluetoothTimerTask, 15000, 10000);
        }
    }

    public void disconnect() {
        if (bluetoothWasOff) {
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
            }
        }
    }

    public void startReading() {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && bluetoothAdapter != null) {
            getContext().registerReceiver(bluetoothDiscoveryMonitor, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
            getContext().registerReceiver(bluetoothDiscoveryResult, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        }
    }

    public void stopReading() {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && bluetoothAdapter != null) {
            getContext().unregisterReceiver(bluetoothDiscoveryMonitor);
            getContext().unregisterReceiver(bluetoothDiscoveryResult);
        }
    }

    private void setHandler(DataSensor dataSensor) {
        bluetoothDiscoveryMonitor = new BroadcastReceiver() {
            final String discoveryFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

            @Override
            public void onReceive(Context context, Intent intent) {
                if (discoveryFinished.equals(intent.getAction())) {
                    StringBuilder statusStringBuilder = new StringBuilder();
                    statusStringBuilder.
                            append("\tDiscovered Bluetooth devices:").
                            append(bluetoothDevices.size());
                    for (BluetoothDevice device : bluetoothDevices) {
                        statusStringBuilder.
                                append("\n\t-").
                                append(device.getName()).
                                append(" Address: ").
                                append(device.getAddress()).
                                append(" RSSI: n.a.");
                    }

                    // get freq update
                    setCounter(getCounter() + 1);
                    float measurementFrequency = (float) (0.99 * getMeasurementFrequency() + 0.01 / (getSecondsFromEpoch() - getPreviousSensorTimestampInSeconds()));
                    setMeasurementFrequency(measurementFrequency);
                    setPreviousSensorTimestampInSeconds(getSecondsFromEpoch());
                    statusStringBuilder.insert(0, String.format(Locale.US, "\t\t\t\t\t\t\t\tFreq: %5.2f Hz\n", measurementFrequency));
                    extendedStatusForScreen = statusStringBuilder.toString();

                    Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                    statusStringBuilder = new StringBuilder();
                    statusStringBuilder.
                            append("\tBonded Bluetooth devices:").
                            append(bondedDevices.size());
                    for (BluetoothDevice bondedDevice : bondedDevices) {
                        statusStringBuilder.append("\n\t-").append(bondedDevice.getName()).append(" Address: ").append(bondedDevice.getAddress());
                    }
                    statusForScreen = statusStringBuilder.toString();

                    if (getListener() != null) {
                        getListener().onDataSensorChanged(dataSensor);
                    }
                }
            }
        };

        bluetoothDiscoveryResult = new BroadcastReceiver() {
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

                BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (!bluetoothDevices.contains(remoteDevice)) {
                    bluetoothDevices.add(remoteDevice);
                }

                String address = "";
                String name = "";
                if (remoteDevice != null) {
                    address = remoteDevice.getAddress();
                    name = remoteDevice.getName();
                }
                int rss = 0;
                statusForLog = String.format(Locale.US, "\nBLUE;%.3f;%s;%s;%d", timestamp, name, address, rss);
            }
        };

        bluetoothTimerTask = new TimerTask() {
            public void run() {
                bluetoothHandler.post(new Runnable() {
                    public void run() {
                        // TODO: discoverBluetooth is always false
                        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering() && discoverBluetooth) {
                            bluetoothDevices.clear();
                            bluetoothAdapter.startDiscovery();
                        }
                    }
                });
            }
        };

    }
}
