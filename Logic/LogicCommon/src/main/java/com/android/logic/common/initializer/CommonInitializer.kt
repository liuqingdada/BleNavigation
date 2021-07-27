package com.android.logic.common.initializer

import com.airbnb.mvrx.Mavericks
import com.android.common.utils.ApplicationUtils
import com.android.lib.datastore.DsManager
import com.android.logic.common.app.AppConfig
import com.hjq.permissions.XXPermissions
import com.jeremyliao.liveeventbus.LiveEventBus
import com.wxy.appstartfaster.task.AppStartTask

/**
 * Created by cooper
 * 2021/7/27.
 * Email: 1239604859@qq.com
 */
class CommonInitializer : AppStartTask() {
    override fun run() {
        DsManager.init()
        AppConfig.main()
        val application = ApplicationUtils.getApplication()
        Mavericks.initialize(application)
        XXPermissions.setScopedStorage(true)
        LiveEventBus.config().enableLogger(false)
    }

    override fun isRunOnMainThread() = true
}