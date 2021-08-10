package com.android.cooper.ble.navigation.model

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.android.common.utils.ApplicationUtils
import com.android.common.utils.LogUtil
import com.android.common.utils.mainThread
import com.android.cooper.ble.navigation.state.*
import com.android.gaode.map.AMapToastUtil
import com.android.lib.ble.BLE
import com.android.lib.ble.central.CentralGattCallback
import com.android.lib.ble.central.CentralGattOperator
import com.android.lib.ble.central.ICentral
import com.jeremyliao.liveeventbus.LiveEventBus
import no.nordicsemi.android.support.v18.scanner.ScanResult

/**
 * Created by liuqing.yang
 * 2021/8/6.
 * Email: 1239604859@qq.com
 */
object BleModel {
    private const val TAG = "BleModel"
    private const val ASU_SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
    private const val ASU_WRITE_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB"

    private val context by lazy { ApplicationUtils.getApplication() as Context }
    var central: ICentral? = null

    private val centralGattCallback = object : CentralGattCallback {
        override fun onStartConnect(gatt: BluetoothGatt) {
            LogUtil.d(TAG, "onStartConnect: ")
            LiveEventBus.get(GattStartConnect::class.java).postOrderly(GattStartConnect)
        }

        override fun onConnectFailed(status: Int) {
            LogUtil.d(TAG, "onConnectFailed: ")
            LiveEventBus.get(GattConnectFailed::class.java).postOrderly(GattConnectFailed)
        }

        override fun onConnected(gatt: BluetoothGatt, status: Int) {
            LogUtil.d(TAG, "onConnected: ")
            toast("连接成功!")
            LiveEventBus.get(GattConnected::class.java).postOrderly(GattConnected)
            mainThread(500) {
                val flag = gatt.discoverServices()
                toast("discoverServices: $flag")
            }
        }

        override fun onDisconnected(gatt: BluetoothGatt, status: Int) {
            LogUtil.d(TAG, "onDisconnected: ")
            toast("断开连接!")
            LiveEventBus.get(GattDisconnected::class.java).postOrderly(GattDisconnected)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            LogUtil.d(TAG, "onServicesDiscovered: ")
            toast("onServicesDiscovered!")
            for (service in gatt.services) {
                LogUtil.d(TAG, "${service.uuid}")
                for (characteristic in service.characteristics) {
                    LogUtil.d(
                        TAG, "\t" + "${characteristic.uuid}, " +
                                "${characteristic.properties}, " +
                                "${characteristic.permissions}"
                    )
                }
            }
            LiveEventBus.get(GattServicesDiscovered::class.java).postOrderly(GattServicesDiscovered)
        }

        override fun onServicesDiscoverFailed(gatt: BluetoothGatt, status: Int) {
            LogUtil.d(TAG, "onServicesDiscoverFailed: ")
            toast("onServicesDiscoverFailed!")
        }
    }

    private fun toast(msg: String) {
        mainThread {
            AMapToastUtil.show(context, msg)
        }
    }

    @Throws(Exception::class)
    fun connectBle(result: ScanResult) {
        val device = result.device
        LogUtil.d(TAG, "connectBle: $device")
        central?.device?.let {
            if (it == device) {
                central?.disconnect()
                central = null
            }
        }
        if (central == null) {
            central = BLE.newCentral(SimpleBleCentral::class.java, device)
            central?.centralGattCallback = centralGattCallback
            addOperator()
        }
        if (central?.lastState == BluetoothProfile.STATE_DISCONNECTED) {
            central?.connect(false)
        }
    }

    val asuWriteOperator = object : CentralGattOperator(
        ASU_SERVICE_UUID,
        ASU_WRITE_UUID
    ) {
        override fun onWrite(value: ByteArray, status: Int) {
            LogUtil.d(TAG, "asuWriteOperator: onWrite: $status: ${value.decodeToString()}")
        }
    }

    private fun addOperator() {
        central?.addOperator(asuWriteOperator)
        central?.mtuOperator = object : CentralGattOperator("", "") {
            override fun onMtuChanged(mtu: Int, status: Int) {
                LogUtil.d(TAG, "onMtuChanged: $mtu, $status")
            }
        }
    }
}