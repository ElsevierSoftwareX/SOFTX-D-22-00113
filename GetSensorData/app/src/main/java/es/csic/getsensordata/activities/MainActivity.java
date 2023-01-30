package es.csic.getsensordata.activities;


import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.MacAddress;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsChecker;
import com.estimote.sdk.eddystone.Eddystone;
import com.estimote.sdk.eddystone.EddystoneTelemetry;
import com.estimote.sdk.telemetry.EstimoteTelemetry;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import es.csic.getsensordata.MTiXSensIMU;
import es.csic.getsensordata.R;
import es.csic.getsensordata.RFIDM220Reader;
import es.csic.getsensordata.Smartphone;
import es.csic.getsensordata.data_sensors.AccelerometerDataSensor;
import es.csic.getsensordata.data_sensors.AmbientTemperatureDataSensor;
import es.csic.getsensordata.data_sensors.BLEDataSensor;
import es.csic.getsensordata.data_sensors.BluetoothDataSensor;
import es.csic.getsensordata.data_sensors.CameraDataSensor;
import es.csic.getsensordata.data_sensors.GNSSDataSensor;
import es.csic.getsensordata.data_sensors.GyroscopeDataSensor;
import es.csic.getsensordata.data_sensors.IMUXDataSensor;
import es.csic.getsensordata.data_sensors.LightDataSensor;
import es.csic.getsensordata.data_sensors.MagneticFieldDataSensor;
import es.csic.getsensordata.data_sensors.PressureDataSensor;
import es.csic.getsensordata.data_sensors.ProximityDataSensor;
import es.csic.getsensordata.data_sensors.RFIDDataSensor;
import es.csic.getsensordata.data_sensors.RelativeHumidityDataSensor;
import es.csic.getsensordata.data_sensors.RotationVectorDataSensor;
import es.csic.getsensordata.data_sensors.SoundDataSensor;
import es.csic.getsensordata.data_sensors.WiFiDataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensor.DataSensorEventListener;
import es.csic.getsensordata.data_sensors.definition.DataSensorProxy;
import es.csic.getsensordata.data_sensors.definition.DataSensorStatusDestination;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;
import es.csic.getsensordata.databinding.ActivityMainBinding;
import es.csic.getsensordata.preferences.Preferences;

public class MainActivity extends AppCompatActivity implements DataSensorEventListener {

    private final String Tag = MainActivity.class.getSimpleName();

    private ActivityMainBinding binding;

    // Flags
    private final Boolean isTracingEnabled = false;

    // Show seconds logging in toggle button
    private TimerTask secondsLoggingTimerTask;
    private final Handler secondsLoggingHandler = new Handler();
    private Timer secondsLoggingTimer;

    ArrayList<RFIDM220Reader> rfidM220Readers = new ArrayList<>();

    //----XSens--
    MTiXSensIMU mXSens;   // XSens object
    private UsbDevice mUsbDevice = null;
    private static final String ACTION_USB_PERMISSION = "es.csic.getsensordata.USB_PERMISSION";
    BroadcastReceiver usbBroadcastReceiver;
    boolean USBConnected = false;

    //---
    AccelerometerDataSensor accelerometerDataSensor;
    GyroscopeDataSensor gyroscopeDataSensor;
    MagneticFieldDataSensor magneticFieldDataSensor;
    PressureDataSensor pressureDataSensor;
    LightDataSensor lightDataSensor;
    ProximityDataSensor proximityDataSensor;
    RelativeHumidityDataSensor relativeHumidityDataSensor;
    AmbientTemperatureDataSensor ambientTemperatureDataSensor;
    RotationVectorDataSensor rotationVectorDataSensor;
    CameraDataSensor cameraDataSensor;
    GNSSDataSensor gnssDataSensor;
    WiFiDataSensor wiFiDataSensor;
    BluetoothDataSensor bluetoothDataSensor;
    BLEDataSensor bleDataSensor;
    SoundDataSensor soundDataSensor;
    RFIDDataSensor rfidDataSensor;
    IMUXDataSensor imuxDataSensor;

    String bluetooth4FeaturesText;

    OutputStreamWriter outputStreamWriter;

    long startingTimestampNanoseconds = 0;
    long timestampNanoseconds;
    double timestampSeconds;
    long positionCounter = 0;
    double timestamp_Imul_last_update = 0;
    double updateInterval = 0.25;   // update screen with measurements every 0.25 seconds (4 Hz)

    // Estimote:
    private BeaconManager beaconManager;
    private Region region;
    String scanId;  // telemetry state
    String scanId_Eddystone;
    boolean bleEnabled = true;
    boolean flag_EstimoteTelemetry = false;   // set to true if telemetry required

    // User interface
    int colorSensorAvailable = 0xff000000;
    int colorSensorNotAvailable = 0xff000000;

    // Recycler View
    DataSensorsRecyclerView dataSensorsRecyclerView;
    DataSensorsRecyclerViewItems dataSensorsRecyclerViewItems;

    // region Activity Lifecycle

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        if (isTracingEnabled) {
            // Start tracing to "/sdcard/GetSensorData_Trace.trace"
            Debug.startMethodTracing("GetSensorData_Trace");
        }

        Log.d(Tag, Smartphone.Companion.toString());

        Resources resources = getResources();
        colorSensorAvailable = resources.getColor(R.color.colorSensorAvailable);
        colorSensorNotAvailable = resources.getColor(R.color.colorSensorNotAvailable);

        setShowSensorFeaturesToggleButtonHandler();
        setShowRealTimeDataToggleButtonHandler();
        setSaveLogToggleButtonHandler();
        setMarkPositionButtonHandler();

        accelerometerDataSensor = new AccelerometerDataSensor(this, updateInterval);
        gyroscopeDataSensor = new GyroscopeDataSensor(this, updateInterval);
        magneticFieldDataSensor = new MagneticFieldDataSensor(this, updateInterval);
        pressureDataSensor = new PressureDataSensor(this, updateInterval);
        lightDataSensor = new LightDataSensor(this, updateInterval);
        proximityDataSensor = new ProximityDataSensor(this, updateInterval);
        relativeHumidityDataSensor = new RelativeHumidityDataSensor(this, updateInterval);
        ambientTemperatureDataSensor = new AmbientTemperatureDataSensor(this, updateInterval);
        rotationVectorDataSensor = new RotationVectorDataSensor(this, updateInterval);
        cameraDataSensor = new CameraDataSensor(this, updateInterval);
        gnssDataSensor = new GNSSDataSensor(this, updateInterval);
        wiFiDataSensor = new WiFiDataSensor(this, updateInterval);
        bluetoothDataSensor = new BluetoothDataSensor(this, updateInterval);
        bleDataSensor = new BLEDataSensor(this);
        soundDataSensor = new SoundDataSensor(this, updateInterval);
        rfidDataSensor = new RFIDDataSensor(this);
        imuxDataSensor = new IMUXDataSensor(this);

