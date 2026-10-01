package dev.glyphalarm.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/** Two-language UI (Russian / English). The chosen language is the app's own locale, so every Activity and Service follows it. */
object Lang {
    val isRu: Boolean get() = Locale.getDefault().language == "ru"

    /** "" = follow the phone, otherwise "ru" or "en". */
    fun current(ctx: Context): String =
        ctx.getSystemService(LocaleManager::class.java).applicationLocales.let { if (it.isEmpty) "" else it[0].language }

    fun set(ctx: Context, code: String) {
        ctx.getSystemService(LocaleManager::class.java).applicationLocales =
            if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
    }
}

/** Picks the string of the current UI language. */
fun tr(ru: String, en: String): String = if (Lang.isRu) ru else en
