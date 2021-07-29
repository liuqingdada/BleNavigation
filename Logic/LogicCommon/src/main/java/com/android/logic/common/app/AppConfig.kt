package com.android.logic.common.app

import android.content.Context
import android.os.Environment
import com.android.common.utils.*
import com.android.common.utils.LogUtil.Dir.LOG_DIR
import com.android.logic.common.BuildConfig
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.*

/**
 * Created by cooper
 * 2021/7/27.
 * Email: 1239604859@qq.com
 */
object AppConfig {
    private const val TAG = "AppConfig"
    private const val MAX_LOG_FILE_COUNT = 10

    private const val LOG_BASE_DIR = "debug_log/ble_navigation"
    private const val LOG_FILE_PREFIX = "assistant"

    private val context by lazy { ApplicationUtils.getApplication() as Context }

    fun main() {
        LOG_DIR = initLogDir()
        initLogDir()
        initLogger()
    }

    private fun initLogDir(): String {
        val logDir = if (XXPermissions.isGranted(context, Permission.MANAGE_EXTERNAL_STORAGE)) {
            File(Environment.getExternalStorageDirectory(), LOG_BASE_DIR)
        } else {
            File(context.getExternalFilesDir(null), "logs")
        }
        return logDir.absolutePath
    }

    private fun initLogger() {
        val processName = ProcessUtil.getCurrentProcessName(context)
        val processNameSuffix = ProcessUtil.getCurrentProcessNameSuffix(processName)
        LogUtil.setDebug(BuildConfig.IS_DEBUG)
        val fileLogger = FileLogger(LOG_DIR, LOG_FILE_PREFIX, processNameSuffix)
        fileLogger.setFileCreateListener { clean() }
        LogUtil.setFileLogger(fileLogger)
        CrashHandler.getInstance().init(context)
    }

    private fun clean() {
        serialExecute {
            if (LOG_DIR == null) {
                return@serialExecute
            }
            val logDir = File(LOG_DIR)
            val logFiles = logDir.listFiles { _: File?, name: String ->
                name.startsWith(LOG_FILE_PREFIX)
            }
            if (logFiles != null && logFiles.size > MAX_LOG_FILE_COUNT) {
                Arrays.sort(logFiles) { lhs: File, rhs: File ->
                    rhs.lastModified().compareTo(lhs.lastModified())
                }
                val size = logFiles.size
                for (i in MAX_LOG_FILE_COUNT until size) {
                    logFiles[i].delete()
                }
            }
        }
    }
}