package es.csic.getsensordata;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;

import es.csic.getsensordata.data_sensors.definition.DataSensor;
import es.csic.getsensordata.data_sensors.definition.DataSensorType;

/*
 * Class that handles the USB communication and parse of the data coming from an MTi IMU from XSens
 * The IMU must be previously configured to submit, Temperature, Acc, Gyr, Mag, Quaternions (all in
 * IEEE single precision float) and a 2 bytes counter (in total 58 bytes of information). It has
 * been tested at 100 Hz without lost information. Other configurations and support for other
 * devices will be added in the future.
 *
 * For accessing the MTi it is necessary to:
 *
 * - Create a handler to manage the messages from the class:
 *
 *   private void createMTiHandler(){
 *	   handlerMTi = new Handler() {
 *	     @Override
 *	   	 public void handleMessage(Message msg) {
 *	   	   // handle MTi data and connection messages
 *	   	   Bundle data = msg.getData();
 *	   	   String messageType = data.getString(MESSAGE_TYPE);
 *	   	   String readerName = data.getString(READER_NAME);
 *	   	   if (messageType.equals(CONNECTED)) {...
 *	   			dataIMU.acceleration[0] = data.getFloat("Accelerations_x");
 *	   			dataIMU.acceleration[1] = data.getFloat("Accelerations_y");
 *	   			dataIMU.acceleration[2] = data.getFloat("Accelerations_z");
 *	   			dataIMU.turnRate[0] = data.getFloat("TurnRates_x");
 *	   			dataIMU.turnRate[1] = data.getFloat("TurnRates_y");
 *	   			dataIMU.turnRate[2] = data.getFloat("TurnRates_z");
 *	   			dataIMU.magneticField[0] = data.getFloat("MagneticFields_x");
 *	   			dataIMU.magneticField[1] = data.getFloat("MagneticFields_y");
 *	   			dataIMU.magneticField[2] = data.getFloat("MagneticFields_z");
 *	   			dataIMU.temperature = data.getFloat("Temperature");
 *	   			dataIMU.counter = data.getShort("Counter");
 *	   			dataIMU.time = data.getLong("Time");
 *         }
 *	   	 }
 *	   }
 *   }
 *
 *   - Create the UsbManager.
 *
 *     manager = (UsbManager)getSystemService(Context.USB_SERVICE);
 *
 *   - Create the request permission broadcast:
 *
 *	   mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
 *	   IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
 *	   registerReceiver(mUsbReceiver, filter);
 *
 *	   and the broadcaster:
 *
 *	   private static final String ACTION_USB_PERMISSION = "com.csic.xoom_usb.USB_PERMISSION";
 *
 *	   public final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
 *	     public void onReceive(Context context, Intent intent) {
 *	       String action = intent.getAction();
 *	       if (ACTION_USB_PERMISSION.equals(action)) {
 *	         synchronized (this) {
 *	           UsbDevice localDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
 *             if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
 *               //If the permission was granted
 *             } else{
 *               //If the permission was denied
 *             }
 *           }
 *         }
 *       }
 *     };
 *
 * - Create MTi element
 *
 *   XSens = new MTi_XSens_IMU(handlerMTi, mPermissionIntent);
 *
 * - Get the Device (if permission is needed the program will call the broadcast receiver, a system
 *   call)
 *
 *   mUsbDevice = XSens.getDevice(manager);
 *
 * - Connect to the Device(if permission is needed, this step must be done after the broadcast
 *   receiver is called or it will not connect and return false)
 *
 *   XSens.connect(mUsbDevice); // return true if correctly connected
 *
 * - Start the measurements
 *
 *   XSens.startMeasurements();
 *
 * The Manifest must include:
 *
 * <uses-feature android:name="android.hardware.usb.host" />
 *
 * before the uses-sdk, and the min sdk must be at least 12.
 */
public class MTiXSensIMU extends DataSensor {
    private static final String TAG = "MTi XSens";
    public static final String MESSAGE_TYPE = "MessageType";
    public static final String MESSAGE_TYPE_CONNECT = "Connect";
    public static final String READER_NAME = "ReaderName";
    public static final String CONNECTED = "Connected";

    private static final int VENDOR_ID = 0x0403;
    private static final int PRODUCT_ID = 0x0D38B;
    private static final short TEMPERATURE_INDEX = 4;
    private static final short ACCELERATION_INDEX = 8;
    private static final short TURN_RATE_INDEX = 20;
    private static final short MAGNETIC_FIELD_INDEX = 32;
    private static final short QUATERNIONS_INDEX = 44;
    private static final short COUNTER_INDEX = 60;
    private static final int PACKAGE_SIZE = 63;
    private static final int MAX_PACKAGES = 100; // 100 * 1 * 1: 10 minutes data, 1 seg

