package iwarok.app.ui.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the chosen [AppLanguage] to a Context. No AppCompat dependency: we
 * build a configuration-scoped Context and let the Activity recreate on change.
 */
object LocaleHelper {

    /** Wraps [base] with the app language configuration (no-op for SYSTEM). */
    fun wrap(base: Context): Context {
        val language = LanguageStore.load(base)
        return wrap(base, language)
    }

    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = language.locale
        if (locale == null) {
            // Restore the system default after returning to "follow system".
            val system = base.resources.configuration.locales[0]
            Locale.setDefault(system)
            return base
        }
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    /** Persists [language] and recreates the host Activity to apply it. */
    fun change(context: Context, language: AppLanguage) {
        LanguageStore.save(context, language)
        context.findHostActivity()?.recreate()
    }

    /**
     * Keeps [context] wrapped with the persisted language (useful for
     * non-Activity contexts such as the Application or a Service).
     */
    fun applyTo(context: Context): Context = wrap(context)
}
