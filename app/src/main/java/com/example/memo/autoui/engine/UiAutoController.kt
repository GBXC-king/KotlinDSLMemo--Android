package com.example.memo.autoui.engine

import android.os.Process
import com.example.memo.autoui.service.AutoAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object UiAutoController {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _shouldStop = MutableStateFlow(false)
    val shouldStop: StateFlow<Boolean> = _shouldStop

    private var lastAiActionTime = 0L
    private const val AI_ACTION_COOLDOWN_MS = 800L

    private var onStopCallback: (() -> Unit)? = null

    fun startOperation() {
        _isRunning.value = true
        _shouldStop.value = false
    }

    fun stopOperation(reason: String = "用户停止") {
        _shouldStop.value = true
        _isRunning.value = false
        onStopCallback?.invoke()
    }

    fun setOnStopCallback(callback: () -> Unit) {
        onStopCallback = callback
    }

    fun clearOnStopCallback() {
        onStopCallback = null
    }

    fun notifyAiActionPerformed() {
        lastAiActionTime = System.currentTimeMillis()
    }

    fun isUserInitiatedClick(): Boolean {
        val timeSinceLastAiAction = System.currentTimeMillis() - lastAiActionTime
        return timeSinceLastAiAction > AI_ACTION_COOLDOWN_MS
    }

    fun onUserClickDetected() {
        if (_isRunning.value && isUserInitiatedClick()) {
            stopOperation("用户点击屏幕，已终止操作")
        }
    }

    fun emergencyKill() {
        stopOperation("紧急停止")
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
        }
        Process.killProcess(Process.myPid())
    }

    fun reset() {
        _isRunning.value = false
        _shouldStop.value = false
        lastAiActionTime = 0
    }
}
