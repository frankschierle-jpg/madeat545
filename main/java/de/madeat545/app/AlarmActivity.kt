package de.madeat545.app

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Vollbild-Wecker: Ausschalten nur durch ein Live-Foto vom gemachten Bett. */
class AlarmActivity : ComponentActivity() {

    private var photoFile: File? = null
    private var startedAt = 0L
    private val message = mutableStateOf("")

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && BedPhoto.isValid(photoFile, startedAt)) {
            BedPhoto.accept(this)
            Toast.makeText(this, "Bett gemacht – guten Morgen! Streak läuft.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            photoFile?.delete()
            message.value = "Kein Foto erkannt. Der Wecker klingelt weiter – versuch es nochmal."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("photo")?.let { photoFile = File(it) }
        startedAt = savedInstanceState?.getLong("started") ?: 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Zurück-Taste schaltet den Wecker NICHT aus.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                message.value = "Erst das Bett-Foto, dann ist der Wecker aus."
            }
        })

        setContent {
            MadeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(C.Creme)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StreakRing(progress = 0.75f, size = 150, label = "5:45", sub = null)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Guten Morgen!",
                        fontFamily = FontFamily.Serif,
                        fontSize = 34.sp,
                        color = C.Mokka,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Mach dein Bett und fotografiere es.\nDann ist der Wecker aus.",
                        fontSize = 17.sp,
                        color = C.MokkaLight,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(36.dp))
                    Button(
                        onClick = { startPhoto() },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(29.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Sand, contentColor = C.Mokka),
                    ) {
                        Text("Bett fotografieren", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    if (message.value.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(message.value, color = C.Mokka, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Wecker wurde schon anders beendet (z. B. Foto in der Haupt-App) → schließen.
        if (!AlarmService.isRinging && photoFile == null) finish()
    }

    private fun startPhoto() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchCamera()
                override fun onDismissError() = launchCamera()
                override fun onDismissCancelled() {
                    message.value = "Entsperre dein Handy, um das Foto zu machen."
                }
            })
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val f = BedPhoto.newFile(this)
        photoFile = f
        startedAt = System.currentTimeMillis()
        try {
            takePicture.launch(BedPhoto.uriFor(this, f))
        } catch (_: ActivityNotFoundException) {
            message.value = "Keine Kamera-App gefunden."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoFile?.let { outState.putString("photo", it.absolutePath) }
        outState.putLong("started", startedAt)
    }
}
