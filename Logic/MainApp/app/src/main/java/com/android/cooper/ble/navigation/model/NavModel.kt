package com.android.cooper.ble.navigation.model

import com.amap.api.navi.enums.AMapNaviRouteNotifyDataType
import com.android.cooper.ble.navigation.state.NavState
import com.android.logic.common.app.AppViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Created by cooper
 * 2021/7/28.
 * Email: 1239604859@qq.com
 */
class NavModel(state: NavState) : AppViewModel<NavState>(state) {
    companion object {
        fun distanceText(pathDistance: Int): String {
            return if (pathDistance <= 0) {
                "0m"
            } else if (pathDistance < 1000) {
                "${pathDistance}m"
            } else {
                val round = pathDistance.toDouble().roundToLong() / 100
                if (round % 10L == 0L) {
                    "${round / 10L}km"
                } else {
                    "${round.toDouble() / 10.0}km"
                }
            }
        }

        fun timeText(second: Int) = buildString {
            var sec = abs(second)
            var h = sec / 3600
            if (h == 0) {
                append("00:")
            }
            if (h > 0) {
                append(timePerfix(h)).append(":")
            }
            h = sec % 3600
            sec = (h + 59) / 60
            append(timePerfix(sec)).append(":")
            sec = h % 60
            append(timePerfix(sec))
        }

        fun naviTurn(icon: Int) = when (icon) {
            0 -> {
                "forward"
            }
            1 -> {
                "forward"
            }
            2 -> {
                "left"
            }
            3 -> {
                "right"
            }
            4 -> {
                "forward-left"
            }
            5 -> {
                "forward-right"
            }
            6 -> {
                "back-left"
            }
            7 -> {
                "back-right"
            }
            8 -> {
                "back"
            }
            9 -> {
                "forward"
            }
            20 -> {
                "keep-forward"
            }
            else -> {
                "cooper"
            }
        }

        fun trafficInfo(type: Int) = when(type) {
            AMapNaviRouteNotifyDataType.AVOID_JAM_AREA ->  "JAM"
            AMapNaviRouteNotifyDataType.AVOID_RESTRICT_AREA ->  "RESTRICT"
            AMapNaviRouteNotifyDataType.CHANGE_MAIN_ROUTE ->  "CHANGE"
            AMapNaviRouteNotifyDataType.DISPATCH ->  "DISPATCH"
            AMapNaviRouteNotifyDataType.FORBIDDEN_AREA ->  "FORBIDDEN"
            AMapNaviRouteNotifyDataType.GPS_SIGNAL_WEAK ->  "GPS_WEAK"
            AMapNaviRouteNotifyDataType.ROAD_CLOSED_AREA ->  "CLOSED"
            else -> "unknown"
        }

        private fun timePerfix(time: Int) = if (time < 10) {
            "0$time"
        } else {
            "$time"
        }
    }
}