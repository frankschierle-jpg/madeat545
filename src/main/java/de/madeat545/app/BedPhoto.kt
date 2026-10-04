package de.madeat545.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/**
 * Bett-Foto: Es wird immer die Kamera geöffnet (ACTION_IMAGE_CAPTURE).
 * Eine Auswahl aus der Galerie gibt es nicht. Akzeptiert wird nur ein frisch
 * aufgenommenes Foto, das nach dem Start der Kamera gespeichert wurde.
 */
object BedPhoto {
    private const val MIN_BYTES = 10_000L

    fun newFile(ctx: Context): File {
        val dir = File(ctx.filesDir, "bed").apply { mkdirs() }
        cleanup(dir)
        return File(dir, "bett_${LocalDate.now()}_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    fun isValid(f: File?, startedAt: Long): Boolean =
        f != null && f.exists() && f.length() >= MIN_BYTES && f.lastModified() >= startedAt - 2_000

    /** Nur die letzten 14 Bett-Fotos behalten. */
    private fun cleanup(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(14).forEach { it.delete() }
    }

    /** Erfolgreiches Foto: Bett abhaken und Wecker stoppen. */
    fun accept(ctx: Context) {
        Store(ctx).setDone(LocalDate.now(), "bett", true)
        androidx.core.app.NotificationManagerCompat.from(ctx).cancel(Notifications.ID_ALARM)
        if (!AlarmService.isRinging) return
        // Sofort zurücksetzen, damit MainActivity.onResume den Wecker nicht erneut öffnet,
        // bevor der Service den Stop-Intent verarbeitet hat.
        AlarmService.isRinging = false
        try {
            ctx.startService(android.content.Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP_ALARM))
        } catch (_: IllegalStateException) {
            // App im Hintergrund – Service ist ohnehin im Vordergrund, sollte nicht passieren
        }
    }
}
