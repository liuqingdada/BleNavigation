package com.android.cooper.ble.navigation.ui

import android.content.Context
import android.os.Bundle
import android.os.Message
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.widget.doOnTextChanged
import com.airbnb.mvrx.viewModel
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.android.common.utils.LogUtil
import com.android.common.utils.WeakHandler
import com.android.cooper.ble.navigation.R
import com.android.cooper.ble.navigation.databinding.ActivityMainBinding
import com.android.cooper.ble.navigation.model.BleModel
import com.android.cooper.ble.navigation.model.NavModel
import com.android.cooper.ble.navigation.state.GattConnected
import com.android.cooper.ble.navigation.state.GattDisconnected
import com.android.gaode.map.AMapToastUtil
import com.android.lib.ble.nrfscan.BleScanManager
import com.android.lib.ble.permission.PermissionWizard
import com.android.lib.uicommon.support.BaseActivity
import com.android.lib.uicommon.viewBinding
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.jeremyliao.liveeventbus.LiveEventBus
import no.nordicsemi.android.support.v18.scanner.ScanResult

class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val HUD_DEVICE_NAME = "asu"
        private const val MSG_BLE_SCAN_TIMEOUT = 0
    }

    private val binding by viewBinding<ActivityMainBinding>()
    private val vm by viewModel(NavModel::class)
    private val handler = UiHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        handler.removeCallbacksAndMessages(null)
    }

    override fun invalidate() {
    }

    private fun initView() = binding.run {
        XXPermissions.with(this@MainActivity)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request { _, all ->
                if (all) {
                    AMapToastUtil.show(this@MainActivity, "SD卡权限申请成功")
                } else {
                    AMapToastUtil.show(this@MainActivity, "SD卡权限申请失败")
                }
            }
        BleScanManager.bleScanCallback = bleScanCallback
        LiveEventBus.get(GattConnected::class.java).observe(this@MainActivity) {
            btHud.isEnabled = true
        }
        LiveEventBus.get(GattDisconnected::class.java).observe(this@MainActivity) {
            btHud.isEnabled = false
        }
        inputStart.doOnTextChanged { text: CharSequence?, start: Int, before: Int, count: Int ->
            observeUserInput(text, start, before, count, inputStart)
        }
        inputEnd.doOnTextChanged { text: CharSequence?, start: Int, before: Int, count: Int ->
            observeUserInput(text, start, before, count, inputEnd)
        }
        btHud.setOnClickListener { goNavi(it.context, false) }
        btTestNavi.setOnClickListener { goNavi(it.context) }
        btScan.setOnClickListener { startBleScan() }
        btConnect.setOnClickListener {
            val scanResult = it.tag as? ScanResult
            if (scanResult == null) {
                Toast.makeText(it.context, "未找到设备,请重新扫描", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                BleModel.connectBle(scanResult)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun goNavi(context: Context, emulator: Boolean = true) = binding.run {
        val startTip = inputStart.tag as? Tip
        val endTip = inputEnd.tag as? Tip
        if (startTip == null || endTip == null) {
            AMapToastUtil.show(context, "请先选择起点和终点!")
            return
        }
        val startNaviLatLng = NaviLatLng(startTip.point.latitude, startTip.point.longitude)
        val endNaviLatLng = NaviLatLng(endTip.point.latitude, endTip.point.longitude)
        HudDisplayActivity.start(
            context,
            startNaviLatLng,
            endNaviLatLng,
            inputStart.text.toString(),
            inputEnd.text.toString(),
            emulator
        )
    }

    /**
     * 正式环境下要进行 POI search，当前 demo 只使用 tip search
     */
    private fun observeUserInput(
        text: CharSequence?,
        start: Int,
        before: Int,
        count: Int,
        inputView: AppCompatAutoCompleteTextView
    ) {
        LogUtil.d(TAG, "$text, $start, $before, $count")
        if (!text.isNullOrEmpty() && text.toString().trim().isNotEmpty()) {
            val inputQuery = InputtipsQuery(text.toString(), null)
            val inputTip = Inputtips(this@MainActivity, inputQuery)
            inputTip.setInputtipsListener { mutableList, code ->
                when (code) {
                    AMapException.CODE_AMAP_SUCCESS -> {
                        val tipPoiList = arrayListOf<Tip>()
                        val tipList = arrayListOf<String>()
                        mutableList.forEach {
                            // 当 Tip 的 getPoiID() 返回不为空，且 getPoint() 也不为空时，
                            // 表示该提示词一个真实存在的POI，可直接显示在地图上
                            if (it.poiID != null && it.point != null) {
                                tipPoiList.add(it)
                                tipList.add("${it.name}(${it.district})")
                            }
                        }
                        val arrAdapter = ArrayAdapter(
                            this@MainActivity,
                            R.layout.item_route_inputs,
                            tipList
                        )
                        inputView.setOnItemClickListener { _, _, position, _ ->
                            val tip = tipPoiList[position]
                            inputView.tag = tip
                        }
                        inputView.setAdapter(arrAdapter)
                        arrAdapter.notifyDataSetChanged()
                    }
                    else -> {
                        AMapToastUtil.showerror(this@MainActivity, code)
                    }
                }
            }
            inputTip.requestInputtipsAsyn()
        }
    }

    private class UiHandler(activity: MainActivity) : WeakHandler<MainActivity>(activity) {
        override fun handleMessage(msg: Message) {
            val owner = owner ?: return
            when (msg.what) {
                MSG_BLE_SCAN_TIMEOUT -> {
                    owner.stopBleScan()
                }
            }
        }
    }

    /**
     * Let android BT ble scan 30s
     */
    private fun startBleScan() {
        if (!BleScanManager.adapter.isEnabled) {
            AMapToastUtil.show(this, "请打开蓝牙!")
            return
        }
        if (!PermissionWizard.isLocationServiceAllowed(this)) {
            AMapToastUtil.show(this, "请打开 GPS/位置信息 开关!")
            return
        }
        XXPermissions.with(this)
            .permission(Permission.ACCESS_COARSE_LOCATION, Permission.ACCESS_FINE_LOCATION)
            .request { _, all ->
                if (all) {
                    BleScanManager.startLeScan()
                    handler.sendEmptyMessageDelayed(MSG_BLE_SCAN_TIMEOUT, 30 * 1000L)
                } else {
                    AMapToastUtil.show(this, "请授予 App 定位权限")
                }
            }
    }

    private fun stopBleScan() {
        handler.removeMessages(MSG_BLE_SCAN_TIMEOUT)
        BleScanManager.stopLeScan()
    }

    private val bleScanCallback = object : BleScanManager.BleScanCallback {
        override fun onBluetoothOpen() {
            binding.tvAppInfo.append("BT is open\n")
        }

        override fun onBluetoothClose() {
            binding.tvAppInfo.append("BT is off\n")
            stopBleScan()
        }

        override fun onRemoteDeviceFound(result: ScanResult) {
        }

        override fun onScanFailed(code: Int) {
        }

        override fun onReported(results: List<ScanResult>) {
            if (!BleScanManager.isLeScanning()) {
                return
            }
            results.forEach {
                handler.post {
                    binding.tvAppInfo.append("onReported: ${it.device.name}, ${it.device.address}\n")
                }
                if (HUD_DEVICE_NAME.equals(it.device.name, true)) {
                    stopBleScan()
                    handler.post {
                        binding.tvAppInfo.append("自动选择 BLE 设备: ${it.device.name}, ${it.device.address}\n")
                        binding.btConnect.tag = it
                        binding.btConnect.isEnabled = true
                    }
                }
            }
        }

        override fun onStartScan() {
            binding.tvAppInfo.append("BT is start ble scan\n")
        }

        override fun onStopScan() {
            binding.tvAppInfo.append("BT is stop ble scan\n")
        }
    }
}