package iwarok.app.ui.util

import android.content.Context
import iwarok.app.R
import java.util.Locale

/** Supported app languages (SYSTEM follows the device locale). */
enum class AppLanguage(val languageTag: String?, val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    ZH("zh-CN", R.string.language_zh),
    EN("en", R.string.language_en),
    JA("ja", R.string.language_ja),
    KO("ko", R.string.language_ko),
    ;

    val locale: Locale?
        get() = languageTag?.let(Locale::forLanguageTag)
}

/**
 * Synchronous storage for the chosen app language. Must be readable from
 * [android.app.Activity.attachBaseContext] / Application start, hence plain
 * SharedPreferences instead of DataStore.
 */
object LanguageStore {

    private const val PREFS_NAME = "iwarok_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun load(context: Context): AppLanguage {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
            ?: AppLanguage.SYSTEM.name
        return runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
    }

    fun save(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}
