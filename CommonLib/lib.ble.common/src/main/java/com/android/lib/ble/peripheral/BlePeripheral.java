package com.android.lib.ble.peripheral;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.android.common.utils.ApplicationUtils;
import com.android.common.utils.LogUtil;
import com.android.lib.ble.BLE;
import com.android.lib.ble.message.ActiveSerialExecutor;
import com.android.lib.ble.message.BleMessage;
import com.android.lib.ble.nrfscan.FastPairConstant;
import com.android.lib.ble.peripheral.base.BleBasePeripheral;
import com.android.lib.ble.message.IndicateRunnable;
import com.android.lib.ble.peripheral.callback.BasePeripheralCallback;
import com.android.lib.ble.peripheral.callback.BluetoothCallback;
import com.android.lib.ble.utils.ClsUtils;
import com.android.lib.datastore.DataStore;
import com.android.lib.datastore.DsManager;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by liuqing
 * 2018/7/26.
 * Email: suhen0420@163.com
 * <p>
 * This is BLE Peripheral simple encapsulation, if you need more character, just override.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class BlePeripheral extends BleBasePeripheral implements IPeripheral {
    private static final String TAG = "BlePeripheral";
    private static final int GATT_OPEN_SUCCESS = 0;
    private static final int GATT_NOT_SUPPORT = 1;
    private static final int GATT_OPEN_ERROR = 2;
    private static final int ACCESS_BLUETOOTH_LE_ADVERTISER_INTERVAL = 10;
    private static final int ACCESS_BLUETOOTH_LE_ADVERTISER_MAX_TIMES = 900;

    private HandlerThread mGattServerCallbackThread;
    private Handler mGattServerCallbackHandler;
    protected Queue<BasePeripheralCallback> mBasePeripheralCallbacks =
            new ConcurrentLinkedQueue<>();

    private HandlerThread mGattServerWriteThread;
    private Handler mGattServerWriteHandler;

    private final ActiveSerialExecutor mIndicateService = new ActiveSerialExecutor();
    /**
     * key: serviceUUID + characterUUID
     */
    private final Map<String, IndicateRunnable> indicateRunnables = new ConcurrentHashMap<>();

    protected Context mContext = ApplicationUtils.getApplication();
    private final Lock mLock = new ReentrantLock();

    protected BluetoothManager mBluetoothManager;
    protected BluetoothAdapter mBluetoothAdapter;
    protected BluetoothLeAdvertiser mLeAdvertiser;
    protected volatile BluetoothGattServer mBluetoothGattServer;

    private volatile BluetoothDevice mConnectionDevice;
    private BluetoothStatusReceiver mBluetoothStatusReceiver;
    private BluetoothCallback bluetoothCallback;

    protected BlePeripheral() {
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onCreate() {
        mGattServerCallbackThread = new HandlerThread("gatt-server-callback-looper-thread");
        mGattServerCallbackThread.start();
        mGattServerCallbackHandler = new Handler(mGattServerCallbackThread.getLooper());

        mGattServerWriteThread = new HandlerThread("gatt-server-write-looper-thread");
        mGattServerWriteThread.start();
        mGattServerWriteHandler = new Handler(mGattServerWriteThread.getLooper());

        mBluetoothStatusReceiver = new BluetoothStatusReceiver();
        IntentFilter bleIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothStatusReceiver, bleIntentFilter);

        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null) {
            LogUtil.d(TAG, "setup: BluetoothManager is null, " + BLE.NOT_SUPPORT_PERIPHERAL);
            mGattServerCallbackHandler.post(this::peripheralNotSupport);
            return;
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            LogUtil.d(TAG, "setup: BluetoothAdapter is null, " + BLE.NOT_SUPPORT_PERIPHERAL);
            mGattServerCallbackHandler.post(this::peripheralNotSupport);
        }
    }

    @Override
    public void addBasePeripheralCallback(BasePeripheralCallback basePeripheralCallback) {
        mLock.lock();
        try {
            boolean isContains = false;
            for (BasePeripheralCallback peripheralCallback : mBasePeripheralCallbacks) {
                if (peripheralCallback.getParentUuid()
                        .equalsIgnoreCase(basePeripheralCallback.getParentUuid()) &&
                        peripheralCallback.getChildUuid()
                                .equalsIgnoreCase(basePeripheralCallback.getChildUuid())) {
                    isContains = true;
                    break;
                }
            }
            if (!isContains) {
                mBasePeripheralCallbacks.add(basePeripheralCallback);
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void setup() {
        mGattServerCallbackHandler.post(this::start);
    }

    @Override
    public void preparePair() {
        ClsUtils.setDiscoverableTimeout(120);
    }

    @Override
    public boolean isConnected() {
        BluetoothDevice device = mConnectionDevice;
        if (device == null) {
            return false;
        }
        return mBluetoothManager.getConnectionState(
                device,
                BluetoothProfile.GATT_SERVER
        ) == BluetoothProfile.STATE_CONNECTED;
    }

    @Override
    public void cancelConnection() {
        BluetoothDevice device = mConnectionDevice;
        if (device != null) {
            mBluetoothGattServer.cancelConnection(device);
        }
    }

    @Override
    public void onDestroy() {
        mContext.unregisterReceiver(mBluetoothStatusReceiver);
        stop();
        quitIndicates();
        mGattServerCallbackThread.quitSafely();
        mGattServerWriteThread.quitSafely();
    }

    @Override
    public void setBluetoothCallback(BluetoothCallback bluetoothCallback) {
        this.bluetoothCallback = bluetoothCallback;
    }

    /* BluetoothGattServerCallback START */     /* BluetoothGattServerCallback START */
    /* BluetoothGattServerCallback START */     /* BluetoothGattServerCallback START */
    /* BluetoothGattServerCallback START */     /* BluetoothGattServerCallback START */

    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        switch (newState) {

            case BluetoothProfile.STATE_CONNECTED:
                LogUtil.d(TAG, "onConnectionStateChange: peripheral STATE_CONNECTED");
                mConnectionDevice = device;
                // create bond
                /*if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                    try {
                        device.setPairingConfirmation(true);
                    } catch (final SecurityException e) {
                        LogUtil.e(TAG, "onConnectionStateChange: ", e);
                    }
                    device.createBond();
                } else if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    mGattServerCallbackHandler.post(() -> {
                        if (mBluetoothGattServer != null) {
                            mBluetoothGattServer.connect(device, false);
                        }
                    });
                }*/
                mGattServerCallbackHandler.post(() -> {
                    mBluetoothGattServer.connect(device, false);
                    quitIndicates();

                    onConnected(device);
                });
                break;

            case BluetoothProfile.STATE_DISCONNECTED:
                LogUtil.d(TAG, "onConnectionStateChange: peripheral STATE_DISCONNECTED");
                mGattServerCallbackHandler.post(() -> {
                    quitIndicates();

                    onDisconnected(device);
                });
                break;
        }
    }

    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
    }

    @Override
    public void onCharacteristicReadRequest(
            BluetoothDevice device,
            int requestId,
            int offset,
            BluetoothGattCharacteristic characteristic
    ) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                if (basePeripheralCallback.getParentUuid()
                        .equalsIgnoreCase(characteristic.getService().getUuid()
                                .toString()) &&
                        basePeripheralCallback.getChildUuid()
                                .equalsIgnoreCase(characteristic.getUuid()
                                        .toString())) {
                    basePeripheralCallback.onCharacteristicReadRequest(
                            device,
                            requestId,
                            offset,
                            characteristic
                    );
                }
            }
            sendResponse(
                    device,
                    requestId,
                    offset,
                    characteristic.getValue()
            );
        });
    }

    @Override
    public void onCharacteristicWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value
    ) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                if (basePeripheralCallback.getParentUuid()
                        .equalsIgnoreCase(characteristic.getService().getUuid()
                                .toString()) &&
                        basePeripheralCallback.getChildUuid()
                                .equalsIgnoreCase(characteristic.getUuid()
                                        .toString())) {
                    basePeripheralCallback.onCharacteristicWriteRequest(
                            device,
                            requestId,
                            characteristic,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value
                    );
                }
            }
            sendResponse(
                    device,
                    requestId,
                    offset,
                    value
            );
        });
    }

    @Override
    public void onDescriptorReadRequest(
            BluetoothDevice device,
            int requestId,
            int offset,
            BluetoothGattDescriptor descriptor
    ) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                if (basePeripheralCallback.getParentUuid()
                        .equalsIgnoreCase(descriptor.getCharacteristic().getUuid()
                                .toString()) &&
                        basePeripheralCallback.getChildUuid()
                                .equalsIgnoreCase(descriptor.getUuid().toString())) {
                    basePeripheralCallback.onDescriptorReadRequest(
                            device,
                            requestId,
                            offset,
                            descriptor
                    );
                }
            }
            sendResponse(
                    device,
                    requestId,
                    offset,
                    descriptor.getValue()
            );
        });
    }

    @Override
    public void onDescriptorWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattDescriptor descriptor,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value
    ) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                if (basePeripheralCallback.getParentUuid()
                        .equalsIgnoreCase(descriptor.getCharacteristic().getUuid()
                                .toString()) &&
                        basePeripheralCallback.getChildUuid()
                                .equalsIgnoreCase(descriptor.getUuid().toString())) {
                    basePeripheralCallback.onDescriptorWriteRequest(
                            device,
                            requestId,
                            descriptor,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value
                    );
                }
            }
            sendResponse(
                    device,
                    requestId,
                    offset,
                    descriptor.getValue()
            );
        });
    }

    @Override
    public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
        mGattServerCallbackHandler.post(() -> sendResponse(
                device,
                requestId,
                0,
                null
        ));
    }

    /**
     * ?????????indicate, ???????????????????????????????????????????????????
     */
    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
        mGattServerCallbackHandler.post(() -> {
            LogUtil.d(TAG, "onNotificationSent: " + status);
            nextIndicatePackage();
        });
    }

    @Override
    public void onMtuChanged(BluetoothDevice device, int mtu) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                basePeripheralCallback.onMtuChanged(device, mtu);
            }
        });
    }

    @Override
    public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                basePeripheralCallback.onPhyUpdate(device, txPhy, rxPhy, status);
            }
        });
    }

    @Override
    public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
        mGattServerCallbackHandler.post(() -> {
            for (BasePeripheralCallback basePeripheralCallback : mBasePeripheralCallbacks) {
                basePeripheralCallback.onPhyRead(device, txPhy, rxPhy, status);
            }
        });
    }

    /* BluetoothGattServerCallback END */       /* BluetoothGattServerCallback END */
    /* BluetoothGattServerCallback END */       /* BluetoothGattServerCallback END */
    /* BluetoothGattServerCallback END */       /* BluetoothGattServerCallback END */

    @Override
    protected void postAction(Runnable r) {
        mGattServerCallbackHandler.post(r);
    }

    private class BluetoothStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        LogUtil.e(TAG, "bluetooth OFF");

                        if (bluetoothCallback != null) {
                            bluetoothCallback.onClose();
                        }
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        LogUtil.e(TAG, "bluetooth TURNING_OFF");
                        mGattServerCallbackHandler.post(BlePeripheral.this::stop);
                        break;

                    case BluetoothAdapter.STATE_ON:
                        LogUtil.e(TAG, "bluetooth ON");
                        mGattServerCallbackHandler.post(BlePeripheral.this::start);

                        if (bluetoothCallback != null) {
                            bluetoothCallback.onOpen();
                        }
                        break;

                    case BluetoothAdapter.STATE_TURNING_ON:
                        LogUtil.e(TAG, "bluetooth TURNING_ON");
                        break;
                }
            }
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                final int state =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final BluetoothDevice device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (state == BluetoothDevice.BOND_BONDED) {
                    mGattServerCallbackHandler.post(() -> {
                        if (device != null &&
                                mBluetoothGattServer != null &&
                                device.equals(mConnectionDevice)) {
                            mBluetoothGattServer.connect(device, true);
                        }
                    });
                    LogUtil.e(TAG, "successfully bonded");
                }
            }
        }
    }

    private boolean checkSupported() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }
        return mBluetoothManager != null && mBluetoothAdapter != null;
    }

    // Avoid multiple initialization
    private final AtomicBoolean peripheralFlag = new AtomicBoolean(false);

    /**
     * on bluetooth is open, or it is already open.
     */
    private synchronized void start() {
        if (!checkSupported()) {
            LogUtil.d(TAG, "start: check not supported");
            peripheralNotSupport();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            LogUtil.d(TAG, "start: bt is not open");
            return;
        }
        if (peripheralFlag.compareAndSet(false, true)) {
            // step 1
            String btAddress = ClsUtils.getBtAddress();
            LogUtil.d(TAG, "start: get mac address " + btAddress);
            DsManager.INSTANCE.getDelegate().putString(
                    FastPairConstant.Extra.SP_NAME,
                    BLE.PERIPHERAL_MAC,
                    btAddress != null ? btAddress : "",
                    DataStore.SINGLE_PROCESS_MODE
            );

            // step 2
            //preparePair();

            // step 3
            String peripheralName = generatePeripheralName();
            mBluetoothAdapter.setName(peripheralName);
            LogUtil.d(TAG, "start: " + peripheralName);
            DsManager.INSTANCE.getDelegate().putString(
                    FastPairConstant.Extra.SP_NAME,
                    BLE.PERIPHERAL_NAME,
                    peripheralName,
                    DataStore.SINGLE_PROCESS_MODE
            );

            // step 4
            switch (openGattServer()) {
                case GATT_NOT_SUPPORT:
                    LogUtil.d(TAG, "start: " + BLE.NOT_SUPPORT_PERIPHERAL);
                    stop();
                    peripheralNotSupport();
                    break;
                case GATT_OPEN_ERROR:
                    LogUtil.d(TAG, "start: openGattServer status error");
                    stop();
                    onOpenGattServerError(mBluetoothAdapter);
                    break;
                case GATT_OPEN_SUCCESS:
                    LogUtil.d(TAG, "start: openGattServer status normal");
                    addGattService();
                    startAdvertising();
                    break;
            }
        } else {
            LogUtil.w(TAG, "start: peripheral is already initialized.");
        }
    }

    private synchronized void stop() {
        if (peripheralFlag.compareAndSet(true, false)) {
            stopAdvertising();
            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.clearServices();
                mBluetoothGattServer.close();
            }
        }
    }

    private void openBTByUser() {
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(mContext, BLE.BT_NO_PERMISSION, Toast.LENGTH_LONG).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mContext.startActivity(enableBtIntent);
        }
    }

    /**
     * 0 open gatt server success
     * 1 not support
     * 2 error
     */
    private int openGattServer() {
        mLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (mLeAdvertiser == null) {
            return GATT_NOT_SUPPORT;
        }

        int openCount = ACCESS_BLUETOOTH_LE_ADVERTISER_MAX_TIMES;
        while (openCount-- > 0) {
            mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, this);

            if (mBluetoothGattServer != null) {
                // open gatt server success
                break;
            }

            SystemClock.sleep(ACCESS_BLUETOOTH_LE_ADVERTISER_INTERVAL);
        }
        if (mBluetoothGattServer == null) {
            return GATT_OPEN_ERROR;
        } else {
            mBluetoothGattServer.clearServices();
            return GATT_OPEN_SUCCESS;
        }
    }

    protected void startAdvertising() {
        if (mLeAdvertiser != null) {
            AdvertiseData advertiseData = generateAdvertiseData();
            LogUtil.i(TAG, "startAdvertising advertiseData: " + advertiseData.toString());

            AdvertiseData scanResponse = generateAdvertiseResponse();
            LogUtil.i(TAG, "startAdvertising scanResponse: " + scanResponse.toString());

            if (mBluetoothAdapter.isEnabled()) {
                try {
                    mLeAdvertiser.startAdvertising(
                            generateAdvertiseSettings(),
                            advertiseData,
                            scanResponse,
                            mAdvertiseCallback
                    );
                } catch (Exception e) {
                    LogUtil.d(TAG, "startAdvertising error: ", e);
                }
            }
        }
    }

    protected void stopAdvertising() {
        if (mLeAdvertiser != null) {
            mLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            LogUtil.i(TAG, "onStartSuccess: " + settingsInEffect.toString());

            mGattServerCallbackHandler.post(() -> onPeripheralStartSuccess(settingsInEffect));
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            LogUtil.w(TAG, "onStartFailure: " + errorCode);

            mGattServerCallbackHandler.post(() -> onPeripheralStartFailure(errorCode));
        }
    };

    @Override
    public void notify(String serviceUUID, String characterUUID, byte[] data) {
        if (mBluetoothGattServer != null) {
            BluetoothGattService service =
                    mBluetoothGattServer.getService(UUID.fromString(serviceUUID));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(characterUUID));
                notify(characteristic, data);
            }
        }
    }

    @Override
    public void notify(BluetoothGattCharacteristic characteristic, byte[] data) {
        mGattServerWriteHandler.post(() -> {
            if (!isConnected()) {
                return;
            }
            characteristic.setValue(data);
            mBluetoothGattServer.notifyCharacteristicChanged(
                    mConnectionDevice,
                    characteristic,
                    false
            );
        });
    }

    @Override
    public void indicate(String serviceUUID, String characterUUID, BleMessage message) {
        if (mBluetoothGattServer != null) {
            BluetoothGattService service =
                    mBluetoothGattServer.getService(UUID.fromString(serviceUUID));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(characterUUID));
                indicate(characteristic, message);
            }
        }
    }

    /**
     * ?????? {@link BluetoothGattServerCallback#onNotificationSent(BluetoothDevice, int)}
     * ???????????? message put ???????????????, ?????????, ???????????????????????????
     *
     * @param message ?????????????????????, ??????????????????
     */
    @Override
    public void indicate(BluetoothGattCharacteristic characteristic, BleMessage message) {
        mGattServerWriteHandler.post(() -> {
            if (!isConnected()) {
                return;
            }
            String key = characteristic.getService().getUuid().toString() +
                    characteristic.getUuid().toString();
            IndicateRunnable indicateRunnable = indicateRunnables.get(key);
            if (indicateRunnable == null) {
                indicateRunnable = new IndicateRunnable(
                        mBluetoothGattServer,
                        mConnectionDevice,
                        characteristic
                );
                indicateRunnables.put(key, indicateRunnable);
            }

            List<byte[]> subpackage = message.subpackage(message.getPayload());
            indicateRunnable.putSubPackage(subpackage);

            mIndicateService.execute(indicateRunnable);
        });
    }

    private void nextIndicatePackage() {
        ActiveSerialExecutor.RunnableWrapper active = mIndicateService.getActive();
        if (active != null) {
            active.getIndicateRunnable().next();
        }
    }

    private void sendResponse(
            BluetoothDevice device,
            int requestId,
            int offset,
            byte[] value
    ) {
        mBluetoothGattServer.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                value
        );
    }

    private void quitIndicates() {
        for (IndicateRunnable runnable : indicateRunnables.values()) {
            runnable.quit();
        }
        indicateRunnables.clear();
        mIndicateService.quit();
    }
}
