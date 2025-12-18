package com.example.coupleapp.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Manager for handling app language changes
 */
object LanguageManager {
    
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"
    
    enum class Language(val code: String, val displayName: String) {
        ENGLISH("en", "English"),
        VIETNAMESE("vi", "Tiếng Việt")
    }
    
    /**
     * Get current language from preferences
     */
    fun getCurrentLanguage(context: Context): Language {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANGUAGE, getSystemLanguageCode()) ?: "en"
        return Language.entries.find { it.code == code } ?: Language.ENGLISH
    }
    
    /**
     * Set app language and save to preferences
     * Note: For the change to take full effect, the Activity needs to be recreated
     */
    fun setLanguage(context: Context, language: Language) {
        // Save to preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }
    
    /**
     * Get system default language code
     */
    private fun getSystemLanguageCode(): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.os.LocaleList.getDefault()[0]
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
        
        return when (locale.language) {
            "vi" -> "vi"
            else -> "en"
        }
    }
    
    /**
     * Update context with current language
     * Call this in attachBaseContext of Activity
     */
    fun updateContextLocale(context: Context): Context {
        val language = getCurrentLanguage(context)
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    /**
     * Apply language to a context and return the updated context
     * Use this to wrap the context for localized resources
     */
    fun applyLanguage(context: Context, language: Language): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    /**
     * Toggle between English and Vietnamese
     */
    fun toggleLanguage(context: Context): Language {
        val current = getCurrentLanguage(context)
        val newLanguage = if (current == Language.ENGLISH) Language.VIETNAMESE else Language.ENGLISH
        setLanguage(context, newLanguage)
        return newLanguage
    }
    
    /**
     * Get all available languages
     */
    fun getAvailableLanguages(): List<Language> {
        return Language.entries
    }
}
