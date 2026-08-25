package com.fuso.app.di

import com.fuso.app.BuildConfig
import com.fuso.core.data.remote.SupabaseConfig
import com.fuso.core.intelligence.GeminiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseConfig(): SupabaseConfig = SupabaseConfig(
        url = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
    )

    @Provides
    @Singleton
    fun provideGeminiConfig(): GeminiConfig = GeminiConfig(
        apiKey = BuildConfig.GEMINI_API_KEY,
    )
}
