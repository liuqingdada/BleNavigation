package com.android.cooper.ble.navigation.model

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
                "0米"
            } else if (pathDistance < 1000) {
                "${pathDistance}米"
            } else {
                val round = pathDistance.toDouble().roundToLong() / 100
                if (round % 10L == 0L) {
                    "${round / 10L}公里"
                } else {
                    "${round.toDouble() / 10.0}公里"
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

        private fun timePerfix(time: Int) = if (time < 10) {
            "0$time"
        } else {
            "$time"
        }
    }
}