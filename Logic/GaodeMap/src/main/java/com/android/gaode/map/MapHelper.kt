package com.android.gaode.map

import android.content.Context
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.amap.api.navi.AmapNaviType
import com.amap.api.navi.AmapPageType
import com.android.common.utils.ApplicationUtils


/**
 * Created by cooper
 * 2021/7/28.
 * Email: 1239604859@qq.com
 */
object MapHelper {
    private val context by lazy { ApplicationUtils.getApplication() as Context }

    fun selectPosition() {
        val params = AmapNaviParams(null, null, null, AmapNaviType.DRIVER, AmapPageType.ROUTE)
        AmapNaviPage.getInstance().showRouteActivity(
            context,
            params,
            null,
            MapRouteActivity::class.java
        )
    }
}