package com.android.logic.common.app

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.android.common.utils.ApplicationUtils

/**
 * Created by cooper
 * 2021/7/27.
 * Email: 1239604859@qq.com
 */
abstract class BaseApp : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        ApplicationUtils.init(this)
    }
}