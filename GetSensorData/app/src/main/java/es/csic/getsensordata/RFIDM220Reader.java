package es.csic.getsensordata;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;

import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

/*
 * # Example of use
 *
 * 1. First Create an "RFIDM220Reader" object:
 *
 *     ```java
 *     RFIDM220Reader rfidM220Reader = new RFIDM220Reader(handlerRFID, bluetoothMAC);
 *     ```
 *
 *     where `handlerRFID` is a `Handler` object that processes (e.g. to update your UI with RFID
 *     data) the data sent by the object in a message. You have to create this code:
 *
 *     ```java
 *     Handler handlerRFID = new Handler() {
 *	       @Override
 *		   public void handleMessage(Message msg) {
 *		       Bundle data = msg.getData();
 *			   String messageType = data.getString("MessageType");
 *			   String readerName = data.getString("ReaderName");
 *			   int rssA = data.getInt("RSS_A");
 *			   int rssB = data.getInt("RSS_B");
 *			   long tagID = data.getLong("TagID");
 *			   // Do something with this data (e.g. update your UI)
 *		   }
 *	   }
 *     ```
 *
 *     `bluetoothMAC` is the MAC address of the RFID reader.
 *
 * 2. Connect a socket to the Bluetooth RFID reader device:
 *
 *     ```java
 *     rfidM220Reader.connect();
 *     ```
 *
 * 3. Start reading the RFID reader (put the reader in Measurement mode):
 *
 *     ```java
 *     rfidM220Reader.startReading();
 *     ```
 *
 * 4. Now, all the processing of data is done in the handlerRFID (in your activity).
 *
 * 5. When you do not need the RFID reader anymore stop and disconnect it:
 *
 *     ```java
 *     rfidM220Reader.stopReading();
 *     rfidM220Reader.disconnect();
 *     ```
 */
public class RFIDM220Reader extends DataSensor {
    private static final String TAG = "RFIDM220Reader";
    public static final String MESSAGE_TYPE = "MessageType";
    public static final String MESSAGE_TYPE_CONNECT = "Connect";
    public static final String MESSAGE_TYPE_RFID_DATA = "RFID_Data";
    public static final String READER_NAME = "ReaderName";
    public static final String CONNECTED = "Connected";
    public static final String RSS_A = "RSS_A";
    public static final String RSS_B = "RSS_B";
    public static final String TAG_ID = "TagID";

    public String name;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread connectThread;
    private Thread readThread;
    private Handler handler;
    private String bluetoothDeviceName;
    private UUID uuid;
    private boolean uuidObtained = false;
    private boolean readingThread = false;
    boolean socketConnected = false;
    private boolean unableToConnect = false;
    private Bundle data;

    // region Class Initializer