    private boolean connected = false;
    private Handler handler;
    private UsbManager manager;
    private Thread readingThread;
    private boolean readingIMU = false;
    private final String name = "XSens MTi IMU";
    private UsbEndpoint outputEndPoint, inputEndPoint;
    private UsbInterface usbInterface;
    private UsbDeviceConnection connection;
    private final PendingIntent pendingIntent;
    private int receivedPackages = 0;
    private final byte dataLength = (byte) (PACKAGE_SIZE - 5);
    private final int maxErrors = 30;
    private final IMUData[] receivedData;
    private Bundle data;

    public static byte preamble = (byte) 0xFA;
    public static byte BID = (byte) 0xFF;
    public static byte MID_MTData = 50;
    public static byte MID_goToConfig = 48;
    public static byte MID_goToMeasurement = 16;
    public static byte MID_reqDID = 0;
    public static byte MID_reqProductCode = 28;
    public static byte MID_reqBaudRate = 24;
    public static byte MID_SetPeriod = 4;
    public static byte MID_SetOutputMode = (byte) 208;
    public static byte MID_SetOutputSettings = (byte) 210;

    // TODO: should be out of here when DataSensor provides this funcionality.
    double timestamp_Imux_last = 0;

    /**
     * Class initializer.
     *
     * @param context       App context.
     * @param pendingIntent Pending intent for handling the permission request (the broadcast
     *                      must be implemented in the main).
     */
    public MTiXSensIMU(Context context, PendingIntent pendingIntent) {
        super(context, DataSensorType.InertialMeasurementUnitIMUX, 0);
        this.pendingIntent = pendingIntent;
        this.receivedData = new IMUData[MAX_PACKAGES];
        setHandler();
    }

    /**
     * Scan for the MTi IMU, if it is detected, request permission to the UsbDevice (a broadcast
     * receiver must be implemented in the main) and return the UsbDevice, null if not detected.
     *
     * @param manager Must provide the UsbManager: UsbManager manager = (UsbManager)getSystemService(Context.USB_SERVICE)
     */
    public UsbDevice getDevice(UsbManager manager) {

        this.manager = manager;

        HashMap<String, UsbDevice> deviceList = this.manager.getDeviceList();
        for (UsbDevice usbDevice : deviceList.values()) {
            Log.i(TAG, "USB device detected");
            if (usbDevice.getVendorId() == VENDOR_ID & usbDevice.getProductId() == PRODUCT_ID) {
                if (pendingIntent != null)
                    this.manager.requestPermission(usbDevice, pendingIntent);
                return usbDevice;
            }
        }
        Log.e(TAG, "USB not connected");
        return null;
    }

    /**
     * Initiate connection with the device, setting the data rate and sending a message to the
     * handler indicating the connection (MESSAGE_TYPE -> CONNECTED, READER_NAME -> self.name,
     * "Connected" -> true)
     *
     * @param usbDevice of the MTi
     * @return true if connected, false otherwise
     */
    public boolean connect(UsbDevice usbDevice) {
        int count = usbDevice.getInterfaceCount();
        for (int ii = 0; ii < count; ii++) {
            usbInterface = usbDevice.getInterface(ii);
            int endpointCount = usbInterface.getEndpointCount();
            for (int jj = 0; jj < endpointCount; jj++) {
                if (usbInterface.getEndpoint(jj).getDirection() == UsbConstants.USB_DIR_OUT) {
                    outputEndPoint = usbInterface.getEndpoint(jj);
                } else {
                    inputEndPoint = usbInterface.getEndpoint(jj);
                }
            }
        }
        connection = manager.openDevice(usbDevice);
        if (connection == null)
            return false;

        if (connection.claimInterface(usbInterface, false)) {
            int[] sentSize = new int[4];
            sentSize[0] = connection.controlTransfer(0x40, 0, 0, 0, null, 0, 0); // reset
            sentSize[1] = connection.controlTransfer(0x40, 0, 1, 0, null, 0, 0); // clear Rx
            sentSize[2] = connection.controlTransfer(0x40, 0, 2, 0, null, 0, 0); // clear Tx
            sentSize[3] = connection.controlTransfer(0x40, 0x03, 0x001A, 0, null, 0, 0); //115200 baud rate
            Message message = new Message();
            Bundle data = new Bundle();
            data.putString(MESSAGE_TYPE, "Connect");
            data.putString(READER_NAME, name);
            data.putBoolean(CONNECTED, true);
            message.setData(data);
            handler.sendMessage(message);
            Log.i(TAG, "USB MTi connected");
            connected = true;
            return true;
        }
        return false;
    }

