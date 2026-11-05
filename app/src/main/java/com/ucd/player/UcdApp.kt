package com.ucd.player

import android.app.Application
import android.util.Log
import com.ucd.player.data.Perf
import com.ucd.player.data.UcdDatabase
import com.ucd.player.data.UcdPrefs
import java.io.File

class UcdApp : Application() {
    val db by lazy { UcdDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        Perf.lightMode = UcdPrefs(this).lightModeExternal
        Perf.start(this)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(e)) }
            old?.uncaughtException(t, e)
        }
    }
}
