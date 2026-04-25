package com.example.scannerone.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleUtils {
    const val LANGUAGE_IT = "it"
    const val LANGUAGE_EN = "en"
    const val DEFAULT_LANGUAGE = LANGUAGE_EN

    fun normalizeLanguage(languageCode: String?): String? {
        return when (languageCode?.lowercase(Locale.ROOT)) {
            LANGUAGE_IT -> LANGUAGE_IT
            LANGUAGE_EN -> LANGUAGE_EN
            else -> null
        }
    }

    fun systemLanguageOrDefault(context: Context): String {
        val configuration = context.resources.configuration
        val systemLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]?.language
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.language
        }

        return normalizeLanguage(systemLanguage) ?: DEFAULT_LANGUAGE
    }

    fun resolveAppLanguage(context: Context, storedLanguage: String?): String {
        return normalizeLanguage(storedLanguage) ?: systemLanguageOrDefault(context)
    }

    fun applyLocale(baseContext: Context, languageCode: String): Context {
        val normalized = normalizeLanguage(languageCode) ?: DEFAULT_LANGUAGE
        val locale = Locale(normalized)
        Locale.setDefault(locale)

        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return baseContext.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    fun updateResources(context: Context, languageCode: String) {
        val normalized = normalizeLanguage(languageCode) ?: DEFAULT_LANGUAGE
        val locale = Locale(normalized)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}