    /**
     * Starts a thread that will send the received IMU packages to the specified handler
     * <p>
     * MESSAGE_TYPE->"IMU_Data"
     * READER_NAME->"XSens MTi IMU"
     * "Time"-> System time in nanoseconds (long)
     * "Accelerations_x"-> float
     * "Accelerations_y"-> float
     * "Accelerations_z"-> float
     * "TurnRates_x"-> float
     * "TurnRates_y"-> float
     * "TurnRates_z"-> float
     * "MagneticFields_x"-> float
     * "MagneticFields_y"-> float
     * "MagneticFields_z"-> float
     * "Temperature"-> float
     * "Counter"-> MTi internal counter (short)
     * <p>
     * If it receive more than 30 packages without information it assume that the MTi is
     * disconnected and sends a message to the handler (MESSAGE_TYPE -> "MTiConnect",
     * "Connected" -> false).
     * <p>
     * If the USB connection is interrupted it sends a message to the handler (MESSAGE_TYPE ->
     * "Connect", CONNECTED -> false).
     */
    public void startMeasurements() {

        this.readingThread = new Thread(() -> {
            readingIMU = true;
            byte[] msgBuffer = new byte[64];
            int inCount, roundCounter = 0;
            byte[] mtiPkg = new byte[PACKAGE_SIZE * 2];
            for (int ii = 0; ii < PACKAGE_SIZE * 2; ii++)
                mtiPkg[ii] = 0;
            int loopsCounts = 0;

            do {
                // Read USB data from external device
                inCount = connection.bulkTransfer(inputEndPoint, msgBuffer, 64, 1000);
                IMUData mtiBlock = null;
                // check data are fresh
                if ((msgBuffer[1] & 0x02) == 0 & inCount > 2)
                    for (int i = 2; i < inCount; i++) {
                        mtiPkg[roundCounter] = msgBuffer[i];

                        if (mtiPkg[roundCounter] == preamble) {
                            if (mtiPkg[(roundCounter + PACKAGE_SIZE + 1) % (PACKAGE_SIZE * 2)] == BID &
                                    mtiPkg[(roundCounter + PACKAGE_SIZE + 2) % (PACKAGE_SIZE * 2)] == MID_MTData &
                                    mtiPkg[(roundCounter + PACKAGE_SIZE + 3) % (PACKAGE_SIZE * 2)] == dataLength &
                                    mtiPkg[(roundCounter + PACKAGE_SIZE) % (PACKAGE_SIZE * 2)] == preamble) {
                                mtiBlock = parse(mtiPkg, (roundCounter + PACKAGE_SIZE) % (PACKAGE_SIZE * 2));  // --------RAW parse---------
                                Message message = new Message();
                                assert mtiBlock != null;
                                Bundle data = bundleParse(mtiBlock);   // -------- parse Bundle---------
                                data.putString(MESSAGE_TYPE, "IMU_Data");
                                data.putString(READER_NAME, name);
                                data.putLong("Time", System.nanoTime());
                                message.setData(data);
                                handler.sendMessage(message);
                            }
                        }
                        roundCounter = (roundCounter + 1) % (PACKAGE_SIZE * 2);
                    }
                if (mtiBlock != null) {
                    receivedData[(receivedPackages - 1) % MAX_PACKAGES] = mtiBlock;
                    loopsCounts = 0;
                } else {
                    loopsCounts++;
                }

            } while (inCount > 0 & loopsCounts < maxErrors & readingIMU);

            if (inCount <= 0) {
                Message message = new Message();
                Bundle data = new Bundle();
                data.putString(MESSAGE_TYPE, "Connect");
                data.putString(READER_NAME, name);
                data.putBoolean(CONNECTED, false);
                message.setData(data);
                handler.sendMessage(message);
                readingIMU = false;
            }
            if (loopsCounts >= maxErrors) {
                Message message = new Message();
                Bundle data = new Bundle();
                data.putString(MESSAGE_TYPE, "MTiConnect");
                data.putString(READER_NAME, name);
                data.putBoolean(CONNECTED, false);
                message.setData(data);
                handler.sendMessage(message);
                readingIMU = false;
            }
        });
        if (!readingThread.isAlive()) {
            readingThread.setName("Thread ReadingThread - XSens MTi IMU");
            readingThread.start();
        }
    }

    /**
     * Stop reading the IMU
     */
    public void stopReading() {
        readingIMU = false;  // indicates the IMU reading thread to stop reading from the USB
        try {
            if (readingThread.isAlive()) {
                readingThread.interrupt();
            }
        } catch (Exception e) {
            Log.e(TAG, "Socket not connected");
        }
    }

