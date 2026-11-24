package com.duduezn.budgettracker.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journalise localement les erreurs non gérées afin de pouvoir diagnostiquer
 * un plantage même lorsque l'application refuse de s'ouvrir.
 * Le fichier reste dans le stockage privé de l'application.
 */
object CrashLogger {

    private const val TAG = "BudgetTracker"
    private const val FILE_NAME = "crash.log"
    private const val MAX_SIZE_BYTES = 256 * 1024

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    /** Installe le gestionnaire global. Sans effet s'il est déjà en place. */
    fun install(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(applicationContext, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Journalise une erreur interceptée ; utilise le contexte de l'application si connu. */
    fun log(throwable: Throwable) {
        val context = appContext
        if (context != null) {
            log(context, throwable)
        } else {
            Log.e(TAG, "Erreur non gérée", throwable)
        }
    }

    fun log(context: Context, throwable: Throwable) {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists() && file.length() > MAX_SIZE_BYTES) file.delete()
            val header = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRENCH)
                .format(Date())
            file.appendText(
                buildString {
                    appendLine("=== $header ===")
                    appendLine(Log.getStackTraceString(throwable))
                }
            )
        }
        Log.e(TAG, "Erreur non gérée", throwable)
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (file.exists() && file.length() > 0) file.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
