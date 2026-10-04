package de.madeat545.app

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private val tick = mutableIntStateOf(0)
    private var photoFile: File? = null
    private var photoStartedAt = 0L

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && BedPhoto.isValid(photoFile, photoStartedAt)) {
            BedPhoto.accept(this)
            Toast.makeText(this, "Bett gemacht – abgehakt!", Toast.LENGTH_SHORT).show()
        } else {
            photoFile?.delete()
            Toast.makeText(this, "Kein Foto erkannt – versuch es nochmal.", Toast.LENGTH_SHORT).show()
        }
        photoFile = null
        refresh()
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }

    fun refresh() { tick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        savedInstanceState?.getString("photo")?.let { photoFile = File(it) }
        photoStartedAt = savedInstanceState?.getLong("started") ?: 0L
        Notifications.createChannels(this)
        AlarmScheduler.scheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MadeTheme { AppRoot(tick.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleAll(this)
        if (AlarmService.isRinging) {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoFile?.let { outState.putString("photo", it.absolutePath) }
        outState.putLong("started", photoStartedAt)
    }

    // ---------- Aktionen ----------
    fun takeBedPhoto() {
        val f = BedPhoto.newFile(this)
        photoFile = f
        photoStartedAt = System.currentTimeMillis()
        try {
            takePicture.launch(BedPhoto.uriFor(this, f))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Keine Kamera-App gefunden.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggle(date: LocalDate, id: String, value: Boolean) {
        store.setDone(date, id, value)
        refresh()
    }

    fun nextRecipe(date: LocalDate) {
        store.setRecipeIndex(date, store.recipeIndex(date) + 1)
        refresh()
    }

    fun switchWeek() {
        store.weekOffset = store.weekOffset + 1
        refresh()
    }

    fun testAlarm() {
        AlarmScheduler.scheduleTest(this, 60)
        Toast.makeText(this, "Test-Wecker klingelt in 1 Minute. Handy ruhig sperren.", Toast.LENGTH_LONG).show()
    }

    // ---------- Berechtigungen ----------
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canFullScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= 34) getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true

    fun ignoresBattery(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openAppSettings()
    }

    fun requestFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            safeStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }

    fun requestExact() {
        if (Build.VERSION.SDK_INT >= 31) {
            safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestBattery() {
        safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun openAppSettings() {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun safeStart(i: Intent) {
        try { startActivity(i) } catch (_: Exception) { openAppSettingsFallback() }
    }

    private fun openAppSettingsFallback() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { }
    }

    // ---------- UI ----------
    @Composable
    private fun AppRoot(tick: Int) {
        var tab by remember { mutableStateOf(0) }
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Creme)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(Modifier.weight(1f)) {
                if (tab == 0) TodayScreen(tick) else SettingsScreen(tick)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TabButton("Heute", tab == 0, Modifier.weight(1f)) { tab = 0 }
                TabButton("Einstellungen", tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
        }
    }

    @Composable
    private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Box(
            modifier
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) C.Sand else C.Beige)
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = C.Mokka, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }

    @Composable
    private fun TodayScreen(tick: Int) {
        val today = LocalDate.now()
        val plan = store.plan(today)
        val done = store.done(today)
        val streak = store.streak(today)
        val progress = store.progress(today)
        val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
        val badge = when (plan.kind) {
            DayKind.SCHOOL -> "${plan.week}-Woche"
            DayKind.WEEKEND -> "Wochenende"
            DayKind.HOLIDAY -> plan.holidayName ?: "Ferien"
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Made at 5:45", fontFamily = FontFamily.Serif, fontSize = 15.sp, color = C.MokkaLight)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateText, fontFamily = FontFamily.Serif, fontSize = 26.sp, color = C.Mokka, modifier = Modifier.weight(1f))
                Pill(badge)
            }
            Spacer(Modifier.height(16.dp))

            // Streak-Karte
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakRing(progress = progress, size = 92, label = "$streak", sub = if (streak == 1) "Tag" else "Tage")
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Streak", fontWeight = FontWeight.Medium, color = C.Mokka, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                plan.kind != DayKind.SCHOOL -> "Heute pausiert dein Streak – er reißt nicht."
                                store.isComplete(today) -> "Alle Pflicht-Aufgaben erledigt. Stark!"
                                else -> "Hake Bett, Morgensport, Mittagessen und Abendsport ab."
                            },
                            color = C.MokkaLight, fontSize = 14.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "„${Quotes.forDay(today)}“",
                fontFamily = FontFamily.Serif, color = C.MokkaLight, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            if (plan.kind != DayKind.SCHOOL) {
                Text(
                    "Heute ist frei: kein Wecker, keine Pflicht. Alles hier ist freiwillig.",
                    color = C.MokkaLight, fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
            }

            plan.items.forEach { item ->
                val isDone = item.id in done
                TaskRow(item, isDone) {
                    if (item.isBed && !isDone) takeBedPhoto()
                    else toggle(today, item.id, !isDone)
                }
                if (item.isLunch && item.note != "in der Schule / mitgebracht") {
                    RecipeCard(Recipes.forDay(store, today)) { nextRecipe(today) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    @Composable
    private fun TaskRow(item: PlanItem, done: Boolean, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (done) C.Beige.copy(alpha = 0.55f) else C.Beige)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (done) C.Sand else Color.Transparent)
                    .border(2.dp, C.Sand, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Text("✓", color = C.Mokka, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = if (done) C.MokkaLight else C.Mokka,
                    fontSize = 16.sp,
                    fontWeight = if (item.mandatory) FontWeight.Medium else FontWeight.Normal,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (item.note != null && !(item.isBed && done)) {
                    Text(item.note, color = C.MokkaLight, fontSize = 13.sp)
                }
            }
            if (item.time != null) {
                Text(item.time, color = C.Mokka, fontSize = 14.sp, fontFamily = FontFamily.Serif)
            }
        }
    }

    @Composable
    private fun RecipeCard(r: Recipe, onNext: () -> Unit) {
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Creme)
                .border(1.dp, C.Line, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Vorschlag: ${r.name}", fontFamily = FontFamily.Serif, fontSize = 17.sp, color = C.Mokka)
            Text("${r.minutes} Min. · ca. ${r.kcal} kcal", color = C.MokkaLight, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("Zutaten", fontWeight = FontWeight.Medium, color = C.Mokka, fontSize = 14.sp)
            r.ingredients.forEach { Text("• $it", color = C.Mokka, fontSize = 14.sp) }
            Spacer(Modifier.height(6.dp))
            Text("So geht's", fontWeight = FontWeight.Medium, color = C.Mokka, fontSize = 14.sp)
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = C.Mokka, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            Text(
                "Anderer Vorschlag",
                color = C.Mokka, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.Rose)
                    .clickable { onNext() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    @Composable
    private fun SettingsScreen(tick: Int) {
        val week = Schedule.weekType(LocalDate.now(), store.weekOffset)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Einstellungen", fontFamily = FontFamily.Serif, fontSize = 26.sp, color = C.Mokka)
            Spacer(Modifier.height(16.dp))

            Card {
                Text("Nächster Wecker", fontWeight = FontWeight.Medium, color = C.Mokka)
                Text(AlarmScheduler.nextWakeText(), color = C.MokkaLight, fontSize = 14.sp)
                Text("Mo–Fr um 5:45 · nicht am Wochenende und in den Ferien", color = C.MokkaLight, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                ActionChip("Test-Wecker in 1 Minute") { testAlarm() }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Text("Damit der Wecker sicher klingelt", fontWeight = FontWeight.Medium, color = C.Mokka)
                Text("Alles sollte auf „OK“ stehen. Tippe sonst auf den Punkt.", color = C.MokkaLight, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                PermissionRow("Benachrichtigungen", hasNotificationPermission()) { requestNotifications() }
                PermissionRow("Vollbild-Wecker", canFullScreen()) { requestFullScreen() }
                PermissionRow("Genaue Weckzeit", AlarmScheduler.canExact(this@MainActivity)) { requestExact() }
                PermissionRow("Akku-Optimierung aus", ignoresBattery()) { requestBattery() }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Samsung-Tipp: Einstellungen → Akku → Hintergrundnutzungslimits → „Made at 5:45“ zu „Nie im Standby“ hinzufügen.",
                    color = C.MokkaLight, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Text("Schulwoche", fontWeight = FontWeight.Medium, color = C.Mokka)
                Text("Diese Woche ist eine $week-Woche.", color = C.MokkaLight, fontSize = 14.sp)
                Text("Ferienwochen zählen nicht mit – nach den Ferien geht es mit der nächsten Woche weiter.", color = C.MokkaLight, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                ActionChip("Stimmt nicht – A/B tauschen") { switchWeek() }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Text("Kommt in der nächsten Version", fontWeight = FontWeight.Medium, color = C.Mokka)
                Text(
                    "Stundenplan eintragen oder fotografieren · To-Dos & Termine mit Erinnerungen · Sync mit Google-Konto · Statistik & Abzeichen",
                    color = C.MokkaLight, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    @Composable
    private fun PermissionRow(label: String, ok: Boolean, onFix: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !ok) { onFix() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = C.Mokka, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Pill(if (ok) "OK" else "Einrichten", if (ok) C.Sand else C.Rose)
        }
    }
}

// ---------- Gemeinsame Bausteine ----------
@Composable
fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(C.Beige)
            .padding(18.dp)
    ) { content() }
}

@Composable
fun Pill(text: String, color: Color = C.Rose) {
    Text(
        text,
        color = C.Mokka, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun ActionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = C.Mokka, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(C.Sand)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Ring wie im Logo: füllt sich mit dem Tagesfortschritt. */
@Composable
fun StreakRing(progress: Float, size: Int, label: String, sub: String?) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = C.Creme, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
            )
            drawArc(
                color = C.Sand, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontFamily = FontFamily.Serif, fontSize = (size / 3.6).sp, color = C.Mokka, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, fontSize = 12.sp, color = C.MokkaLight)
        }
    }
}
