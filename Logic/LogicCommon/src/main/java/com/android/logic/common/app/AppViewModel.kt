package com.android.logic.common.app

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.airbnb.mvrx.BaseMvRxViewModel
import com.airbnb.mvrx.MavericksState

abstract class AppViewModel<S : MavericksState>(
    initialState: S
) : BaseMvRxViewModel<S>(initialState) {

    protected val lifecycleOwner: LifecycleOwner = LifecycleOwner { lifecycleRegistry }

    @SuppressLint("VisibleForTests")
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(lifecycleOwner)
        .apply { currentState = Lifecycle.State.RESUMED }

    override fun onCleared() {
        super.onCleared()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}