        accelerometerDataSensor.connect(this);
        gyroscopeDataSensor.connect(this);
        magneticFieldDataSensor.connect(this);
        pressureDataSensor.connect(this);
        lightDataSensor.connect(this);
        proximityDataSensor.connect(this);
        relativeHumidityDataSensor.connect(this);
        ambientTemperatureDataSensor.connect(this);
        rotationVectorDataSensor.connect(this);
        cameraDataSensor.connect(this);
        gnssDataSensor.connect(this);
        wiFiDataSensor.connect(this);
        bluetoothDataSensor.connect(this);
        bleDataSensor.connect(this);
        soundDataSensor.connect(this);
        rfidDataSensor.connect(this);
        imuxDataSensor.connect(this);

        // DataSensorsRecyclerView: start

        dataSensorsRecyclerView = findViewById(R.id.dataSensorsRecyclerView);
        // TODO:
        // - divide in two lists: internal and external, to avoid headers.
        // - even better: add property to DataSensor so each sensor knows if it is internal or
        //   external. Then, in the recycler view, divide them accordingly.
        // - smartphone details should not be in the recycler view.
        dataSensorsRecyclerViewItems = new DataSensorsRecyclerViewItems(new DataSensorsRecyclerViewItem[]{
                new DataSensorsRecyclerViewItemHeader("Internal Sensors"),
                new DataSensorsRecyclerViewItemDataSensor(accelerometerDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(gyroscopeDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(magneticFieldDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(pressureDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(lightDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(proximityDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(relativeHumidityDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(ambientTemperatureDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(rotationVectorDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(gnssDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(wiFiDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(bluetoothDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(bleDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(soundDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(cameraDataSensor),
                new DataSensorsRecyclerViewItemHeader("External Sensors"),
                new DataSensorsRecyclerViewItemDataSensor(rfidDataSensor),
                new DataSensorsRecyclerViewItemDataSensor(imuxDataSensor),
                new DataSensorsRecyclerViewItemDummyDataSensor(Smartphone.Companion.getSummary(this))
        });
        DataSensorsRecyclerViewAdapter dataSensorsRecyclerViewAdapter = new DataSensorsRecyclerViewAdapter(dataSensorsRecyclerViewItems);
        dataSensorsRecyclerViewAdapter.setColorSensorAvailable(colorSensorAvailable);
        dataSensorsRecyclerViewAdapter.setColorSensorNotAvailable(colorSensorNotAvailable);
        dataSensorsRecyclerView.setAdapter(dataSensorsRecyclerViewAdapter);

        // DataSensorsRecyclerView: end

        //-------------------- BLE Handler ------------------------------------------
        boolean hasBLE = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        if (hasBLE) {
            Log.i("OnCreate", "BLE is available");
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            String bluetoothPreferencesBleEnabledKey = getString(R.string.bluetoothPreferencesBleEnabledKey);
            bleEnabled = pref.getBoolean(bluetoothPreferencesBleEnabledKey, true);
            Log.i("OnCreate", "bleEnabled read");
        } else {
            Log.i("OnCreate", "BLE is not available");
            bleEnabled = false;
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
        }

        if (bleEnabled) {
            // if API version is lower than 18 BLE and Estimote can't be used
            if (Smartphone.Companion.getSdkVersion() < 18) {
                bleEnabled = false;
                Toast.makeText(this, "API version: " + Smartphone.Companion.getSdkVersion() + "lower than 18", Toast.LENGTH_SHORT).show();
            }
        }

        // if BLE is available and the user wants to use it
        if (bleEnabled) {
            bluetooth4FeaturesText = getString(R.string.no_features);
            setBLEHandler();

            bleDataSensor.setName(getString(R.string.bleAvailable));
            bleDataSensor.setFeatures(bluetooth4FeaturesText);
            bleDataSensor.enable();
        } else {
            bluetooth4FeaturesText = getString(R.string.no_features);

            bleDataSensor.setName(getString(R.string.bleNotAvailable));
            bleDataSensor.setFeatures(bluetooth4FeaturesText);
            bleDataSensor.disable();
        }
        dataSensorsRecyclerView.notifyDataSensorChanged(bleDataSensor);

        if (!bleEnabled) {
            bluetoothDataSensor.connect();
        }

        soundDataSensor.connect();

        //----------------- External RFID Reader RF Code M220 -----------------------
        rfidDataSensor.setName(getString(R.string.rfidNotDetected));
        rfidDataSensor.setFeatures(getString(R.string.rfidNoFeatures));
        rfidDataSensor.disable();
        rfidDataSensor.setStatusForScreen(getString(R.string.emptyStatus));
        rfidDataSensor.setExtendedStatusForScreen(getString(R.string.emptyStatus));
        dataSensorsRecyclerView.notifyDataSensorChanged(rfidDataSensor);

        // Try to connect to all bonded RFID Readers
        if (bluetoothDataSensor.bluetoothAdapter != null && !bleEnabled) {
            Set<BluetoothDevice> bondedDevices = bluetoothDataSensor.bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                String bluetoothName = device.getName();
                if (bluetoothName.contains("M220")) {
                    String bluetoothMac = device.getAddress();
                    RFIDM220Reader rfidM220Reader = new RFIDM220Reader(this, bluetoothMac);
                    rfidM220Reader.setListener(this);
                    rfidM220Reader.connect();
                }
            }
        }

        // ----------------- External XSens IMU ------------------------------------
        imuxDataSensor.setName(getString(R.string.imuxNotDetected));
        imuxDataSensor.setFeatures(getString(R.string.imuxNoFeatures));
        imuxDataSensor.disable();
        imuxDataSensor.setStatusForScreen(getString(R.string.emptyStatus));
        imuxDataSensor.setExtendedStatusForScreen(getString(R.string.emptyStatus));
        dataSensorsRecyclerView.notifyDataSensorChanged(imuxDataSensor);

        UsbManager USBmanager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION),
                0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        IntentFilter filterDetached = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        Log.i("OnCreate", "Set USB Handler");
        setUSBHandler();
        registerReceiver(usbBroadcastReceiver, filter);
        registerReceiver(usbBroadcastReceiver, filterDetached);

        mXSens = new MTiXSensIMU(this, usbPermissionIntent);
        mXSens.setListener(this);

        if (mUsbDevice == null) {
            mUsbDevice = mXSens.getDevice(USBmanager);
            /* TODO: uncomment below when mUsbDevice != null?
                mXSens.connect(mUsbDevice);
                mXSens.startMeasurements();
             */
        }

        Log.i("OnCreate", "Fin de OnCreate");
    }

    @Override
    protected void onResume() {
        Log.d(Tag, "onResume()");

        super.onResume();

        int delay;

        SystemRequirementsChecker.checkWithDefaultDialogs(this);

        if (bleEnabled) {
            beaconManager.connect(() -> {
                long scanPeriodMillis = 200;  // 5Hz  (maximum scan rate according to SDK and experimental; with 10 Hz or 100ms the result is the same as 5Hz)
                beaconManager.setForegroundScanPeriod(scanPeriodMillis, 0);
                beaconManager.startRanging(region);                         // Scan for iBeacon BLE tags
                scanId_Eddystone = beaconManager.startEddystoneScanning();  // Scan for EddyStone BLE tags
                if (flag_EstimoteTelemetry) {
                    scanId = beaconManager.startTelemetryDiscovery();
                }
            });
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String updateFrequencyDelayTypeKey = getString(R.string.updateFrequencyDelayTypeKey);
        switch (Integer.parseInt(Objects.requireNonNull(pref.getString(updateFrequencyDelayTypeKey, "2")))) {
            case 1:
                delay = SensorManager.SENSOR_DELAY_FASTEST;
                Log.i("OnResume", "Option 1: SENSOR_DELAY_FASTEST");
                break;
            case 2:
                delay = SensorManager.SENSOR_DELAY_GAME;
                Log.i("OnResume", "Option 1: SENSOR_DELAY_GAME");
                break;
            case 3:
                delay = SensorManager.SENSOR_DELAY_NORMAL;
                Log.i("OnResume", "Option 1: SENSOR_DELAY_NORMAL");
                break;
            case 4:
                delay = SensorManager.SENSOR_DELAY_UI;
                Log.i("OnResume", "Option 1: SENSOR_DELAY_UI");
                break;
            default:
                delay = SensorManager.SENSOR_DELAY_GAME;
        }

        // Check if user wants to set the frequency they prefer, in Hz
        String updateFrequencyCustomUpdateRateEnabledKey = getString(R.string.updateFrequencyCustomUpdateRateEnabledKey);
        boolean updateFrequencyCustomUpdateRateEnabled = pref.getBoolean(updateFrequencyCustomUpdateRateEnabledKey, false);
        if (updateFrequencyCustomUpdateRateEnabled) {
            try {
                String updateFrequencyCustomUpdateRateKey = getString(R.string.updateFrequencyCustomUpdateRateKey);
                double customUpdateFrequency = Integer.parseInt(Objects.requireNonNull(pref.getString(updateFrequencyCustomUpdateRateKey, "100")));
                delay = (int) (1 / customUpdateFrequency * 1000000);
            } catch (Exception x) {
                Log.i("Preference", "Update rate in Hz not parsed");
            }
        }

        // Register sensors
        if (accelerometerDataSensor.isAvailable()) {
            accelerometerDataSensor.setSamplingPeriodUs(delay);
            accelerometerDataSensor.startReading();
        }
        if (gyroscopeDataSensor.isAvailable()) {
            gyroscopeDataSensor.setSamplingPeriodUs(delay);
            gyroscopeDataSensor.startReading();
        }
        if (magneticFieldDataSensor.isAvailable()) {
            magneticFieldDataSensor.setSamplingPeriodUs(delay);
            magneticFieldDataSensor.startReading();
        }
        if (pressureDataSensor.isAvailable()) {
            pressureDataSensor.setSamplingPeriodUs(delay);
            pressureDataSensor.startReading();
        }
        if (lightDataSensor.isAvailable()) {
            lightDataSensor.setSamplingPeriodUs(delay);
            lightDataSensor.startReading();
        }
        if (proximityDataSensor.isAvailable()) {
            proximityDataSensor.setSamplingPeriodUs(delay);
            proximityDataSensor.startReading();
        }
        if (relativeHumidityDataSensor.isAvailable()) {
            relativeHumidityDataSensor.setSamplingPeriodUs(delay);
            relativeHumidityDataSensor.startReading();
        }
        if (ambientTemperatureDataSensor.isAvailable()) {
            ambientTemperatureDataSensor.setSamplingPeriodUs(delay);
            ambientTemperatureDataSensor.startReading();
        }
        if (rotationVectorDataSensor.isAvailable()) {
            rotationVectorDataSensor.setSamplingPeriodUs(delay);
            rotationVectorDataSensor.startReading();
        }
        if (cameraDataSensor.isAvailable()) {
            cameraDataSensor.startReading();
        }
        if (gnssDataSensor.isAvailable()) {
            gnssDataSensor.startReading();
        }
        if (wiFiDataSensor.isAvailable()) {
            wiFiDataSensor.startReading();
        }
        if (!bleEnabled && bluetoothDataSensor.isAvailable()) {
            bluetoothDataSensor.startReading();
        }
        if (soundDataSensor.isAvailable()) {
            soundDataSensor.startReading();
        }
        if (!bleEnabled) {
            for (RFIDM220Reader rfidm220Reader : rfidM220Readers) {
                rfidm220Reader.startReading();
            }
        }

        // Show camera basic details
        dataSensorsRecyclerView.notifyDataSensorChanged(cameraDataSensor);

        Preferences preferences = new Preferences(this);
        if (preferences.getCameraEnabled()) {
            final CameraDataSensor cameraDataSensor = this.cameraDataSensor;
            cameraDataSensor.getCamera().start(
                    ImageFormat.JPEG,
                    preferences.getLensFacing(),
                    preferences.getPreviewLocation(),
                    preferences.getPreviewEnabled(),
                    preferences.getShowGuides()
            );

            cameraDataSensor.getCamera().preview.setAlpha(preferences.getPreviewTransparency());
        }
    }

    @Override
    protected void onPause() {
        Log.d(Tag, "onPause()");

        super.onPause();

        if (bleEnabled) {
            beaconManager.stopRanging(region);
            beaconManager.stopEddystoneScanning(scanId_Eddystone);
            if (flag_EstimoteTelemetry) {
                beaconManager.stopTelemetryDiscovery(scanId);
            }
        }

        accelerometerDataSensor.stopReading();
        gyroscopeDataSensor.stopReading();
        magneticFieldDataSensor.stopReading();
        pressureDataSensor.stopReading();
        lightDataSensor.stopReading();
        proximityDataSensor.stopReading();
        relativeHumidityDataSensor.stopReading();
        ambientTemperatureDataSensor.stopReading();
        rotationVectorDataSensor.stopReading();
        cameraDataSensor.stopReading();
        gnssDataSensor.stopReading();
        wiFiDataSensor.stopReading();

        if (!bleEnabled) {
            bluetoothDataSensor.stopReading();
        }

        soundDataSensor.stopReading();

        // TODO: use same interface rest of data sensors use.
        cameraDataSensor.getCamera().close();

        if (!bleEnabled) {
            for (RFIDM220Reader rfidm220Reader : rfidM220Readers) {
                rfidm220Reader.stopReading();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(Tag, "onDestroy()");

        super.onDestroy();

        accelerometerDataSensor.disconnect();
        gyroscopeDataSensor.disconnect();
        magneticFieldDataSensor.disconnect();
        pressureDataSensor.disconnect();
        lightDataSensor.disconnect();
        proximityDataSensor.disconnect();
        relativeHumidityDataSensor.disconnect();
        ambientTemperatureDataSensor.disconnect();
        rotationVectorDataSensor.disconnect();
        cameraDataSensor.disconnect();
        gnssDataSensor.disconnect();
        wiFiDataSensor.disconnect();
        bluetoothDataSensor.disconnect();
        bleDataSensor.disconnect();
        soundDataSensor.disconnect();
        rfidDataSensor.disconnect();
        imuxDataSensor.disconnect();

        if (!bleEnabled) {
            // Disconnect from RFID Reader
            for (RFIDM220Reader rfidm220Reader : rfidM220Readers) {
                rfidm220Reader.disconnect();
            }
        }

        // Disconnect XSens
        mXSens.stopReading();
        unregisterReceiver(usbBroadcastReceiver);
        mXSens.disconnect();

        // Remove timers
        /* TODO: should this code be called?
            if (!bleEnabled) {
                timerBlue.cancel();
            }
         */

        if (secondsLoggingTimer != null) {
            secondsLoggingTimer.cancel();
        }

        if (isTracingEnabled) {
            Debug.stopMethodTracing();
        }
    }

    // endregion

    // region Menu Management

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(Tag, "onCreateOptionsMenu(menu=" + menu + ")");

        getMenuInflater().inflate(R.menu.menu_preferences, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(Tag, "onOptionsItemSelected(item=" + item + ")");

        if (item.getItemId() == R.id.preferences) {
            startActivity(new Intent(MainActivity.this, PreferencesActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    // endregion

    // region User Interface Management

    private void showToast(String message) {
        Log.d(Tag, "showToast(msg=" + message + ")");

        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.show();
    }

    // endregion

    // region Button Handling

    private void setShowSensorFeaturesToggleButtonHandler() {
        Log.d(Tag, "setShowSensorFeaturesToggleButtonHandler()");

        binding.showSensorFeaturesToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding.showSensorFeaturesToggleButton.isChecked()) {
                Log.d(Tag, "- toggle button touched: show sensor features");
                dataSensorsRecyclerView.setShowSensorFeatures(true);
            } else {
                Log.d(Tag, "- toggle button touched: hide sensor features");
                dataSensorsRecyclerView.setShowSensorFeatures(false);
            }
        });
    }

    private void setShowRealTimeDataToggleButtonHandler() {
        Log.d(Tag, "setShowRealTimeDataToggleButtonHandler()");

        binding.showRealTimeDataToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DataSensorsRecyclerViewAdapter dataSensorsRecyclerViewAdapter = (DataSensorsRecyclerViewAdapter) dataSensorsRecyclerView.getAdapter();
            assert dataSensorsRecyclerViewAdapter != null;

            if (binding.showRealTimeDataToggleButton.isChecked()) {
                Log.d(Tag, "- toggle button touched: show real-time data");
                dataSensorsRecyclerView.setShowSensorRealTimeData(true);
            } else {
                Log.d(Tag, "- toggle button touched: hide real-time data");
                dataSensorsRecyclerView.setShowSensorRealTimeData(false);
            }
        });
    }

    private void setSaveLogToggleButtonHandler() {
        Log.d(Tag, "setSaveLogToggleButtonHandler()");

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            finish();
        }

        binding.saveLogToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Check if external storage is available
            boolean externalStorageAvailable;
            boolean externalStorageWriteable;
            String state = Environment.getExternalStorageState();

            if (Environment.MEDIA_MOUNTED.equals(state)) {
                // We can read and write the media
                externalStorageAvailable = true;
                externalStorageWriteable = true;
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                // We can only read the media
                externalStorageAvailable = true;
                externalStorageWriteable = false;
            } else {
                // Something else is wrong. It may be one of many other states, but all we need to
                // know is we can neither read nor write
                externalStorageAvailable = false;
                externalStorageWriteable = false;
            }
            Log.d(Tag, "- externalStorageAvailable: " + externalStorageAvailable);
            Log.d(Tag, "- externalStorageWriteable:" + externalStorageWriteable);

            if (binding.saveLogToggleButton.isChecked()) {
                Log.d(Tag, "- start saving to log");

                long currentTimestampNanoseconds = System.nanoTime();
                if (binding.saveLogToggleButton.isChecked()) {
                    startingTimestampNanoseconds = currentTimestampNanoseconds;
                    Log.d(Tag, "- starting timestamp: " + startingTimestampNanoseconds + " ns");

                    timestamp_Imul_last_update = 0;

                    accelerometerDataSensor.setEpoch(startingTimestampNanoseconds);
                    gyroscopeDataSensor.setEpoch(startingTimestampNanoseconds);
                    magneticFieldDataSensor.setEpoch(startingTimestampNanoseconds);
                    pressureDataSensor.setEpoch(startingTimestampNanoseconds);
                    lightDataSensor.setEpoch(startingTimestampNanoseconds);
                    proximityDataSensor.setEpoch(startingTimestampNanoseconds);
                    relativeHumidityDataSensor.setEpoch(startingTimestampNanoseconds);
                    ambientTemperatureDataSensor.setEpoch(startingTimestampNanoseconds);
                    rotationVectorDataSensor.setEpoch(startingTimestampNanoseconds);
                    cameraDataSensor.setEpoch(startingTimestampNanoseconds);
                }

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
                Date currentDate = new Date();
                String formattedCurrentDate = simpleDateFormat.format(currentDate);

                try {
                    if (externalStorageAvailable) {
                        File path = Environment.getExternalStoragePublicDirectory("LogFiles_GetSensorData");
                        Log.d(Tag, "- path: " + path);
                        if (path.mkdirs()) {
                            Log.d(Tag, "- path created");
                        }
                        File file = new File(path.getAbsolutePath(), "logfile_" + formattedCurrentDate + ".txt");
                        outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file));
                        Log.d(Tag, "- external log file opened for writing");
                    } else {
                        outputStreamWriter = new OutputStreamWriter(openFileOutput("logfile_" + formattedCurrentDate + ".txt", Context.MODE_PRIVATE));
                        Log.d(Tag, "- internal log file opened for writing");
                    }

                    Toast.makeText(getApplicationContext(), getString(R.string.savingSensorData), Toast.LENGTH_SHORT).show();

                    outputStreamWriter.write("% LogFile created by the 'GetSensorData' App for Android.");
                    outputStreamWriter.write("\n% Date of creation: " + currentDate.toString());
                    outputStreamWriter.write("\n% Developed by LOPSI research group at CAR-CSIC, Spain (http://www.car.upm-csic.es/lopsi)");
                    outputStreamWriter.write("\n% Version 2.1 January 2018");
                    outputStreamWriter.write("\n% The 'GetSensorData' program stores information from Smartphone/Tablet internal sensors (Accelerometers, Gyroscopes, Magnetometers, Pressure, Ambient Light, Orientation, Sound level, GPS/GNSS position, WiFi RSS, Cellular/GSM/3G signal strength,...) and also from external devices (e.g. RFCode RFID reader, XSens IMU, or MIMU22BT)");
                    outputStreamWriter.write("\n%\n% Phone used for this logfile:");
                    outputStreamWriter.write("\n% Manufacturer:            \t" + Smartphone.Companion.getManufacturer());
                    outputStreamWriter.write("\n% Model:                   \t" + Smartphone.Companion.getModel());
                    outputStreamWriter.write("\n% API Android version:     \t" + Smartphone.Companion.getSdkVersion());
                    outputStreamWriter.write("\n% Android version Release: \t" + Smartphone.Companion.getReleaseVersion());
                    outputStreamWriter.write("\n%\n% LogFile Data format:");
                    outputStreamWriter.write("\n% Accelerometer data: \t'ACCE;AppTimestamp(s);SensorTimestamp(s);Acc_X(m/s^2);Acc_Y(m/s^2);Acc_Z(m/s^2);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Gyroscope data:     \t'GYRO;AppTimestamp(s);SensorTimestamp(s);Gyr_X(rad/s);Gyr_Y(rad/s);Gyr_Z(rad/s);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Magnetometer data:  \t'MAGN;AppTimestamp(s);SensorTimestamp(s);Mag_X(uT);;Mag_Y(uT);Mag_Z(uT);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Pressure data:      \t'PRES;AppTimestamp(s);SensorTimestamp(s);Pres(mbar);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Light data:         \t'LIGH;AppTimestamp(s);SensorTimestamp(s);Light(lux);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Proximity data:     \t'PROX;AppTimestamp(s);SensorTimestamp(s);prox(?);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Humidity data:      \t'HUMI;AppTimestamp(s);SensorTimestamp(s);humi(Percentage);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Temperature data:   \t'TEMP;AppTimestamp(s);SensorTimestamp(s);temp(Celsius);Accuracy(integer)'");
                    outputStreamWriter.write("\n% Orientation data:   \t'AHRS;AppTimestamp(s);SensorTimestamp(s);PitchX(deg);RollY(deg);YawZ(deg);Quat(2);Quat(3);Quat(4);Accuracy(int)'");
                    outputStreamWriter.write("\n% GNSS/GPS data:      \t'GNSS;AppTimestamp(s);SensorTimeStamp(s);Latit(deg);Long(deg);Altitude(m);Bearing(deg);Accuracy(m);Speed(m/s);SatInView;SatInUse'");
                    outputStreamWriter.write("\n% WIFI data:          \t'WIFI;AppTimestamp(s);SensorTimeStamp(s);Name_SSID;MAC_BSSID;Frequency;RSS(dBm);'");
                    outputStreamWriter.write("\n% Bluetooth data:     \t'BLUE;AppTimestamp(s);Name;MAC_Address;RSS(dBm);'");
                    outputStreamWriter.write("\n% BLE 4.0 data:       \t'BLE4;AppTimestamp(s);iBeacon;MAC;RSSI(dBm);Power;MajorID;MinorID;UUID'");
                    outputStreamWriter.write("\n% BLE 4.0 data:       \t'BLE4;AppTimestamp(s);Eddystone;MAC;RSSI(dBm);instanceID;OptionalTelemetry[voltage;temperature;uptime;count]");
                    outputStreamWriter.write("\n% Sound data:         \t'SOUN;AppTimestamp(s);RMS;Pressure(Pa);SPL(dB);'");
                    outputStreamWriter.write("\n% RFID Reader data:   \t'RFID;AppTimestamp(s);ReaderNumber(int);TagID(int);RSS_A(dBm);RSS_B(dBm);'");
                    outputStreamWriter.write("\n% IMU XSens data:     \t'IMUX;AppTimestamp(s);SensorTimestamp(s);Counter;Acc_X(m/s^2);Acc_Y(m/s^2);Acc_Z(m/s^2);Gyr_X(rad/s);Gyr_Y(rad/s);Gyr_Z(rad/s);Mag_X(uT);;Mag_Y(uT);Mag_Z(uT);Roll(deg);Pitch(deg);Yaw(deg);Quat(1);Quat(2);Quat(3);Quat(4);Pressure(mbar);Temp(Celsius)'");
                    outputStreamWriter.write("\n% IMU MIMU22BT data:  \t'IMUI;AppTimestamp(s);Packet_count;Step_Counter;delta_X(m);delta_Y(m);delta_Z(m);delta_theta(degrees);Covariance4x4[1:10]'");
                    outputStreamWriter.write("\n% POSI Reference:    	\t'POSI;Timestamp(s);Counter;Latitude(degrees); Longitude(degrees);floor ID(0,1,2..4);Building ID(0,1,2..3);'");
                    outputStreamWriter.write("\n% ");
                    outputStreamWriter.write("\n% Note that there are two timestamps: ");
                    outputStreamWriter.write("\n%  -'AppTimestamp' is set by the Android App as data is read. It is not representative of when data is actually captured by the sensor (but has a common time reference for all sensors)");
                    outputStreamWriter.write("\n%  -'SensorTimestamp' is set by the sensor itself (the delta_time=SensorTimestamp(k)-SensorTimestamp(k-1) between two consecutive samples is an accurate estimate of the sampling interval). This timestamp is better for integrating inertial data. \n");
                } catch (Exception exception) {
                    Log.d(Tag, "- error writing data to log file");
                    exception.printStackTrace();
                }

                // Start timer in charge of showing how long the app has been saving to a log file
                setClockHandler();
                secondsLoggingTimer = new Timer("Seconds Logging");
                secondsLoggingTimer.schedule(secondsLoggingTimerTask, 1000, 1000);

                // Start camera sampling
                Preferences preferences = new Preferences(this);
                if (preferences.getCameraEnabled()) {
                    if (cameraDataSensor != null) {
                        cameraDataSensor.startSampling();
                    }
                }
            } else {
                // Stop timer in charge of showing how long the app has been saving to a log file
                secondsLoggingTimer.cancel();

                Log.d(Tag, "- stop logging, close file");
                try {
                    startingTimestampNanoseconds = 0;
                    outputStreamWriter.close();
                    Toast.makeText(getApplicationContext(), getString(R.string.endOfSaving), Toast.LENGTH_SHORT).show();
                } catch (Exception exception) {
                    Log.d(Tag, "- error closing log file");
                    exception.printStackTrace();
                }

                // Stop camera sampling
                Preferences preferences = new Preferences(this);
                if (preferences.getCameraEnabled()) {
                    if (cameraDataSensor != null) {
                        cameraDataSensor.stopExperiment();
                    }
                }
            }
        });
    }

    private void setMarkPositionButtonHandler() {
        Log.d(Tag, "setMarkPositionButtonHandler()");

        binding.markPositionButton.setText(getString(R.string.markPositionFirst));

        binding.markPositionButton.setOnClickListener(view -> {
            if (binding.saveLogToggleButton.isChecked()) {
                Log.d(Tag, "- mark position while saving data to file");
                positionCounter += 1;
                binding.markPositionButton.setText(getString(R.string.markPositionNext, positionCounter + 1));
            } else {
                Log.d(Tag, "- position not marked: not saving data to file");
                if (positionCounter == 0) {
                    binding.markPositionButton.setText(getString(R.string.markPositionFirst));
                } else {
                    binding.markPositionButton.setText(getString(R.string.markPositionNext, positionCounter + 1));
                }
                Toast.makeText(getApplicationContext(), getString(R.string.markPositionStartSaving), Toast.LENGTH_SHORT).show();
            }

            if (binding.saveLogToggleButton.isChecked()) {
                long currentTimestampNanoseconds = System.nanoTime(); // in nano seconds
                if (currentTimestampNanoseconds >= startingTimestampNanoseconds) {
                    timestampNanoseconds = currentTimestampNanoseconds - startingTimestampNanoseconds;
                } else {
                    timestampNanoseconds = (currentTimestampNanoseconds - startingTimestampNanoseconds) + Long.MAX_VALUE;
                }
                timestampSeconds = ((double) (timestampNanoseconds)) * 1e-9;

                try {
                    String logLine = String.format(Locale.US, "\nPOSI;%.3f;%d;%.8f;%.8f;%d;%d", timestampSeconds, positionCounter, 0.0, 0.0, 0, 0);
                    outputStreamWriter.write(logLine);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // endregion

    // region Handler Management

    /**
     * Shows time passed, in seconds, since log saving started. The time is shown in the button.
     */
    private void setClockHandler() {
        Log.d(Tag, "setClockHandler()");

        secondsLoggingTimerTask = new TimerTask() {
            public void run() {
                secondsLoggingHandler.post(() -> {
                    long currentTimestampNanoseconds = System.nanoTime();
                    if (currentTimestampNanoseconds >= startingTimestampNanoseconds) {
                        timestampNanoseconds = currentTimestampNanoseconds - startingTimestampNanoseconds;
                    } else {
                        timestampNanoseconds = (currentTimestampNanoseconds - startingTimestampNanoseconds) + Long.MAX_VALUE;
                    }
                    long currentTimestampSeconds = (long) (((double) (timestampNanoseconds)) * 1e-9);
                    if (binding.saveLogToggleButton.isChecked()) {
                        binding.saveLogToggleButton.setText(getString(R.string.stopSaving, Long.toString(currentTimestampSeconds)));
                    }
                });
            }
        };
    }

    private void setBLEHandler() {
        Log.i("BLE", " START: Set BLE BeaconManager handler");
        beaconManager = new BeaconManager(this);
        region = new Region("Any beacon type", null, null, null);

        // iBeacon tags
        beaconManager.setRangingListener((region, list_BLE) -> {
            int major;
            int minor;
            int rssi;
            int power;
            MacAddress macAddress;
            StringBuilder bleStringBuilder = new StringBuilder();
            String uuid;

            if (!list_BLE.isEmpty()) {

                int bleItems = list_BLE.size();

                for (Beacon mote_BLE : list_BLE) {
                    major = mote_BLE.getMajor();
                    minor = mote_BLE.getMinor();
                    rssi = mote_BLE.getRssi();
                    macAddress = mote_BLE.getMacAddress();
                    power = mote_BLE.getMeasuredPower();
                    uuid = mote_BLE.getProximityUUID().toString();
                    //UUID=mote_BLE.getProximityUUID();
                    String macAddressAsString = macAddress.toStandardString();

                    bleStringBuilder.append("\n\t-").append(macAddressAsString).append("\t\tRSS:").append(rssi).append("dBm").append("\t\tID: ").append(major).append(":").append(minor);
                    if (binding.saveLogToggleButton.isChecked()) {
                        long timestamp_ns_raw = System.nanoTime();
                        if (timestamp_ns_raw >= startingTimestampNanoseconds) {
                            timestampNanoseconds = timestamp_ns_raw - startingTimestampNanoseconds;
                        } else {
                            timestampNanoseconds = (timestamp_ns_raw - startingTimestampNanoseconds) + Long.MAX_VALUE;
                        }
                        timestampSeconds = ((double) (timestampNanoseconds)) * 1E-9;  // nanoseconds to seconds

                        try {
                            // 'BLE4;AppTimestamp(s);"iBeacon";MAC;RSSI(dBm);MajorID;MinorID;'
                            // 'BLE4;AppTimestamp(s);"Eddystone";MAC;RSSI(dBm);instanceID;OptionalTelemetry[voltage;temperature;uptime;count]'
                            String string = String.format(Locale.US, "\nBLE4;%.3f;%s;%s;%d;%d;%d;%d;%s", timestampSeconds, "iBeacon", macAddressAsString, rssi, power, major, minor, uuid);
                            outputStreamWriter.write(string);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                String text = "\tNumber of iBEACON BLE motes: " + bleItems + bleStringBuilder;
                Log.i("BLE Scan", text);
                bleDataSensor.setStatusForScreen(text);
            } else {
                Log.i("BLE Scan", " No BLE motes in range");
                String text = "\tNo iBEACON BLE motes in range";
                bleDataSensor.setStatusForScreen(text);
            }
            dataSensorsRecyclerView.notifyDataSensorChanged(bleDataSensor);
        });

        // Eddystone tags
        beaconManager.setEddystoneListener(eddystoneBeacons -> {
            StringBuilder stringBuilder = new StringBuilder();
            MacAddress macAddress;
            String instanceID;
            int rssi;
            EddystoneTelemetry telemetry;
            int voltage;
            long packetCounter;
            double temperature;
            long uptime;

            if (!eddystoneBeacons.isEmpty()) {

                int eddystoneItems = eddystoneBeacons.size();

                for (Eddystone eddystoneBeacon : eddystoneBeacons) {
                    macAddress = eddystoneBeacon.macAddress;
                    instanceID = eddystoneBeacon.instance;
                    rssi = eddystoneBeacon.rssi;
                    telemetry = eddystoneBeacon.telemetry;
                    if (telemetry != null) {
                        voltage = telemetry.batteryVoltage;
                        temperature = (double) (((int) (telemetry.temperature * 10)) / 10);
                        packetCounter = telemetry.packetCounter;
                        uptime = telemetry.uptimeMillis / (1000 * 3600);  // en horas
                    } else {
                        voltage = -1;
                        temperature = -1;
                        packetCounter = -1;
                        uptime = -1;
                    }
                    String macAddressAsString = macAddress.toStandardString();

                    stringBuilder.append("\n\t-").append(macAddressAsString).append(",\t\tRSS:").append(rssi).append("dBm").append(",\t\tID: ").append(instanceID).append("\n  Vols:").append(voltage).append("\tTemp:").append(temperature).append("\tUpHours:").append(uptime).append("\tCount:").append(packetCounter);

                    if (binding.saveLogToggleButton.isChecked()) {
                        long timestamp_ns_raw = System.nanoTime();
                        if (timestamp_ns_raw >= startingTimestampNanoseconds) {
                            timestampNanoseconds = timestamp_ns_raw - startingTimestampNanoseconds;
                        } else {
                            timestampNanoseconds = (timestamp_ns_raw - startingTimestampNanoseconds) + Long.MAX_VALUE;
                        }
                        timestampSeconds = ((double) (timestampNanoseconds)) * 1E-9;

                        try {
                            // 'BLE4;AppTimestamp(s);"iBeacon";MAC;RSSI(dBm);MajorID;MinorID;'
                            // 'BLE4;AppTimestamp(s);"Eddystone";MAC;RSSI(dBm);instanceID;OptionalTelemetry[voltage;temperature;uptime;count]'
                            String string = String.format(Locale.US, "\nBLE4;%.3f;%s;%s;%d;%s;%d;%.1f;%d;%d", timestampSeconds, "Eddystone", macAddressAsString, rssi, instanceID, voltage, temperature, uptime, packetCounter);
                            outputStreamWriter.write(string);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                String text = "\tNumber of EDDYSTONE BLE motes: " + eddystoneItems + stringBuilder;
                Log.i("Eddystone BLE Scan", text);
                bleDataSensor.setExtendedStatusForScreen(text);
            } else {
                Log.i("Eddystone BLE Scan", " No EDDYSTONE BLE motes in range");
                String text = "\tNo EDDYSTONE BLE motes in range";
                bleDataSensor.setExtendedStatusForScreen(text);
            }
            dataSensorsRecyclerView.notifyDataSensorChanged(bleDataSensor);
        });

        if (flag_EstimoteTelemetry) { // Do not create listener unless telemetry requires it with Estimote's special format
            beaconManager.setTelemetryListener(telemetryList -> {
                if (!telemetryList.isEmpty()) {
                    int telemetryListItems = telemetryList.size();
                    Log.i("telemetries BLE Scan", " BLE Telemetry Items: " + telemetryListItems);

                    for (EstimoteTelemetry telemetryItem : telemetryList) {
                        Log.i("TELEMETRY", "BLE beaconID: " + telemetryItem.deviceId +
                                ", temperature: " + telemetryItem.temperature + " °C" +
                                ", batteryPercentage: " + telemetryItem.batteryPercentage + " %" +
                                ", pressure: " + telemetryItem.pressure + " mbar" +
                                ", rssi: " + telemetryItem.rssi + " dB" +
                                ", ambientLight: " + telemetryItem.ambientLight + " lux");
                    }
                } else {
                    Log.i("telemetries BLE Scan", " No BLE telemetries");
                }
            });
        }

        Log.i("BLE", "END: Set BLE BeaconManager handler");
    }

    void setUSBHandler() {
        Log.d(Tag, "setUSBHandler()");

        usbBroadcastReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice localDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (localDevice != null) {
                                if (!mXSens.connect(mUsbDevice)) {
                                    Log.e("USB Receiver", "MTi not Responding");
                                    showToast("Permission: MTi not Responding");
                                } else {
                                    mXSens.goToConfig();  // --- config-----
                                    byte[] Period = new byte[2];  // --- measurement freq 100 Hz-----
                                    Period[0] = (byte) 0x04;
                                    Period[1] = (byte) 0x80;    // freq=115200/period: (0x0480->100 Hz)  (0x0280 -> 180 Hz)
                                    mXSens.Setting(MTiXSensIMU.MID_SetPeriod, Period);
                                    byte[] OutputMode = new byte[2];  // --- Set Output Mode-----
                                    OutputMode[1] = (byte) 0x07;    // Set on: Temperature data, Calibrated Data and Orientation data (pag 20 manual xsens)
                                    mXSens.Setting(MTiXSensIMU.MID_SetOutputMode, OutputMode);
                                    byte[] OutputSettings = new byte[4];  // --- Set Output Settings-----
                                    OutputSettings[0] = (byte) 0x00;
                                    OutputSettings[1] = (byte) 0x00;    // Set on: Sample Counter, Quaternion, AccGyrMag calibrated, Float output,... (pag 21 manual xsens)
                                    OutputSettings[2] = (byte) 0x00;
                                    OutputSettings[3] = (byte) 0x01;
                                    mXSens.Setting(MTiXSensIMU.MID_SetOutputSettings, OutputSettings);
                                    mXSens.goToMeasurement();  // --- measurement-----
                                    mXSens.startMeasurements(); // -------Start Mti Reading-----
                                    Log.i("USB Receiver", "MTi detected and Start Reading");
                                    //	showToast("Permission: MTi detected and Start Reading");
                                }
                            }
                        } else {
                            mUsbDevice = null;
                            Log.e("USB Receiver", "MTi detected, but permission not granted");
                            showToast("Permission: MTi detected, but permission not granted");
                        }
                    }
                }

                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        if (mXSens != null) {
                            mXSens.stopReading();
                            mXSens.disconnect();
                        }
                        USBConnected = false;
                        Log.e("USB Receiver", "USB disconnected");
                        mUsbDevice = null;
                    }
                }
            }
        };
    }

    // endregion

    // region DataSensor Management

    @Override
    public void onDataSensorConnected(@NotNull DataSensor dataSensor) {
        Log.v(Tag, "onDataSensorConnected(dataSensor=" + dataSensor + ")");

        DataSensorProxy dataSensorProxy = null;

        switch (dataSensor.getType()) {
            case RadioFrequencyIdentification:
                RFIDM220Reader rfidM220Reader = (RFIDM220Reader) dataSensor;
                rfidM220Reader.startReading();
                rfidM220Readers.add(rfidM220Reader);
                dataSensorProxy = rfidDataSensor;
                break;
            case InertialMeasurementUnitIMUX:
                dataSensorProxy = imuxDataSensor;
                break;
        }

        if (dataSensorProxy != null) {
            dataSensorProxy.setName(dataSensor.getName());
            dataSensorProxy.setFeatures(dataSensor.getFeatures());
            dataSensorProxy.enable();
            dataSensorsRecyclerView.notifyDataSensorChanged(dataSensorProxy);
        }
    }

    @Override
    public void onDataSensorDisconnected(@NotNull DataSensor dataSensor) {
        Log.v(Tag, "onDataSensorDisconnected(dataSensor=" + dataSensor + ")");

        DataSensorProxy dataSensorProxy = null;

        if (dataSensor.getType() == DataSensorType.InertialMeasurementUnitIMUX) {
            // TODO: is this the best place to have this flag?
            USBConnected = false;
            // TODO: is this the best place to do this toast?
            showToast("XSens Handler: USB disconnected");
            dataSensorProxy = imuxDataSensor;
        }

        // TODO: generalize for external sensors, now it works only for IMUX
        if (dataSensorProxy != null) {
            dataSensorProxy.setName(getString(R.string.xsensNotDetected));
            dataSensorProxy.setFeatures(getString(R.string.xsensNoFeatures));
            dataSensorProxy.disable();
            dataSensorProxy.setStatusForScreen(getString(R.string.emptyStatus));
            dataSensorProxy.setExtendedStatusForScreen(getString(R.string.emptyStatus));
            dataSensorsRecyclerView.notifyDataSensorChanged(dataSensorProxy);
        }
    }

    @Override
    public void onDataSensorChanged(@NotNull DataSensor dataSensor) {
        Log.v(Tag, "onDataSensorChanged(dataSensor=" + dataSensor + ")");

        int dataSensorRecyclerViewIndex = dataSensorsRecyclerViewItems.indexOf(dataSensor);
        String prologue = "";

        DataSensorProxy dataSensorProxy = null;

        switch (dataSensor.getType()) {
            case RadioFrequencyIdentification:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.
                        append(" ").
                        append(getString(R.string.rfidConnectedReaders, rfidM220Readers.size()));
                for (RFIDM220Reader rfidm220Reader : rfidM220Readers) {
                    stringBuilder.
                            append("\n").
                            append(" - ").
                            append(rfidm220Reader.name);
                }
                prologue = stringBuilder.toString();
                dataSensorProxy = rfidDataSensor;
                break;
            case InertialMeasurementUnitIMUX:
                dataSensorProxy = imuxDataSensor;
                break;
        }

        String status = dataSensor.getStatus(DataSensorStatusDestination.Screen);
        if (prologue.equals("") && status.equals("")) {
            return;
        }
        final String statusForScreen = prologue + "\n" + status;

        if (dataSensorsRecyclerView.getShowSensorRealTimeData()) {
            if (dataSensorProxy == null) {
                dataSensorsRecyclerView.updateStatus(dataSensorRecyclerViewIndex, statusForScreen);
            } else {
                dataSensorProxy.setStatusForScreen(statusForScreen);
                dataSensorsRecyclerView.notifyDataSensorChanged(dataSensorProxy);
            }
        }
    }

    // endregion
}
