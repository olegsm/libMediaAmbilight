package com.media.ambilight.ble;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

@SuppressLint("NewApi")
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothGattItems mGatts = new BluetoothGattItems();
    private ArrayList<BluetoothDevice> mLeDevices = new ArrayList<BluetoothDevice>();

    private static final long START_SCAN_TIMEOUT_MS = 5000;
    private static final long STOP_SCAN_TIMEOUT_MS = 15000;
    private static final long CONNECT_TIMEOUT_MS = 500;
    private static final long RECONNECT_TIMEOUT_MS = 8000;

    public final static String ACTION_GATT_CONNECTED            = "ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED         = "ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED  = "ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE            = "ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA                       = "EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(BluetouthGattAttributes.HEART_RATE_MEASUREMENT);

    private final IBinder mBinder = new LocalBinder();
    private boolean mScaning = false;
    private boolean mClosed = false;

    private Handler mHandler = new Handler();
    private Context mContext = null;

    private Runnable mStopScaningDelayed = new Runnable() {
        @Override
        public void run() {
            if (mScaning) {
                Log.v(TAG, "stop scan delayed");
                stopScanIfPrepared();
            }
        }
    };

    private Runnable mStartScaningDelayed = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "start scan delayed");
            startScan();
        }
    };

    private Runnable mReconnect = new Runnable() {
        @Override
        public void run() {

            Log.v(TAG, "check for connections...");
            if (!isScaningComplete()) {
                Log.v(TAG, "not complete scanning, scan again!");
                startScan();
                return;
            }

            boolean needConnect = false;
            for (BluetoothDevice device : mLeDevices) {
                BluetoothItem item = mGatts.lookup(device.getAddress());
                if (item != null) {
                    if (item.isConnected()) {
                        continue;
                    }
                    if (item.isLongConnecting()) {
                        Log.v(TAG, "so long connecting to=" + item.mMacAddress);
                        closeForce(item);
                        needConnect = true;
                        break;
                    }
                }
            }

            if (needConnect) {
                connect();
            }

            reconnect();
        }
    };

    private Callable<Boolean> mPreparedCallback = null;
    private Callback mCallback = null;

    public interface Callback {
        void OnConnected();
        void OnDisconnected();
    }

    public BluetoothLeService() {
        super();
    }

    public BluetoothLeService(Context context, Callback callback, Callable<Boolean> preparedCallback) {
        this();
        mContext = context;
        mCallback = callback;
        mPreparedCallback = preparedCallback;
    }

    private class BluetoothItem {
        private static final int MAX_CONNECTING_TIME_MS = 16000;

        public String mMacAddress;
        public BluetoothGatt mBluetoothGatt = null;
        public int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        private long mConnectingTime = 0;

        public BluetoothItem() {
        }

        public BluetoothItem(BluetoothGatt gatt, String address) {
            mBluetoothGatt = gatt;
            mMacAddress = address;
            mConnectionState = BluetoothProfile.STATE_CONNECTING;
            mConnectingTime = System.currentTimeMillis();
        }

        public boolean isConnected() {
            return mBluetoothGatt != null && mConnectionState == BluetoothProfile.STATE_CONNECTED;
        }

        public boolean isLongConnecting() {
            return mConnectingTime > 0 && System.currentTimeMillis() - mConnectingTime > MAX_CONNECTING_TIME_MS;
        }

        public void disconnect() {
            if (mBluetoothGatt != null) {
                Log.v(TAG, "BluetoothItem: disconnect " + mMacAddress);
                mBluetoothGatt.disconnect();
            }
            mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
            mConnectingTime = 0;
        }

        public void close() {
            if (mBluetoothGatt != null) {
                Log.v(TAG, "BluetoothItem: close " + mMacAddress);
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
            mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
            mConnectingTime = 0;
        }

        public boolean connect() {
            boolean status = false;
            if (mBluetoothGatt != null) {
                mConnectionState = BluetoothProfile.STATE_CONNECTING;
                status = mBluetoothGatt.connect();
                if (status) {
                    mConnectingTime = System.currentTimeMillis();
                }
            }
            Log.d(TAG, "BluetoothItem: connect " + mMacAddress + " status " + status);
            return status;
        }

        public boolean discoverServices() {
            if (mBluetoothGatt != null) {
                mConnectionState = BluetoothProfile.STATE_CONNECTED;
                mConnectingTime = 0;
                return mBluetoothGatt.discoverServices();
            }
            return false;
        }
    }

    private class BluetoothGattItems {
        private ArrayList<BluetoothItem> mBluetoothGatts = new ArrayList<BluetoothItem>();

        public BluetoothGattItems() {
            initilaize();
        }

        public void add(BluetoothItem item) {
            int index = BluetouthGattAttributes.indexMac(item.mMacAddress);
            mBluetoothGatts.set(index, item);
        }

        public BluetoothItem lookup(String address) {
            if (TextUtils.isEmpty(address)) {
                return null;
            }

            for (BluetoothItem i: mBluetoothGatts) {
                if (TextUtils.equals(i.mMacAddress, address)) {
                    return i;
                }
            }
            return null;
        }

        public BluetoothItem lookup(BluetoothGatt gatt) {
            if (gatt == null) {
                return null;
            }

            for (BluetoothItem i: mBluetoothGatts) {
                if (i.mBluetoothGatt == gatt) {
                    return i;
                }
            }
            return null;
        }

        public BluetoothItem lookup(int index) {
            return mBluetoothGatts.size() > index ? mBluetoothGatts.get(index) : null;
        }

        public void release() {
            close();
            initilaize();
        }

        public void closeDisconnected() {
            for (int i = 0; i < mBluetoothGatts.size(); ++i) {
                BluetoothItem item = mBluetoothGatts.get(i);
                if (!item.isConnected()) {
                    item.close();
                    mBluetoothGatts.set(i, new BluetoothItem());
                }
            }
        }

        public List<BluetoothItem> getConnected() {
            List<BluetoothItem> list = new ArrayList<BluetoothItem>();
            for (BluetoothItem i : mBluetoothGatts) {
                if (i.mBluetoothGatt != null && i.isConnected()) {
                    list.add(i);
                }
            }
            return list;
        }

        public List<BluetoothGattService> getServices() {
            List<BluetoothGattService> list = new ArrayList<BluetoothGattService>();
            for (BluetoothItem i : mBluetoothGatts) {
                if (i.mBluetoothGatt != null) {
                    list.addAll(i.mBluetoothGatt.getServices());
                }
            }
            return list;
        }

        private void close() {
            for (BluetoothItem i : mBluetoothGatts) {
                i.close();
            }
        }

        private void initilaize() {
            mBluetoothGatts.clear();
            for (int i = 0; i < BluetouthGattAttributes.allowedMacSize(); ++i) {
                mBluetoothGatts.add(new BluetoothItem());
            }
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (!mScaning) {
                Log.v(TAG, "onLeScan: " + device.getName() + " : " + device.getAddress() + " scaning " + mScaning);
                stopScan();
                return;
            }

            if (isAllowedDevice(device)) {
                Log.v(TAG, "onLeScan: " + device.getName() + " : " + device.getAddress() + " scaning " + mScaning);
                addLeDevice(device);
            }
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            final BluetoothItem item = mGatts.lookup(gatt);
            if (item == null) {
                return;
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED && BluetoothConst.GATT_SUCCESS == status) {
                onDisconnected(item);
            } else if (newState == BluetoothProfile.STATE_CONNECTED && BluetoothConst.GATT_SUCCESS == status) {
                onConnected(item);
            } else if (BluetoothConst.GATT_ERROR == status) {
                onError(item, status, newState);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {

            if (status != BluetoothConst.GATT_SUCCESS) {
                Log.w(TAG, "onCharacteristicWrite status: " + status);
            }
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothItem item = mGatts.lookup(gatt);
            if (item == null) {
                return;
            }

            Log.i(TAG, "onServicesDiscovered status: " + item.mMacAddress + " status=" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService s : services) {
                    Log.d(TAG, "onServicesDiscovered service: " + s.getUuid().toString());
                }

                if (mCallback != null) {
                    mCallback.OnConnected();
                }

                connectDelayed(CONNECT_TIMEOUT_MS);
                reconnect();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            Log.v(TAG, "onCharacteristicRead");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic) {

            Log.v(TAG, "onCharacteristicChanged");
        }
    };

    public boolean initialize() {
        Log.v(TAG, "initialize");
        if (mBluetoothManager == null) {
            if (mContext != null) {
                mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            } else {
                mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            }

            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return startScan();
    }

    public void close() {
        Log.v(TAG, "close");
        mClosed = true;
        mHandler.removeCallbacksAndMessages(null);

        stopScan();
        mGatts.release();
    }

    public void readCharacteristic(int index, BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothItem item = mGatts.lookup(index);
        if (item != null && item.mBluetoothGatt != null) {
            item.mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    public void setCharacteristicNotification(int index, BluetoothGattCharacteristic characteristic,
            boolean enabled) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothItem item = mGatts.lookup(index);
        if (item == null || item.mBluetoothGatt == null) {
            return;
        }

        item.mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID
                    .fromString(BluetouthGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            item.mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public void sendCharacteristic(int index, byte[] data) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "sendCharacteristic: not initialized");
            return;
        }
        BluetoothItem item = mGatts.lookup(index);
        if (item == null || !item.isConnected()) {
            Log.w(TAG, "sendCharacteristic: device (" + (item != null ? item.mMacAddress : null) + ") is not connected");
            return;
        }

        BluetoothGattService needService = item.mBluetoothGatt.getService(UUID
                .fromString(BluetouthGattAttributes.HEART_RATE_MEASUREMENT));
        if (needService != null) {
            BluetoothGattCharacteristic dataChaterristic = needService.getCharacteristic(UUID
                    .fromString(BluetouthGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            if (dataChaterristic != null) {
                dataChaterristic.setValue(data);
                item.mBluetoothGatt.writeCharacteristic(dataChaterristic);
            }
        } else {
            //Log.w(TAG, "BluetoothGattService empty index = " + index);
        }
    }

    public void reset() {
        Log.i(TAG, "reset start");
        stopScan();

        mGatts.release();
        mLeDevices.clear();

        startScan();
        Log.i(TAG, "reset end");
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        return mGatts.getServices();
    }

    private boolean isAllowedDevice(BluetoothDevice device) {
        return device != null && device.getType() == BluetoothDevice.DEVICE_TYPE_LE
                && BluetouthGattAttributes.allowedMac(device.getAddress());
    }

    private void onDisconnected(BluetoothItem item) {
        Log.i(TAG, "Disconnected! " + item.mMacAddress);
        closeForce(item);
        if (mCallback != null) {
            mCallback.OnDisconnected();
        }
        connectDelayed(CONNECT_TIMEOUT_MS);
    }

    private void onConnected(BluetoothItem item) {
        Log.i(TAG, "Connected! " + item.mMacAddress);
        final boolean success = item.discoverServices();
        Log.i(TAG, "Attempting to start service discovery... " + (success ? "succeed" : "failed"));
    }

    private void onError(BluetoothItem item, int error, int state) {
        Log.e(TAG, "onConnectionStateChange error: " + error + " state: " + state);
        closeForce(item);
        connectDelayed(CONNECT_TIMEOUT_MS * 2);
    }

    private void closeForce(BluetoothItem item) {
        refreshDeviceCache(item.mBluetoothGatt, true);
        try {
            Thread.sleep(CONNECT_TIMEOUT_MS);
        } catch (InterruptedException e) {
        }
        item.close();
    }

    private boolean addLeDevice(BluetoothDevice device) {
        if (!mLeDevices.contains(device)) {
            mLeDevices.add(device);
        }

        Log.i(TAG, "addLeDevice: " + device.getName() + " : " + device.getAddress()
            + " (" + mLeDevices.size() + "/" + BluetouthGattAttributes.allowedMacSize() + ")");

        if (isScaningComplete()) {
            Log.i(TAG, "scaning complete, try to connect!");
            stopScanIfPrepared();
            reconnect();
            return true;
        }
        return false;
    }

    private void stopScan() {
        Log.v(TAG, "stopScan");
        mHandler.removeCallbacks(mStopScaningDelayed);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mScaning = false;

        if (mLeDevices.isEmpty()) {
            mHandler.postDelayed(mStartScaningDelayed, START_SCAN_TIMEOUT_MS * 3);
        }
    }

    private void stopScanIfPrepared() {
        Log.v(TAG, "stopScanIfPrepared before=" + mScaning);
        if (mPreparedCallback != null) {
            try {
                mScaning = !mPreparedCallback.call();
            } catch (Exception e) {
            }
        }

        Log.v(TAG, "stopScanIfPrepared after=" + mScaning);
        if (mScaning) {
            stopScan();
        }

        connectDelayed(CONNECT_TIMEOUT_MS);
    }

    private boolean startScan() {
        if (mClosed || mBluetoothAdapter == null || mScaning) {
            return false;
        }

        Log.d(TAG, "Looking for a bonded device");
        Set<BluetoothDevice> bonded = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d : bonded) {
            if (isAllowedDevice(d)) {
                Log.d(TAG, "Found bonded device " + d.getAddress());
                if (addLeDevice(d)) {
                    return true;
                }
            }
        }

        if (mBluetoothAdapter.startLeScan(mLeScanCallback)) {
            Log.v(TAG, "startScan");
            mScaning = true;
            mHandler.postDelayed(mStopScaningDelayed, STOP_SCAN_TIMEOUT_MS);
            return true;
        }
        return false;
    }

    private boolean refreshDeviceCache(final BluetoothGatt gatt, final boolean force) {
        // It is very unsafe to call the refresh() method. First of all it's hidden so it may be removed
        // in the future release of Android. Android does not clear cache then device is disconnected unless manually
        // restarted Bluetooth Adapter. To do this in the code we need to call method. However is may cause a lot of troubles.
        // Ideally it should be called before connection attempt but we get 'gatt' object by calling
        // when the connection already has been started. Calling refresh() afterwards causes errors 129 and 133 to pop up from
        // time to time when refresh takes place actually during seems to be asynchronous method.
        // Therefore we are refreshing the device after disconnecting from it, before closing gatt.
        // Sometimes you may obtain services from cache, not the actual values so reconnection is required.

        // Please see this: https://github.com/NordicSemiconductor/Android-DFU-Library/issues/1

        BluetoothDevice device = gatt.getDevice();
        if (device == null) {
            return false;
        }

        boolean boundedNone = device.getBondState() == BluetoothDevice.BOND_NONE;
        if (force || boundedNone) {
            try {
                final Method refresh = gatt.getClass().getMethod("refresh");
                if (refresh != null) {
                    Log.i(TAG, "refreshDeviceCache " + device.getAddress() + " bounded " + !boundedNone);
                    return (Boolean) refresh.invoke(gatt);
                }
            } catch (final Exception e) {
                Log.e(TAG, "An exception occurred while refreshing device");
            }
        }
        return false;
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    @Override
    public void sendBroadcast(Intent intent) {
        if (mContext != null) {
            mContext.sendBroadcast(intent);
            return;
        }
        super.sendBroadcast(intent);
    }

    private boolean isScaningComplete() {
        return BluetouthGattAttributes.allowedMacSize() == mLeDevices.size();
    }

    private void connectDelayed(long time) {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connect();
            }
        }, time);
    }

    private void reconnect() {
        mHandler.removeCallbacks(mReconnect);
        mHandler.postDelayed(mReconnect, RECONNECT_TIMEOUT_MS);
    }

    private void connect() {
        if (mClosed) {
            return;
        }

        for (BluetoothDevice device : mLeDevices) {
            connect(device.getAddress());
        }
    }

    private boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (mScaning) {
            Log.w(TAG, "Connect on scaning! Should't be happened!");
        }

        BluetoothItem item = mGatts.lookup(address);
        if (item != null) {
            if (item.isConnected()) {
                return true;
            }

            if (item.mBluetoothGatt != null) {
                return item.connect();
            }
        }

        Log.v(TAG, "connect to " + address);
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.");
            return false;
        }

        Log.d(TAG, "create a new connection.");
        BluetoothGatt gatt = device.connectGatt(this, true, mGattCallback);

        if (gatt == null) {
            Log.w(TAG, "Device not found. Unable to connect too.");
            return false;
        }

        mGatts.add(new BluetoothItem(gatt, address));
        return true;
    }
}
