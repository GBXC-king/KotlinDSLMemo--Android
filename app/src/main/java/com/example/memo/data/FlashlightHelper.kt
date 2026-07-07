package com.example.memo.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import timber.log.Timber

object FlashlightHelper {

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isFlashOn = false

    fun init(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                return false
            }

            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.find { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraId != null
        } catch (e: Exception) {
            Timber.e(e, "初始化手电筒失败")
            false
        }
    }

    fun turnOn(context: Context): Boolean {
        if (cameraManager == null || cameraId == null) {
            if (!init(context)) return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(cameraId!!, true)
                isFlashOn = true
                true
            } else {
                false
            }
        } catch (e: CameraAccessException) {
            Timber.e(e, "打开手电筒失败")
            false
        }
    }

    fun turnOff(context: Context): Boolean {
        if (cameraManager == null || cameraId == null) {
            if (!init(context)) return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(cameraId!!, false)
                isFlashOn = false
                true
            } else {
                false
            }
        } catch (e: CameraAccessException) {
            Timber.e(e, "关闭手电筒失败")
            false
        }
    }

    fun toggle(context: Context): Boolean {
        return if (isFlashOn) {
            turnOff(context)
        } else {
            turnOn(context)
        }
    }

    fun isOn(): Boolean = isFlashOn

    fun hasFlashlight(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
}