    /**
     * Class initializer.
     *
     * @param context            App context.
     * @param bluetoothDeviceMac MAC address of the bluetooth device.
     */
    public RFIDM220Reader(Context context, String bluetoothDeviceMac) {
        super(context, DataSensorType.RadioFrequencyIdentification, 0);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetoothDeviceMac);
            bluetoothDeviceName = bluetoothDevice.getName();
            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                ParcelUuid[] uuids = bluetoothDevice.getUuids();
                if (uuids.length > 0) {
                    uuid = uuids[0].getUuid();
                    uuidObtained = true;
                    Log.i(TAG, "Instance created. UUID: " + uuid);
                    setHandler();
                }
            }
        } else {
            Log.e(TAG, "Bluetooth not activated");
        }
    }

    // endregion

    // region Public Interface

    /**
     * Connect to RFID reader via Bluetooth.
     *
     * This is how I do it, and it works perfectly on the S3:
     *
     * socket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
     *
     * This is how Francisco does it for the LPMS-B:
     *
     * socket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(INSECURE_UUID);
     *
     * According to this post: http://stackoverflow.com/questions/12274210/android-bluetooth-spp-with-galaxy-s3
     * an Android bug exists on Galaxy S3 that makes Bluetooth connection difficult. They solve it this way:
     *
     * device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress);
     * Method method = device.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
     * socket = (BluetoothSocket)method.invoke(device, Integer.valueOf(1));
     *
     *	Method method = RFID_BluetoothDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
     *	socket = (BluetoothSocket)method.invoke(RFID_BluetoothDevice, Integer.valueOf(1));
     */
    public void connect() {
        if (uuidObtained) {
            try {
                bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                Log.i(TAG, "RFIDM220Reader OK: Socket created");
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            connectThread = new Thread(() -> {
                try {
                    // Block until server connection accepted.
                    bluetoothAdapter.cancelDiscovery(); // Recommended before calling to connect()
                    bluetoothSocket.connect(); // Blocking function

                    socketConnected = true;
                    Log.i(TAG, "RFIDM220Reader OK: Socket created");
                    // Send notification to handler in charge of UI update
                    Message message = new Message();
                    Bundle data = new Bundle();
                    data.putString(MESSAGE_TYPE, "Connect");
                    data.putString(READER_NAME, bluetoothDeviceName);
                    data.putBoolean(CONNECTED, true);
                    message.setData(data);
                    handler.sendMessage(message);
                } catch (IOException e) {
                    //System.out.println("RFIDM220Reader ERROR: Socket NOT connected");
                    //Log.e(TAG, "ERROR: Socket NOT connected", e);  // Show message with red text in LogCat
                    unableToConnect = true;
                    Log.e(TAG, "Socket not connected");
                    System.out.println("RFIDM220Reader error: Socket not connected");
                }
            });
            if (!connectThread.isAlive()) {
                connectThread.setName("Connect thread - RFIDM220Reader");
                connectThread.start();
            }
        } else {
            Log.e(TAG, "No UUID obtained");
        }
    }

    /**
     * Disconnect from RFID reader via Bluetooth.
     */
    public void disconnect() {
        if (socketConnected) {
            try {
                if (connectThread.isAlive()) {
                    connectThread.interrupt();
                }
                Log.i(TAG, "disconnect: ConnectThread interrupted");
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            try {
                Log.i(TAG, "disconnect: socket closed on disconnect method (start)");
                if (bluetoothSocket.isConnected()) {
                    bluetoothSocket.close();
                }
                Log.i(TAG, "disconnect: socket closed on disconnect method (end)");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "No disconnection done since it was not connected");
        }
    }

    /**
     * Start reading RFID data.
     */
    public void startReading() {
        if (uuidObtained) {
            readThread = new Thread(() -> {
                readingThread = true;
                try {
                    // Put RFID reader in measurement mode
                    // Configure RFID reader
                    while (!socketConnected && readingThread && !unableToConnect) {
                        Log.i(TAG, "=============Loop=====================" + bluetoothDeviceName);
                        Thread.sleep(1000);
                    } // Wait for socket (in connect thread) to actually connect
                    Log.i(TAG, "=============Start Reading=====================" + bluetoothDeviceName);

                    if (socketConnected) {
                        // Assign input and output streams
                        try {
                            inputStream = bluetoothSocket.getInputStream();
                            outputStream = bluetoothSocket.getOutputStream();
                        } catch (IOException e) {
                            Log.e("RFIDM220Reader", "Streams not created", e);
                            return;
                        }

                        Log.i(TAG, "Config M220 reader" + bluetoothDeviceName);
                        sendDataToSocket("M,0\r");
                        sendDataToSocket("M,0\r");
                        sendDataToSocket("G,LOCATE,4\r");
                        sendDataToSocket("S,2\r");
                        //	SendDataSocket("YT,20\r");
                        Log.i(TAG, "Put RFID reader in measurement mode" + bluetoothDeviceName);
                        sendDataToSocket("M,433\r");
                        sendDataToSocket("M,433\r");

                        // Listen the Incoming DATA
                        Log.i(TAG, "Start listening for messages" + bluetoothDeviceName);
                        listenForData();   // Blocking or very long (infinite) process
                        Log.i(TAG, "End listening for messages" + bluetoothDeviceName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "listenForDataRFID Exception", e);
                }
            });

            if (!readThread.isAlive()) {
                connectThread.setName("Read thread - RFIDM220Reader");
                readThread.start();
            }
        }
    }

    /**
     * Stop reading RFID data.
     */
    public void stopReading() {
        readingThread = false;  // to signal loop break in reading thread
        if (socketConnected) {
            try {
                if (readThread.isAlive()) {
                    readThread.interrupt();
                }
                Log.i(TAG, "stopReading: reading thread interrupted");
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            Log.i(TAG, "stopReading: send M,0");
            sendDataToSocket("M\r");
            sendDataToSocket("M,0\r");
        } else {
            Log.i(TAG, "No stopping done since it was not connected nor reading");
        }
    }

    // endregion

    // region Handler Management

    private void setHandler() {
        Log.d(TAG, "setHandler()");

        class HandlerCallback implements Handler.Callback {
            final RFIDM220Reader dataSensor;

            public HandlerCallback(RFIDM220Reader dataSensor) {
                this.dataSensor = dataSensor;
            }

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                Log.i(TAG, "Handle sensor message");
                Bundle data = msg.getData();
                String messageType = data.getString(RFIDM220Reader.MESSAGE_TYPE);
                String readerName = data.getString(RFIDM220Reader.READER_NAME);
                if (messageType != null && messageType.equals(RFIDM220Reader.MESSAGE_TYPE_CONNECT)) {
                    boolean connected = data.getBoolean(RFIDM220Reader.CONNECTED);
                    if (connected) {
                        name = readerName;
                        if (getListener() != null) {
                            getListener().onDataSensorConnected(dataSensor);
                        }
                    }
                }
                if (messageType != null && messageType.equals(RFIDM220Reader.MESSAGE_TYPE_RFID_DATA)) {
                    dataSensor.data = data;
                    if (getListener() != null) {
                        getListener().onDataSensorChanged(dataSensor);
                    }
                }
                return false;
            }
        }
        handler = new Handler(new HandlerCallback(this));
    }

    // endregion

    // region Data Communication

    /**
     * Send data to RFID reader socket.
     *
     * @param command Command sent to the RFID reader data socket.
     */
    private void sendDataToSocket(String command) {
        byte[] buffer = command.getBytes();
        try {
            outputStream.write(buffer);
            Log.i(TAG, "Data " + command + " sent to socket");
        } catch (IOException e) {
            Log.e(TAG, "Data send failed", e);
        }
    }

    /**
     * Get data from RFID reader.
     */
    private void listenForData() {
        byte currentByte;
        int byteRead;
        boolean inSync = false;
        StringBuilder line = new StringBuilder();
        try {
            while (readingThread) {
                byteRead = inputStream.read();  // read only 1 byte (blocking function)
                currentByte = (byte) byteRead;
                if (!inSync && currentByte == 13) { // reset line and start collecting lines
                    line = new StringBuilder();
                    inSync = true;
                    Log.i(TAG, "Line synchronized");
                } else {
                    if (inSync) {
                        if (currentByte != 13) { // Continue filling current line
                            line.append((char) currentByte);
                        } else { // end of line found, parse it
                            // Log.i("listenForData", "Line read: " + linea);
                            Bundle data = parseLine(line.toString());   // .......PARSE......data contains RSS_A, RSS_B and TagID...
                            int rssA = data.getInt(RSS_A);
                            int rssB = data.getInt(RSS_B);
                            long tagId = data.getLong(TAG_ID);

                            // Send RFID data to handler in UI thread to present them
                            Log.i(TAG, "Tag ID: " + tagId + " RSS_A: " + rssA + " RSS_B: " + rssB);
                            Message message = new Message();
                            data.putString(MESSAGE_TYPE, "RFID_Data");
                            data.putString(READER_NAME, bluetoothDeviceName);
                            message.setData(data);
                            handler.sendMessage(message);
                            // reset line to fetch next one
                            line = new StringBuilder();
                        }
                    }
                }
            }  // end-while
            Log.i(TAG, "Exit listenForData loop");
        } catch (IOException e) {
            Log.i(TAG, "Message reception failed.", e);
        }
    }

    // endregion

    // region Tools

    /**
     * Parse text line received from the RFID reader.
     *
     * @param line Text line to parse.
     *
     * @return text line parsed
     */
    private Bundle parseLine(String line) {   //,long ID, int RSS_A, int RSS_B)
        boolean wrongLine = false;
        Bundle bundle = new Bundle();

        // Process line
        int posH = line.indexOf("H,");
        int posG = line.indexOf(",GLOCATE,");
        int posP = line.toUpperCase().indexOf(",P"); // sometimes it P, others p
        int posA = line.indexOf(",A");
        int posB = line.indexOf(",B");

        // Multiple checks
        if (line.length() < 23 || posH != 0 || posG == -1 || posP == -1 || (posA == -1 && posB == -1)) {
            wrongLine = true;  // continue: "Pass control to next iteration of for or while loop"
        }

        if (!wrongLine) {
            // Extract tag ID
            long tagId = 0;
            try {
                tagId = Long.parseLong(line.substring(posH + 2, posH + 10));
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            int ss1 = -1; // -1 means there is no valid RSS reading
            int ss2 = -1;
            if (posA != -1) { // if A -> 2 or 3 digits number (between 40 and 120 dB), (if 3 digits, first one is 1; one hundred and something)
                if ((line.charAt(posA + 2)) == '1') { // read 3 digits number
                    try {
                        ss1 = Integer.parseInt(line.substring(posA + 2, posA + 5)); // SS1
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else { // read 2 digits number
                    try {
                        ss1 = Integer.parseInt(line.substring(posA + 2, posA + 4)); // SS1
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }

            if (posB != -1) {
                if ((line.charAt(posB + 2)) == '1') { // read 3 digits number
                    try {
                        ss2 = Integer.parseInt(line.substring(posB + 2, posB + 5)); // SS2
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else { // read 2 digits number
                    try {
                        ss2 = Integer.parseInt(line.substring(posB + 2, posB + 4)); // SS2
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }

            // last check
            if (ss1 > 0 || ss2 > 0) {
                bundle.putInt(RSS_A, ss1);
                bundle.putInt(RSS_B, ss2);
                bundle.putLong(TAG_ID, tagId);
            }
        }
        return bundle;
    }

    // endregion

    // region DataSensor

    @Override
    public boolean isAvailable() {
        return socketConnected;
    }

    @Override
    public boolean getOffersExtendedStatus() {
        return false;
    }

    @NotNull
    @Override
    public String getPrefix() {
        return "RFID";
    }

    @NotNull
    @Override
    public String getName() {
        return getContext().getString(R.string.rfidDetected);
    }

    @NotNull
    @Override
    public String getFeatures() {
        return getContext().getString(R.string.rfidFeatures);
    }

    @NotNull
    @Override
    public String getStatusForScreen() {
        setCounter(getCounter() + 1);

        double timestamp = System.nanoTime();
        // initial timestamp set when user starts saving data to file
        if (timestamp >= getPreviousSensorTimestampInSeconds()) {
            timestamp = timestamp - getPreviousSensorTimestampInSeconds();
        } else {
            timestamp = (timestamp - getPreviousSensorTimestampInSeconds()) + Long.MAX_VALUE;
        }
        timestamp = timestamp * 1e-9; // from nanoseconds to seconds

        assert data != null;
        int RSS_A = data.getInt(RFIDM220Reader.RSS_A);
        int RSS_B = data.getInt(RFIDM220Reader.RSS_B);
        long TagID = data.getLong(RFIDM220Reader.TAG_ID);

        // Real-time tag Data:
        String status = " Real-time RFID data:\n\t-Reader: " + name + "\t Tag ID: " + TagID + "\n\t\tRSS_A: -" + RSS_A + "dBm  RSS_B: -" + RSS_B + " dBm";
        // TODO: is counter being increased twice?
        // TODO: should this go with the timestamp code, above?
        setCounter(getCounter() + 1);
        if (timestamp - getPreviousSensorTimestampInSeconds() > 0.005) {
            float measurementFrequency = (float) (0.99 * getMeasurementFrequency() + 0.01 / (timestamp - getPreviousSensorTimestampInSeconds()));
            setMeasurementFrequency(measurementFrequency);
        }
        setPreviousSensorTimestampInSeconds(timestamp);
        status = status + String.format(Locale.US, "\n\t\t\t\t\t\t\t\tFreq: %5.0f Hz ", getMeasurementFrequency());

        return status;
    }

    @NonNull
    @Override
    public String getExtendedStatusForScreen() {
        return getContext().getString(R.string.emptyStatus);
    }

    @NotNull
    @Override
    public String getStatusForLog() {
        int readerNumber = Integer.parseInt(name.substring(name.length() - 2));
        assert data != null;
        long TagID = data.getLong(RFIDM220Reader.TAG_ID);
        return String.format(Locale.US, "\nRFID;%.3f;%d;%d;%s;%s", getSecondsFromEpoch(), readerNumber, TagID, RSS_A, RSS_B);
    }

    // endregion
}
