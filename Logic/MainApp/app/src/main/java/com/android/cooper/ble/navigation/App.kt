package com.android.cooper.ble.navigation

import com.android.common.utils.ProcessUtil
import com.android.logic.common.app.BaseApp
import com.android.logic.common.initializer.CommonInitializer
import com.wxy.appstartfaster.dispatcher.AppStartTaskDispatcher

/**
 * Created by cooper
 * 2021/7/27.
 * Email: 1239604859@qq.com
 */
class App : BaseApp() {
    override fun onCreate() {
        super.onCreate()
        if (ProcessUtil.isMainProcess(this)) {
            AppStartTaskDispatcher.getInstance()
                .setContext(this)
                .addAppStartTask(CommonInitializer())
                .start()
                .await()
        }
    }
}