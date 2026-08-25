package com.fuso.core.intelligence.di

import com.fuso.core.common.dispatcher.ApplicationScope
import com.fuso.core.intelligence.GeminiInsightService
import com.fuso.core.intelligence.InsightProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Singleton
    fun provideInsightProvider(impl: GeminiInsightService): InsightProvider = impl

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
