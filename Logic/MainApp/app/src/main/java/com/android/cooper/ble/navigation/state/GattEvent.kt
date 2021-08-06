package com.android.cooper.ble.navigation.state

import com.jeremyliao.liveeventbus.core.LiveEvent

/**
 * Created by liuqing.yang
 * 2021/8/6.
 * Email: 1239604859@qq.com
 */
sealed class GattEvent : LiveEvent
object GattStartConnect: GattEvent()
object GattConnected: GattEvent()
object GattConnectFailed: GattEvent()
object GattDisconnected: GattEvent()
object GattServicesDiscovered: GattEvent()