package com.fuso.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fuso.core.intelligence.UsageTracker
import com.fuso.core.intelligence.createRhythmChannel
import com.fuso.core.intelligence.ensureRhythmWorkScheduled
import com.fuso.core.data.sync.SyncEnqueuer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FusoApplication : Application() {

    @Inject lateinit var usageTracker: UsageTracker

    @Inject lateinit var syncEnqueuer: SyncEnqueuer

    override fun onCreate() {
        super.onCreate()
        createRhythmChannel(this)
        ensureRhythmWorkScheduled(this)
        syncEnqueuer.ensurePeriodicSync()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    usageTracker.logAppOpen()
                }
            },
        )
    }
}
