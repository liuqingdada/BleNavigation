package com.android.gaode.map

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
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
    val HUD_IMG_ACTIONS = intArrayOf(
        2130837608,
        2130837608,
        2130837600,
        2130837602,
        2130837603,
        2130837604,
        2130837605,
        2130837606,
        2130837607,
        2130837608,
        2130837590,
        2130837591,
        2130837592,
        2130837593,
        2130837594,
        2130837595,
        2130837596,
        2130837642,
        2130837643,
        2130837650,
        2130837645,
    )

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

    @Throws(Exception::class)
    fun colHcHudDrawable(type: Int): Drawable {
        val hczz = Class.forName("com.amap.api.col.3n.hc")
        val resourcesMethod = hczz.getMethod("b", Context::class.java)
        val resources = resourcesMethod.invoke(null, context) as Resources
        return resources.getDrawable(type, null)
    }
}