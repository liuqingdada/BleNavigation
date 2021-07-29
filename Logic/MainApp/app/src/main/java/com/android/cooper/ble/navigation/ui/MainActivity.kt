package com.android.cooper.ble.navigation.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.widget.doOnTextChanged
import com.airbnb.mvrx.viewModel
import com.amap.api.services.core.AMapException
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.android.common.utils.LogUtil
import com.android.cooper.ble.navigation.R
import com.android.cooper.ble.navigation.databinding.ActivityMainBinding
import com.android.cooper.ble.navigation.model.NavModel
import com.android.gaode.map.AMapToastUtil
import com.android.lib.uicommon.support.BaseActivity
import com.android.lib.uicommon.viewBinding

class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val binding by viewBinding<ActivityMainBinding>()
    private val vm by viewModel(NavModel::class)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    override fun invalidate() {
    }

    private fun initView() = binding.run {
        inputStart.doOnTextChanged { text: CharSequence?, start: Int, before: Int, count: Int ->
            observeUserInput(text, start, before, count, inputStart)
        }
        inputEnd.doOnTextChanged { text: CharSequence?, start: Int, before: Int, count: Int ->
            observeUserInput(text, start, before, count, inputEnd)
        }
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
                        inputView.setOnItemClickListener { _, view, position, _ ->
                            val tip = tipPoiList[position]
                            view.tag = tip
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
}