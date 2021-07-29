package com.android.cooper.ble.navigation.ui

import android.os.Bundle
import com.airbnb.mvrx.viewModel
import com.android.cooper.ble.navigation.R
import com.android.cooper.ble.navigation.databinding.ActivityMainBinding
import com.android.cooper.ble.navigation.model.NavModel
import com.android.gaode.map.MapHelper
import com.android.lib.uicommon.support.BaseActivity
import com.android.lib.uicommon.viewBinding

class MainActivity : BaseActivity() {
    private val binding by viewBinding<ActivityMainBinding>()
    private val vm by viewModel(NavModel::class)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    override fun invalidate() {
    }

    private fun initView() {
        binding.btNoPosition.setOnClickListener {
            MapHelper.selectPosition()
        }
    }
}