    /**
     * Release the interface and the USB device connection.
     */
    public void disconnect() {
        if (connection != null) {
            connection.releaseInterface(usbInterface);
            connection.close();
            connected = false;
        }
    }

    /**
     * Enter the configuration mode and interrupt the reading thread if any.
     */
    public void goToConfig() {
        byte[] messageBuffer = new byte[64];
        byte[] outputBuffer = {preamble, BID, MID_goToConfig, 0, 0};
        int[] out = new int[5];
        int loops = 0, outerLoop = 0;


        outputBuffer[4] = crc_calculation(outputBuffer, outputBuffer.length);
        messageBuffer[1] = 2;
        if (!readingIMU)
            while (connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000) >= 2 & (messageBuffer[1] & 0x02) > 0)
                ; //Clean the buffer
        connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
        connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
        do {
            // send set setting mode on command
            connection.bulkTransfer(outputEndPoint, outputBuffer, outputBuffer.length, 1000);
            if (readingIMU) {
                while (readingIMU) ;
            } else {
                outerLoop = connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
                for (int i = 0; i < outerLoop - 2 & i < 5; i++)
                    out[i] = ((int) messageBuffer[i + 2]) & 0x00FF;
            }
            loops++;
        } while (outerLoop != 7 & loops < 100);
    }

    /**
     * Enter the Measurement state.
     */
    public void goToMeasurement() {
        byte[] outputBuffer = {preamble, BID, MID_goToMeasurement, 0, 0}, messageBuffer = new byte[64];
        int outerLoop, receiveCount = 0;
        int[] out = new int[5];

        outputBuffer[4] = crc_calculation(outputBuffer, outputBuffer.length);

        connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
        connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
        // send set measure on command
        if (connection.bulkTransfer(outputEndPoint, outputBuffer, outputBuffer.length, 1000) < outputBuffer.length)
            return;
        do {
            outerLoop = connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
            receiveCount++;
        } while (outerLoop <= 2 & receiveCount < 9);
        if (outerLoop <= 0)
            return;

        for (int i = 0; i < outerLoop - 2 & i < 5; i++)
            out[i] = ((int) messageBuffer[i + 2]) & 0x00FF;
    }


    /**
     * Request the Device ID of the IMU.
     *
     * @return Received Device ID as a byte array.
     */
    public byte[] reqDID() {
        byte[] messageBuffer = new byte[64];
        byte[] outputBuffer = {preamble, BID, MID_reqDID, 0, 0};
        byte[] out = new byte[4];
        int inputCount, loopsCounts, outerLoop = 0;


        outputBuffer[4] = crc_calculation(outputBuffer, outputBuffer.length);
        do {
            connection.bulkTransfer(outputEndPoint, outputBuffer, outputBuffer.length, 1000);
            if (readingIMU) {
                return null;
            } else {
                loopsCounts = 0;
                do {
                    inputCount = connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
                    if (inputCount >= 11) {
                        byte sum = messageBuffer[3];
                        for (int i = 4; i < 11; i++)
                            sum += messageBuffer[i];
                        if (messageBuffer[2] == preamble & messageBuffer[3] == BID & messageBuffer[4] == (MID_reqDID + 1) & messageBuffer[5] == 4 & sum == 0) {//&((int)msg_buffer[3]+(int)msg_buffer[4]+(int)msg_buffer[5]+(int)msg_buffer[6]+(int)msg_buffer[7]+(int)msg_buffer[8]+msg_buffer[9]+msg_buffer[10])==0){
                            out[0] = messageBuffer[6];
                            out[1] = messageBuffer[7];
                            out[2] = messageBuffer[8];
                            out[3] = messageBuffer[9];
                            return out;
                        }
                        loopsCounts = 0;
                    } else
                        loopsCounts++;
                } while (inputCount > 0 & loopsCounts < 10);
            }
            outerLoop++;
        } while (outerLoop < 3);
        return messageBuffer;
    }

    /**
     * Request the product code of the IMU.
     *
     * @return The received product code of the IMU as a char array, use String.copyValueOf(char[])
     * to generate the String
     */
    public char[] reqProductCode() {
        byte[] msg_buffer = new byte[64];
        byte[] out_buffer = {preamble, BID, MID_reqProductCode, 0, 0};
        int in_count, loops_counts, out_loop = 0;


        out_buffer[4] = crc_calculation(out_buffer, out_buffer.length);
        do {
            connection.bulkTransfer(outputEndPoint, out_buffer, out_buffer.length, 1000);
            if (readingIMU) {
                return null;
            } else {
                loops_counts = 0;
                do {
                    in_count = connection.bulkTransfer(inputEndPoint, msg_buffer, 64, 1000);
                    if (in_count >= 7) {
                        byte sum = msg_buffer[3];
                        for (int i = 4; i < in_count; i++)
                            sum += msg_buffer[i];
                        if (msg_buffer[2] == preamble & msg_buffer[3] == BID & msg_buffer[4] == (MID_reqProductCode + 1) & msg_buffer[5] == in_count - 7 & sum == 0) {
                            char[] out = new char[in_count - 7];
                            for (int i = 6; i < in_count - 1; i++)
                                out[i - 6] = (char) (((int) msg_buffer[i]) & 0x00FF);
                            return out;
                        }
                        loops_counts = 0;
                    } else
                        loops_counts++;
                } while (in_count > 0 & loops_counts < 10);
            }
            out_loop++;
        } while (out_loop < 3);
        return null;
    }

    /**
     * Send a message with a Data.
     *
     * @param MID  Message ID.
     * @param Data Data to be send with the MID.
     */
    public void Setting(byte MID, byte[] Data) {
        byte[] messageBuffer = new byte[64];
        byte[] outerBuffer = new byte[5 + Data.length];
        int inputCount, loopsCounts, outerLoop;

        outerBuffer[0] = preamble;
        outerBuffer[1] = BID;
        outerBuffer[2] = MID;
        if (Data.length > 59)
            return;
        outerBuffer[3] = (byte) (Data.length & 0x00FF);
        System.arraycopy(Data, 0, outerBuffer, 4, Data.length);
        //{preamble,BID,MID,Data.length,Data[],0};

        outerBuffer[outerBuffer.length - 1] = crc_calculation(outerBuffer, outerBuffer.length);
        outerLoop = 0;
        do {
            // send command with data
            if (connection.bulkTransfer(outputEndPoint, outerBuffer, outerBuffer.length, 1000) < 0)
                return;
            loopsCounts = 0;
            do {
                inputCount = connection.bulkTransfer(inputEndPoint, messageBuffer, 64, 1000);
                if (inputCount >= 7) {
                    byte sum = messageBuffer[3];
                    for (int i = 4; i < inputCount; i++)
                        sum += messageBuffer[i];
                    if (messageBuffer[2] == preamble & messageBuffer[3] == BID & messageBuffer[4] == (MID + 1) & messageBuffer[5] == inputCount - 7 & sum == 0) {
                        byte[] out = new byte[inputCount - 2];
                        System.arraycopy(messageBuffer, 2, out, 0, inputCount - 2);
                        return;
                    }
                    loopsCounts = 0;
                } else
                    loopsCounts++;
            } while (inputCount > 0 & loopsCounts < 10);

            outerLoop++;
        } while (outerLoop < 3);
    }

    /**
     * Parse received message.
     *
     * @param msg     buffer array with the information to interpret.
     * @param pointer of the first byte of the package.
     * @return Received information.
     */
    private IMUData parse(byte[] msg, int pointer) {
        byte MTData = 0x32;    //Message identifier for a MT data

        IMUData receivedMessage = new IMUData();
        byte sum = 0;
        byte[] Package = new byte[PACKAGE_SIZE];
        Package[0] = preamble;
        for (int i = 1; i < PACKAGE_SIZE; i++) {
            sum += msg[(pointer + i) % (PACKAGE_SIZE * 2)];
            Package[i] = msg[(pointer + i) % (PACKAGE_SIZE * 2)];
        }
        if (sum == 0) {
            if (Package[2] == MTData) {    //Data message
                receivedMessage.acceleration[0] = Float.intBitsToFloat((((int) Package[ACCELERATION_INDEX]) << 24) + ((((int) Package[ACCELERATION_INDEX + 1]) & 0x00FF) << 16) + ((((int) Package[ACCELERATION_INDEX + 2]) & 0x00FF) << 8) + (((int) Package[ACCELERATION_INDEX + 3]) & 0x00FF));
                receivedMessage.acceleration[1] = Float.intBitsToFloat((((int) Package[ACCELERATION_INDEX + 4]) << 24) + ((((int) Package[ACCELERATION_INDEX + 5]) & 0x00FF) << 16) + ((((int) Package[ACCELERATION_INDEX + 6]) & 0x00FF) << 8) + (((int) Package[ACCELERATION_INDEX + 7]) & 0x00FF));
                receivedMessage.acceleration[2] = Float.intBitsToFloat((((int) Package[ACCELERATION_INDEX + 8]) << 24) + ((((int) Package[ACCELERATION_INDEX + 9]) & 0x00FF) << 16) + ((((int) Package[ACCELERATION_INDEX + 10]) & 0x00FF) << 8) + (((int) Package[ACCELERATION_INDEX + 11]) & 0x00FF));
                receivedMessage.turnRate[0] = Float.intBitsToFloat((((int) Package[TURN_RATE_INDEX]) << 24) + ((((int) Package[TURN_RATE_INDEX + 1]) & 0x00FF) << 16) + ((((int) Package[TURN_RATE_INDEX + 2]) & 0x00FF) << 8) + (((int) Package[TURN_RATE_INDEX + 3]) & 0x00FF));
                receivedMessage.turnRate[1] = Float.intBitsToFloat((((int) Package[TURN_RATE_INDEX + 4]) << 24) + ((((int) Package[TURN_RATE_INDEX + 5]) & 0x00FF) << 16) + ((((int) Package[TURN_RATE_INDEX + 6]) & 0x00FF) << 8) + (((int) Package[TURN_RATE_INDEX + 7]) & 0x00FF));
                receivedMessage.turnRate[2] = Float.intBitsToFloat((((int) Package[TURN_RATE_INDEX + 8]) << 24) + ((((int) Package[TURN_RATE_INDEX + 9]) & 0x00FF) << 16) + ((((int) Package[TURN_RATE_INDEX + 10]) & 0x00FF) << 8) + (((int) Package[TURN_RATE_INDEX + 11]) & 0x00FF));
                receivedMessage.magneticField[0] = Float.intBitsToFloat((((int) Package[MAGNETIC_FIELD_INDEX]) << 24) + ((((int) Package[MAGNETIC_FIELD_INDEX + 1]) & 0x00FF) << 16) + ((((int) Package[MAGNETIC_FIELD_INDEX + 2]) & 0x00FF) << 8) + (((int) Package[MAGNETIC_FIELD_INDEX + 3]) & 0x00FF));
                receivedMessage.magneticField[1] = Float.intBitsToFloat((((int) Package[MAGNETIC_FIELD_INDEX + 4]) << 24) + ((((int) Package[MAGNETIC_FIELD_INDEX + 5]) & 0x00FF) << 16) + ((((int) Package[MAGNETIC_FIELD_INDEX + 6]) & 0x00FF) << 8) + (((int) Package[MAGNETIC_FIELD_INDEX + 7]) & 0x00FF));
                receivedMessage.magneticField[2] = Float.intBitsToFloat((((int) Package[MAGNETIC_FIELD_INDEX + 8]) << 24) + ((((int) Package[MAGNETIC_FIELD_INDEX + 9]) & 0x00FF) << 16) + ((((int) Package[MAGNETIC_FIELD_INDEX + 10]) & 0x00FF) << 8) + (((int) Package[MAGNETIC_FIELD_INDEX + 11]) & 0x00FF));
                receivedMessage.temperature = Float.intBitsToFloat((((int) Package[TEMPERATURE_INDEX]) << 24) + ((((int) Package[TEMPERATURE_INDEX + 1]) & 0x00FF) << 16) + ((((int) Package[TEMPERATURE_INDEX + 2]) & 0x00FF) << 8) + (((int) Package[TEMPERATURE_INDEX + 3]) & 0x00FF));
                receivedMessage.quaternions[0] = Float.intBitsToFloat((((int) Package[QUATERNIONS_INDEX]) << 24) + ((((int) Package[QUATERNIONS_INDEX + 1]) & 0x00FF) << 16) + ((((int) Package[QUATERNIONS_INDEX + 2]) & 0x00FF) << 8) + (((int) Package[QUATERNIONS_INDEX + 3]) & 0x00FF));
                receivedMessage.quaternions[1] = Float.intBitsToFloat((((int) Package[QUATERNIONS_INDEX + 4]) << 24) + ((((int) Package[QUATERNIONS_INDEX + 5]) & 0x00FF) << 16) + ((((int) Package[QUATERNIONS_INDEX + 6]) & 0x00FF) << 8) + (((int) Package[QUATERNIONS_INDEX + 7]) & 0x00FF));
                receivedMessage.quaternions[2] = Float.intBitsToFloat((((int) Package[QUATERNIONS_INDEX + 8]) << 24) + ((((int) Package[QUATERNIONS_INDEX + 9]) & 0x00FF) << 16) + ((((int) Package[QUATERNIONS_INDEX + 10]) & 0x00FF) << 8) + (((int) Package[QUATERNIONS_INDEX + 11]) & 0x00FF));
                receivedMessage.quaternions[3] = Float.intBitsToFloat((((int) Package[QUATERNIONS_INDEX + 12]) << 24) + ((((int) Package[QUATERNIONS_INDEX + 13]) & 0x00FF) << 16) + ((((int) Package[QUATERNIONS_INDEX + 14]) & 0x00FF) << 8) + (((int) Package[QUATERNIONS_INDEX + 15]) & 0x00FF));
                receivedMessage.counter = (short) ((((short) Package[COUNTER_INDEX]) << 8) + (((short) Package[COUNTER_INDEX + 1]) & 0x00FF));
                receivedPackages++;
            }
            return receivedMessage;
        } else
            return null;
    }

    /**
     * Parse bundle data.
     *
     * @param data in the IMU data class.
     * @return Data in a bundle
     */
    private Bundle bundleParse(IMUData data) {
        Bundle out = new Bundle();

        out.putFloat("Accelerations_x", data.acceleration[0]);
        out.putFloat("Accelerations_y", data.acceleration[1]);
        out.putFloat("Accelerations_z", data.acceleration[2]);
        out.putFloat("TurnRates_x", data.turnRate[0]);
        out.putFloat("TurnRates_y", data.turnRate[1]);
        out.putFloat("TurnRates_z", data.turnRate[2]);
        out.putFloat("MagneticFields_x", data.magneticField[0]);
        out.putFloat("MagneticFields_y", data.magneticField[1]);
        out.putFloat("MagneticFields_z", data.magneticField[2]);
        out.putFloat("quaternions1", data.quaternions[0]);
        out.putFloat("quaternions2", data.quaternions[1]);
        out.putFloat("quaternions3", data.quaternions[2]);
        out.putFloat("quaternions4", data.quaternions[3]);
        out.putFloat("Temperature", data.temperature);
        out.putFloat("Pressure", data.pressure);
        out.putShort("Counter", data.counter);
        out.putFloat("Euler_Roll", 0);
        out.putFloat("Euler_Pitch", 0);
        out.putFloat("Euler_Yaw", 0);

        return out;
    }

    public static class IMUData {
        public float[] acceleration = {0.0f, 0.0f, 0.0f};
        public float[] turnRate = {0.0f, 0.0f, 0.0f};
        public float[] magneticField = {0.0f, 0.0f, 0.0f};
        public float[] quaternions = {0.0f, 0.0f, 0.0f, 0.0f};
        public float pressure = 0.0f;
        public float temperature = 0.0f;
        public long time = 0;
        public short counter = 0;
    }

    /**
     * Calculate the crc for the XSens MTi IMU messages.
     */
    private byte crc_calculation(byte[] message, int length) {
        byte sum = 0;
        for (int i = 1; i < length - 1; i++)
            sum += message[i];
        return (byte) -sum;
    }

    // region Handler Management

    private void setHandler() {
        Log.d(TAG, "setHandler()");

        class HandlerCallback implements Handler.Callback {
            final DataSensor dataSensor;

            public HandlerCallback(DataSensor dataSensor) {
                this.dataSensor = dataSensor;
            }

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                Log.i(TAG, "Handle sensor message");
                data = msg.getData();
                String messageType = data.getString(MTiXSensIMU.MESSAGE_TYPE);
                if (messageType != null && messageType.equals(MTiXSensIMU.MESSAGE_TYPE_CONNECT)) {
                    boolean connected = data.getBoolean(MTiXSensIMU.CONNECTED);
                    if (getListener() != null) {
                        if (connected) {
                            getListener().onDataSensorConnected(dataSensor);
                        } else {
                            getListener().onDataSensorDisconnected(dataSensor);
                        }
                    }
                }

//                if (messageType != null && messageType.equals("MTiConnect")) {
//                    boolean connected = data.getBoolean(MTiXSensIMU.CONNECTED);
//                    if (!connected) {
//                        if (!configMode) {
//                            showToast("MTi disconnected or in config mode");
//                        }
//                    }
//                }

                if (messageType != null && messageType.equals("IMU_Data")) {
                    // TODO: fire this event only X times per second
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

    // region DataSensor

    @Override
    public boolean isAvailable() {
        return connected;
    }

    @Override
    public boolean getOffersExtendedStatus() {
        return false;
    }

    @NotNull
    @Override
    public String getName() {
        return getContext().getString(R.string.xsensDetected);
    }

    @NotNull
    @Override
    public String getFeatures() {
        return getContext().getString(R.string.xsensFeatures);
    }

    @NotNull
    @Override
    public String getStatusForScreen() {
        setCounter(getCounter() + 1);

        assert data != null;
        float[] acceleration = new float[3];
        acceleration[0] = data.getFloat("Accelerations_x");
        acceleration[1] = data.getFloat("Accelerations_y");
        acceleration[2] = data.getFloat("Accelerations_z");
        float[] turnRate = new float[3];
        turnRate[0] = data.getFloat("TurnRates_x");
        turnRate[1] = data.getFloat("TurnRates_y");
        turnRate[2] = data.getFloat("TurnRates_z");
        float[] magneticField = new float[3];
        magneticField[0] = data.getFloat("MagneticFields_x");
        magneticField[1] = data.getFloat("MagneticFields_y");
        magneticField[2] = data.getFloat("MagneticFields_z");
        float[] quaternions = new float[4];
        quaternions[0] = data.getFloat("quaternions1");
        quaternions[1] = data.getFloat("quaternions2");
        quaternions[2] = data.getFloat("quaternions3");
        quaternions[3] = data.getFloat("quaternions4");
        float[] euler = new float[3];
        euler[0] = data.getFloat("Euler_Roll");
        euler[1] = data.getFloat("Euler_Pitch");
        euler[2] = data.getFloat("Euler_Yaw");
        float temperature = data.getFloat("Temperature");
        float pressure = data.getFloat("Pressure");
        short counter = data.getShort("Counter");

        String status = String.format(Locale.US, "\tAcc(X): \t%10.5f \tm/s^2\n\tAcc(Y): \t%10.5f \tm/s^2\n\tAcc(Z): \t%10.5f \tm/s^2\n", acceleration[0], acceleration[1], acceleration[2]);
        status = status + String.format(Locale.US, "\tGyr(X): \t%10.5f \trad/s\n\tGyr(Y): \t%10.5f \trad/s\n\tGyr(Z): \t%10.5f \trad/s\n", turnRate[0], turnRate[1], turnRate[2]);
        status = status + String.format(Locale.US, "\tMag(X): \t%10.5f \tuT\n\tMag(Y): \t%10.5f \tuT\n\tMag(Z): \t%10.5f \tuT\n", magneticField[0], magneticField[1], magneticField[2]);
        status = status + String.format(Locale.US, "\tEuler(º): \t%5.1f \t%5.1f \t%5.1f\n", euler[0], euler[1], euler[2]);
        status = status + String.format(Locale.US, "\tQuater: \t%3.4f \t%3.4f \t%3.4f \t%3.4f\n", quaternions[0], quaternions[1], quaternions[2], quaternions[3]);
        status = status + String.format(Locale.US, "\tPressu: \t%10.2f \tmbar\n\tTemp: \t%10.1f \tºC\n\tTimeStamp: \t%10.1f \ts\n\t\t\t\t\t\t\t\tFreq: %5.0f Hz", pressure, temperature, getSecondsFromEpoch(), getMeasurementFrequency());

        return status;
    }

    @NonNull
    @Override
    public String getExtendedStatusForScreen() {
        return "";
    }

    @NotNull
    @Override
    public String getStatusForLog() {
        assert data != null;
        float[] acceleration = new float[3];
        acceleration[0] = data.getFloat("Accelerations_x");
        acceleration[1] = data.getFloat("Accelerations_y");
        acceleration[2] = data.getFloat("Accelerations_z");
        float[] turnRate = new float[3];
        turnRate[0] = data.getFloat("TurnRates_x");
        turnRate[1] = data.getFloat("TurnRates_y");
        turnRate[2] = data.getFloat("TurnRates_z");
        float[] magneticField = new float[3];
        magneticField[0] = data.getFloat("MagneticFields_x");
        magneticField[1] = data.getFloat("MagneticFields_y");
        magneticField[2] = data.getFloat("MagneticFields_z");
        float[] quaternions = new float[4];
        quaternions[0] = data.getFloat("quaternions1");
        quaternions[1] = data.getFloat("quaternions2");
        quaternions[2] = data.getFloat("quaternions3");
        quaternions[3] = data.getFloat("quaternions4");
        float[] euler = new float[3];
        euler[0] = data.getFloat("Euler_Roll");
        euler[1] = data.getFloat("Euler_Pitch");
        euler[2] = data.getFloat("Euler_Yaw");
        float temperature = data.getFloat("Temperature");
        float pressure = data.getFloat("Pressure");
        short counter = data.getShort("Counter");
        float SensorTimestamp = (float) timestamp_Imux_last + 0.01f;  // asumo muestreo cada 0.01s i.e. 100Hz //((float)counter/100);

        return String.format(Locale.US, "\nIMUX;%.3f;%.3f;%d;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.3f;%.2f",
                getSecondsFromEpoch(), SensorTimestamp, counter, acceleration[0], acceleration[1], acceleration[2], turnRate[0], turnRate[1], turnRate[2],
                magneticField[0], magneticField[1], magneticField[2], euler[0], euler[1], euler[2], quaternions[0], quaternions[1], quaternions[2],
                quaternions[3], pressure, temperature);
    }

    @Override
    public void startReading() {

    }

    // endregion
}
