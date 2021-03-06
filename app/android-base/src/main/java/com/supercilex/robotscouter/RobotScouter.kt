package com.supercilex.robotscouter

import android.content.Context
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import com.bumptech.glide.Glide
import com.google.android.play.core.splitcompat.SplitCompat
import com.squareup.leakcanary.LeakCanary
import com.supercilex.robotscouter.core.RobotScouter
import com.supercilex.robotscouter.core._globalContext
import com.supercilex.robotscouter.core.data.initAnalytics
import com.supercilex.robotscouter.core.data.initDatabase
import com.supercilex.robotscouter.core.data.initIo
import com.supercilex.robotscouter.core.data.initNotifications
import com.supercilex.robotscouter.core.data.initPrefs
import com.supercilex.robotscouter.core.data.initRemoteConfig
import com.supercilex.robotscouter.core.logFailures
import com.supercilex.robotscouter.shared.initUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.anko.longToast

internal class RobotScouter : MultiDexApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (LeakCanary.isInAnalyzerProcess(this)) return
        LeakCanary.install(this)

        _globalContext = this

        GlobalScope.apply {
            // Prep slow init calls
            async(Dispatchers.IO) { initIo() }.logFailures()
            async { Dispatchers.Main }.logFailures()
            async { initBridges() }.logFailures()
            async { Glide.get(RobotScouter) }.logFailures()

            async { initAnalytics() }.logFailures()
            async { initRemoteConfig() }.logFailures()
            async { initNotifications() }.logFailures()
        }

        // These calls must occur synchronously
        initDatabase()
        initPrefs()
        initUi()

        if (BuildConfig.DEBUG) {
            // Purposefully put this after initialization since Google is terrible with disk I/O.
            val vmBuilder = StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                vmBuilder.detectFileUriExposure()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                vmBuilder.detectCleartextNetwork()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                vmBuilder.penaltyDeathOnFileUriExposure()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vmBuilder.detectContentUriWithoutPermission()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vmBuilder.penaltyListener(mainExecutor, StrictMode.OnVmViolationListener {
                    longToast(it.message.orEmpty())
                })
            }
            StrictMode.setVmPolicy(vmBuilder.penaltyLog().build())

            StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                            .detectAll()
                            // TODO re-enable when we figure out how to ignore split loads
//                            .penaltyDeath()
                            .penaltyLog()
                            .build()
            )
        }
    }

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }
}
