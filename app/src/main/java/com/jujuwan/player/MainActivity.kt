package com.jujuwan.player

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val BgDark = Color(0xFF0A0A12)
val CardDark = Color(0xFF141322)
val NeonPurple = Color(0xFFB829EA)
val NeonPurpleGlow = Color(0xFFD966FF)


object ServerCache {
    var liveCats: List<CategoryItem> = emptyList()
    var liveStreams: List<StreamItem> = emptyList()
    var movieCats: List<CategoryItem> = emptyList()
    var movieStreams: List<StreamItem> = emptyList()
    var seriesCats: List<CategoryItem> = emptyList()
    var seriesStreams: List<StreamItem> = emptyList()
    var loadedForUrl: String = ""

    var loadedKey: String = ""
    var isReady: Boolean = false

    suspend fun syncFull(
        acc: PlaylistAccount,
        onStep: (Int, String, Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val k = "${acc.url}_${acc.user}"
        if (loadedKey == k && isReady && liveStreams.isNotEmpty()) {
            onStep(6, "Tout est prêt !", 1f)
            return@withContext
        }
        isReady = false
        if (false) {
            onStep(6, "Contenu prêt !", 1f)
            return@withContext
        }
        PosterCache.lru.evictAll()
        FastHttp.streamsMemo.clear()
        FastHttp.epgMemo.clear()

        fun loadCats(action: String): List<CategoryItem> {
            return try {
                val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
                val body = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]"
                val arr = org.json.JSONArray(body)
                val out = mutableListOf(CategoryItem("ALL", "Tout afficher"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(CategoryItem(o.optString("category_id"), o.optString("category_name")))
                }
                out
            } catch (_: Exception) {
                listOf(CategoryItem("ALL", "Tout afficher"))
            }
        }

        fun loadArr(action: String): org.json.JSONArray {
            return try {
                val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
                val body = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]"
                org.json.JSONArray(body)
            } catch (_: Exception) { org.json.JSONArray() }
        }

        onStep(1, "Chargement des chaînes en direct...", 0.15f)
        liveCats = loadCats("get_live_categories")
        val aLive = loadArr("get_live_streams")
        liveStreams = (0 until aLive.length()).map { i ->
            val x = aLive.getJSONObject(i)
            val id = x.optString("stream_id")
            StreamItem(id, x.optString("name"), "${acc.url}/live/${acc.user}/${acc.pass}/$id.ts", x.optString("stream_icon", ""), "")
        }

        onStep(2, "Chargement des films...", 0.35f)
        movieCats = loadCats("get_vod_categories")
        val aVod = loadArr("get_vod_streams")
        movieStreams = (0 until aVod.length()).map { i ->
            val x = aVod.getJSONObject(i)
            val id = x.optString("stream_id")
            val ext = x.optString("container_extension", "mp4").ifBlank { "mp4" }
            StreamItem(id, x.optString("name"), "${acc.url}/movie/${acc.user}/${acc.pass}/$id.$ext", x.optString("stream_icon", x.optString("movie_image", "")), x.optString("plot", ""))
        }

        onStep(3, "Chargement des séries...", 0.55f)
        seriesCats = loadCats("get_series_categories")
        val aSer = loadArr("get_series")
        seriesStreams = (0 until aSer.length()).map { i ->
            val x = aSer.getJSONObject(i)
            val id = x.optString("series_id", x.optString("stream_id"))
            StreamItem(id, x.optString("name"), "${acc.url}/series/${acc.user}/${acc.pass}/$id.mp4", x.optString("cover", x.optString("stream_icon", "")), x.optString("plot", ""))
        }

        onStep(4, "Chargement du guide EPG...", 0.62f)
        val epgTargets = liveStreams.take(30)
        epgTargets.forEachIndexed { idx: Int, st: StreamItem ->
            try { fetchLiveEpg(acc, st.id) } catch (_: Exception) {}
            val pEpg = 0.60f + (0.18f * (idx + 1).toFloat() / maxOf(1, epgTargets.size))
            onStep(4, "Chargement du guide EPG (${idx + 1}/${epgTargets.size})...", pEpg)
        }

        onStep(5, "Chargement des jaquettes films & séries...", 0.80f)
        val rawIcons: List<String> = movieStreams.take(60).map { st: StreamItem -> st.icon } + seriesStreams.take(60).map { st: StreamItem -> st.icon }
        val urls: List<String> = rawIcons
            .map { raw: String -> val s = raw.trim(); if (s.startsWith("//")) "https:$s" else s }
            .filter { u: String -> u.startsWith("http") }
            .distinct()

        urls.forEachIndexed { idx: Int, u: String ->
            if (PosterCache.lru.get(u) == null) {
                try {
                    val req = Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()
                    val b = FastHttp.client.newCall(req).execute().body?.bytes()
                    if (b != null && b.isNotEmpty()) {
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, opts)?.let { bmp -> PosterCache.lru.put(u, bmp) }
                    }
                } catch (_: Exception) {}
            }
            val pct = 0.78f + (0.22f * (idx + 1).toFloat() / maxOf(1, urls.size))
            onStep(5, "Chargement des jaquettes films & séries (${idx + 1}/${urls.size})...", pct)
        }

        onStep(6, "Tout est prêt !", 1f)
        kotlinx.coroutines.delay(1500)
        loadedKey = k
        isReady = true
    }
}

data class PlaylistAccount(val name: String, val url: String, val user: String, val pass: String)
data class CategoryItem(val id: String, val name: String)
data class StreamItem(val id: String, val name: String, val streamUrl: String, val icon: String = "",
    val plot: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = NeonPurple, background = BgDark)) {
                JujuWanMainApp()
            }
        }
    }
}

@Composable
fun JujuWanMainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("juju_wan_data", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf("HOME") }
    var activeAccount by remember {
        val u = prefs.getString("active_url", "") ?: ""
        val usr = prefs.getString("active_user", "") ?: ""
        val p = prefs.getString("active_pass", "") ?: ""
        val n = prefs.getString("active_name", "Serveur Actif") ?: "Serveur Actif"
        mutableStateOf(if (u.isNotEmpty()) PlaylistAccount(n, u, usr, p) else null)
    }
    var playingUrl by remember { mutableStateOf("") }
    var playingTitle by remember { mutableStateOf("") }

    var currentDateTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("EEEE dd MMM - HH:mm", Locale.FRENCH)
        while (true) {
            currentDateTime = sdf.format(Date()).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(12.dp)
    ) {
        if (currentScreen != "PLAYER") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Juju Wan", color = NeonPurpleGlow, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(12.dp))
                    val subtitle = when (currentScreen) {
                        "HOME" -> "Le lecteur premium"
                        "PLAYLISTS" -> "Ajouter une playlist"
                        "LIVE" -> "Télévision en direct"
                        "MOVIES" -> "Films"
                        "SERIES" -> "Séries"
                        else -> ""
                    }
                    Text(subtitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(currentDateTime, color = NeonPurpleGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        var isPreloading by remember { mutableStateOf(false) }
        var syncStep by remember { mutableStateOf(1) }
        var syncLabel by remember { mutableStateOf("Chargement des chaînes en direct...") }
        var syncProgress by remember { mutableStateOf(0.1f) }

        LaunchedEffect(activeAccount) {
            val acc = activeAccount
            if (acc != null && (!ServerCache.isReady || ServerCache.loadedKey != "${acc.url}_${acc.user}")) {
                isPreloading = true
                syncStep = 1
                ServerCache.syncFull(acc) { step, label, progress ->
                    syncStep = step
                    syncLabel = label
                    syncProgress = progress
                }
                ServerCache.loadedForUrl = "${acc.url}_${acc.user}"
                isPreloading = false
            }
        }

        if (isPreloading && currentScreen != "PLAYLISTS") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                border = BorderStroke(1.5.dp, NeonPurpleGlow),
                modifier = Modifier.width(480.dp).padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Le contenu de votre serveur charge pour la première fois, cela va prendre un certain moment...",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    val stepsList = listOf(
                        1 to "Chargement des chaînes en direct",
                        2 to "Chargement des films",
                        3 to "Chargement des séries",
                        4 to "Chargement du guide EPG",
                        5 to "Chargement des jaquettes films & séries"
                    )
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        stepsList.forEach { (stNum, stTitle) ->
                            val done = syncStep > stNum
                            val active = syncStep == stNum
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (done) {
                                        Text("✓", color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                    } else if (active) {
                                        CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(14.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(1.dp, Color.Gray)
                                        ) {}
                                    }
                                    Text(
                                        stTitle,
                                        color = if (done || active) Color.White else Color.Gray,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (active || done) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                if (done) {
                                    Text(
                                        "OK",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                } else if (active) {
                                    Text(
                                        "En cours...",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        } else when (currentScreen) {
            "HOME" -> HomeDashboard(
                onOpenCategory = { cat ->
                    if (activeAccount == null) {
                        Toast.makeText(context, "Ajoutez d'abord une playlist serveur", Toast.LENGTH_SHORT).show()
                        currentScreen = "PLAYLISTS"
                    } else {
                        currentScreen = cat
                    }
                },
                onOpenPlaylists = { currentScreen = "PLAYLISTS" },
                onOpenSettings = { Toast.makeText(context, "Décodeur Universel 4K/HDR/DTS Activé", Toast.LENGTH_SHORT).show() }
            )

            "PLAYLISTS" -> PlaylistManagerScreen(
                prefs = prefs,
                onBack = { currentScreen = "HOME" },
                onSelectAccount = { acc ->
                    activeAccount = acc
                    prefs.edit()
                        .putString("active_name", acc.name)
                        .putString("active_url", acc.url)
                        .putString("active_user", acc.user)
                        .putString("active_pass", acc.pass)
                        .apply()
                    Toast.makeText(context, "Connecté à : ${acc.name}", Toast.LENGTH_SHORT).show()
                    currentScreen = "HOME"
                }
            )

            "LIVE", "MOVIES", "SERIES" -> activeAccount?.let { acc ->
                MediaBrowserScreen(
                    mode = currentScreen,
                    account = acc,
                    onBack = { currentScreen = "HOME" }, onSwitchMode = { currentScreen = it },
                    onPlayFullScreen = { title, url ->
                        playingTitle = title
                        playingUrl = url
                        currentScreen = "PLAYER"
                    }
                )
            }

            "PLAYER" -> FullScreenVideoPlayer(
                title = playingTitle,
                url = playingUrl,
                onBack = { currentScreen = "HOME" }
            )
        }
    }
}

@Composable
fun HomeDashboard(onOpenCategory: (String) -> Unit, onOpenPlaylists: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().drawBehind {
        val w = size.width; val h = size.height; val c = 14.dp.toPx()
        val fr = Path().apply { moveTo(c, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h - c); lineTo(w - c, h); lineTo(c, h); lineTo(0f, h - c); lineTo(0f, c); close() }
        drawPath(fr, Color(0xFF38BDF8).copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    }.padding(horizontal = 54.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            NeonMainCard("Télévision\nen direct", Icons.Default.Tv, Modifier.weight(1f)) { onOpenCategory("LIVE") }
            NeonMainCard("Films", Icons.Default.Movie, Modifier.weight(1f)) { onOpenCategory("MOVIES") }
            NeonMainCard("Séries", Icons.Default.VideoLibrary, Modifier.weight(1f)) { onOpenCategory("SERIES") }
        }
        Row(modifier = Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            NeonBottomButton("Changer de playlist de serveur", Icons.Default.Dns, Modifier.weight(1f), onOpenPlaylists)
            NeonBottomButton("Réglage de l'application", Icons.Default.Settings, Modifier.weight(1f), onOpenSettings)
        }
    }
}

@Composable
fun NeonMainCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable { onClick() }.drawBehind {
        val w = size.width; val h = size.height; val c = 16.dp.toPx(); val m = 7.dp.toPx()
        val out = Path().apply {
            moveTo(c, 0f); lineTo(w*0.34f, 0f); lineTo(w*0.37f, m); lineTo(w*0.63f, m); lineTo(w*0.66f, 0f); lineTo(w - c, 0f)
            lineTo(w, c); lineTo(w, h*0.33f); lineTo(w - m, h*0.36f); lineTo(w - m, h*0.64f); lineTo(w, h*0.67f); lineTo(w, h - c)
            lineTo(w - c, h); lineTo(w*0.66f, h); lineTo(w*0.63f, h - m); lineTo(w*0.37f, h - m); lineTo(w*0.34f, h); lineTo(c, h)
            lineTo(0f, h - c); lineTo(0f, h*0.67f); lineTo(m, h*0.64f); lineTo(m, h*0.36f); lineTo(0f, h*0.33f); lineTo(0f, c); close()
        }
        val ctr = Offset(w / 2f, h / 2f); val rad = minOf(w, h) * 0.45f
        drawPath(out, Brush.radialGradient(listOf(Color(0xFF53249E), Color(0xFF211147), Color(0xFF0C0918)), ctr, rad * 1.4f))
        drawPath(out, NeonPurple.copy(alpha = 0.3f), style = Stroke(5.dp.toPx()))
        drawPath(out, Brush.verticalGradient(listOf(NeonPurpleGlow, Color(0xFF38BDF8), NeonPurpleGlow)), style = Stroke(1.8.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9353FB), Color(0xFF4C1C35), Color.Transparent), ctr, rad), rad, ctr)
        drawCircle(NeonPurpleGlow.copy(alpha = 0.8f), rad * 0.82f, ctr, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color(0xFF38BDF8).copy(alpha = 0.6f), rad * 0.66f, ctr, style = Stroke(1.dp.toPx()))
        drawLine(Color(0xFF38BDF8).copy(alpha = 0.6f), Offset(10.dp.toPx(), 10.dp.toPx()), Offset(24.dp.toPx(), 10.dp.toPx()), 1.2.dp.toPx())
        drawLine(Color(0xFF38BDF8).copy(alpha = 0.6f), Offset(w - 24.dp.toPx(), h - 10.dp.toPx()), Offset(w - 10.dp.toPx(), h - 10.dp.toPx()), 1.2.dp.toPx())
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Icon(icon, null, tint = Color(0xFFF0D8FF), modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun NeonBottomButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable { onClick() }.drawBehind {
        val w = size.width; val h = size.height; val b = 14.dp.toPx()
        val hud = Path().apply { moveTo(b, 0f); lineTo(w - b, 0f); lineTo(w, h / 2f); lineTo(w - b, h); lineTo(b, h); lineTo(0f, h / 2f); close() }
        drawPath(hud, Brush.horizontalGradient(listOf(Color(0xFF180F30), Color(0xFF331A66), Color(0xFF180F30))))
        drawPath(hud, NeonPurple.copy(alpha = 0.35f), style = Stroke(4.dp.toPx()))
        drawPath(hud, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), NeonPurpleGlow, Color(0xFF38BDF8))), style = Stroke(1.6.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Icon(icon, null, tint = NeonPurpleGlow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
fun PlaylistManagerScreen(
    prefs: android.content.SharedPreferences,
    onBack: () -> Unit,
    onSelectAccount: (PlaylistAccount) -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var isM3uMode by remember { mutableStateOf(false) }
    var m3uLink by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var savedList by remember { mutableStateOf(loadSavedAccounts(prefs)) }

    fun parseM3uUrl(raw: String) {
        m3uLink = raw
        try {
            val uri = android.net.Uri.parse(raw.trim())
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: ""
            val port = if (uri.port != -1) ":${uri.port}" else ""
            if (host.isNotEmpty()) url = "$scheme://$host$port"
            uri.getQueryParameter("username")?.let { user = it }
            uri.getQueryParameter("password")?.let { pass = it }
            if (name.isBlank() && host.isNotEmpty()) name = host.substringBefore(".")
        } catch (_: Exception) {}
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isM3uMode = false },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isM3uMode) NeonPurple else BgDark)
                    ) { Text("Xtream Codes API", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { isM3uMode = true },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isM3uMode) NeonPurple else BgDark)
                    ) { Text("Lien M3U (Auto)", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                }

                if (isM3uMode) {
                    SlimInputBox(name, { name = it }, "Nom de la playlist (ex: Ma TV)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SlimInputBox(m3uLink, { parseM3uUrl(it) }, "Collez votre lien M3U complet (http://.../get.php?...)")
                        }
                        Button(
                            onClick = { clipboard.getText()?.text?.let { parseM3uUrl(it) } },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) { Text("Coller", color = BgDark, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text(
                        text = if (url.isNotEmpty() && user.isNotEmpty()) "Détecté : $url  |  User : $user" else "Le serveur, l'utilisateur et le mot de passe seront remplis automatiquement.",
                        color = if (url.isNotEmpty()) Color(0xFF38BDF8) else Color.Gray,
                        fontSize = 10.sp
                    )
                } else {
                    SlimInputBox(name, { name = it }, "Nom de la playlist (nommé comme vous le souhaitez)")
                    SlimInputBox(url, { url = it }, "URL du serveur (http://...)")
                    SlimInputBox(user, { user = it }, "Nom d'utilisateur")
                    SlimInputBox(pass, { pass = it }, "Mot de passe", isPassword = true)
                }

                Row(modifier = Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, NeonPurple)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retour", color = Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                                val finalName = name.ifBlank { "Playlist ${savedList.size + 1}" }
                                val newAcc = PlaylistAccount(finalName.trim(), url.trim().removeSuffix("/"), user.trim(), pass.trim())
                                val updated = savedList + newAcc
                                saveAccounts(prefs, updated)
                                savedList = updated
                                onSelectAccount(newAcc)
                            }
                        },
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Valider & Connecter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Mes listes enregistrées", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(savedList) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgDark,
                            border = BorderStroke(1.dp, NeonPurple),
                            modifier = Modifier.fillMaxWidth().clickable { onSelectAccount(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                IconButton(
                                    onClick = {
                                        val updated = savedList.filter { it != item }
                                        saveAccounts(prefs, updated)
                                        savedList = updated
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = NeonPurpleGlow, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SlimInputBox(value: String, onValueChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BgDark,
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().height(34.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = Color.Gray, fontSize = 9.5.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(NeonPurpleGlow),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun Modifier.hudFrame(active: Boolean = false, cutDp: Float = 10f): Modifier = this.drawBehind {
    val w = size.width; val h = size.height; val c = cutDp.dp.toPx()
    val path = Path().apply {
        moveTo(c, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h - c)
        lineTo(w - c, h); lineTo(c, h); lineTo(0f, h - c); lineTo(0f, c); close()
    }
    val bg = if (active) {
        Brush.radialGradient(listOf(Color(0xFF5A25B5), Color(0xFF281254), Color(0xFF120B26)), Offset(w / 2f, h / 2f), maxOf(w, h) * 0.7f)
    } else {
        Brush.radialGradient(listOf(Color(0xFF231347), Color(0xFF130B29), Color(0xFF0A0617)), Offset(w / 2f, h / 2f), maxOf(w, h) * 0.8f)
    }
    drawPath(path, bg)
    drawPath(path, NeonPurple.copy(alpha = if (active) 0.4f else 0.2f), style = Stroke(4.dp.toPx()))
    drawPath(path, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), NeonPurpleGlow, Color(0xFF38BDF8))), style = Stroke(if (active) 1.8.dp.toPx() else 1.2.dp.toPx()))
    val tk = 8.dp.toPx()
    drawLine(Color(0xFF38BDF8), Offset(c, 2.dp.toPx()), Offset(c + tk, 2.dp.toPx()), 1.5.dp.toPx())
    drawLine(Color(0xFF38BDF8), Offset(w - c - tk, h - 2.dp.toPx()), Offset(w - c, h - 2.dp.toPx()), 1.5.dp.toPx())
}

data class VodDetails(
    val title: String,
    val poster: String,
    val plot: String,
    val genre: String,
    val releaseDate: String,
    val duration: String,
    val rating: String,
    val director: String,
    val cast: String
)

suspend fun fetchVodDetails(acc: PlaylistAccount, item: StreamItem): VodDetails = withContext(Dispatchers.IO) {
    try {
        val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=get_vod_info&vod_id=${item.id}"
        val raw = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "{}"
        val root = JSONObject(raw)
        val info = root.optJSONObject("info") ?: JSONObject()
        val movieData = root.optJSONObject("movie_data") ?: JSONObject()

        val title = movieData.optString("name").ifBlank { info.optString("name", item.name) }
        val poster = info.optString("movie_image")
            .ifBlank { info.optString("cover_big") }
            .ifBlank { movieData.optString("stream_icon", item.icon) }
        val plot = info.optString("plot")
            .ifBlank { info.optString("description") }
            .ifBlank { item.plot }
        val genre = info.optString("genre", "")
        val releaseDate = info.optString("releasedate").ifBlank { info.optString("releaseDate", "") }
        val duration = info.optString("duration", "")
        val rating = info.optString("rating", "")
        val director = info.optString("director", "")
        val cast = info.optString("cast").ifBlank { info.optString("actors", "") }

        VodDetails(title, poster, plot, genre, releaseDate, duration, rating, director, cast)
    } catch (_: Exception) {
        VodDetails(item.name, item.icon, item.plot, "", "", "", "", "", "")
    }
}

@Composable
fun MovieDetailScreen(
    account: PlaylistAccount,
    item: StreamItem,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit
) {
    var details by remember(item.id) {
        mutableStateOf(VodDetails(item.name, item.icon, item.plot, "", "", "", "", "", ""))
    }
    var isLoading by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoading = true
        details = fetchVodDetails(account, item)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hudFrame(active = false, cutDp = 12f)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .hudFrame(active = true, cutDp = 10f)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                ServerPosterImage(url = details.poster.ifBlank { item.icon }, isMovie = true)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = details.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    val metaParts = listOfNotNull(
                        details.releaseDate.takeIf { it.isNotBlank() },
                        details.duration.takeIf { it.isNotBlank() },
                        details.genre.takeIf { it.isNotBlank() },
                        details.rating.takeIf { it.isNotBlank() && it != "0" }?.let { "Note: $it" }
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString("  •  "),
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (details.director.isNotBlank()) {
                        Text(
                            text = "Réalisateur : ${details.director}",
                            color = Color.LightGray,
                            fontSize = 10.5.sp
                        )
                    }

                    if (details.cast.isNotBlank()) {
                        Text(
                            text = "Casting : ${details.cast}",
                            color = Color.LightGray,
                            fontSize = 10.5.sp,
                            maxLines = 2
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    if (isLoading && details.plot.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = NeonPurpleGlow, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text("Chargement des informations du serveur...", color = Color.Gray, fontSize = 11.sp)
                        }
                    } else {
                        Text(
                            text = details.plot.ifBlank { "Aucun descriptif fourni par le serveur pour ce film." },
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight()
                            .hudFrame(active = true, cutDp = 8f)
                            .clickable { onPlay(details.title, item.streamUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lancer le film", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .hudFrame(active = false, cutDp = 8f)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retour", color = Color(0xFF38BDF8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaBrowserScreen(
    mode: String,
    account: PlaylistAccount,
    onBack: () -> Unit,
    onPlayFullScreen: (String, String) -> Unit,
    onSwitchMode: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var activeMode by remember(mode) { mutableStateOf(mode) }
    var categories by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>("ALL") }
    var selectedStream by remember { mutableStateOf<StreamItem?>(null) }
    var selectedMovieDetail by remember { mutableStateOf<StreamItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var epgInfo by remember { mutableStateOf("") }

    LaunchedEffect(activeMode) {
        selectedCategory = "ALL"
        categories = when (activeMode) { "LIVE" -> ServerCache.liveCats; "MOVIES" -> ServerCache.movieCats; else -> ServerCache.seriesCats }
        streams = when (activeMode) { "LIVE" -> ServerCache.liveStreams; "MOVIES" -> ServerCache.movieStreams; else -> ServerCache.seriesStreams }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().hudFrame(active = false, cutDp = 8f).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Accueil", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
            Text("|", color = Color(0xFF38BDF8), fontSize = 11.sp)
            listOf("LIVE" to "En direct", "MOVIES" to "Films", "SERIES" to "Séries").forEach { (mKey, label) ->
                val sel = activeMode == mKey
                Text(label, color = if (sel) Color(0xFF38BDF8) else Color.White, fontSize = 11.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.clickable { selectedStream = null; selectedMovieDetail = null; epgInfo = ""; searchQuery = ""; activeMode = mKey; onSwitchMode(mKey) })
                Text("|", color = NeonPurpleGlow, fontSize = 11.sp)
            }
            Box(modifier = Modifier.weight(1f)) { SlimInputBox(searchQuery, { searchQuery = it }, "Rechercher...") }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(0.75f).fillMaxHeight().hudFrame(active = false, cutDp = 10f).padding(6.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(categories.filter { it.name.contains(searchQuery, true) }) { cat ->
                        val isSel = selectedCategory == cat.id
                        Box(modifier = Modifier.fillMaxWidth().hudFrame(active = isSel, cutDp = 5f).clickable {
                            selectedCategory = cat.id
                            if (cat.id == "ALL") streams = when (activeMode) { "LIVE" -> ServerCache.liveStreams; "MOVIES" -> ServerCache.movieStreams; else -> ServerCache.seriesStreams }
                            else scope.launch { isLoading = true; streams = fetchStreams(account, activeMode, cat.id); isLoading = false }
                        }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(cat.name, color = if (isSel) Color.White else Color.LightGray, fontSize = 9.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
            }
            if (activeMode == "MOVIES" && selectedMovieDetail != null) {
                MovieDetailScreen(
                    account = account,
                    item = selectedMovieDetail!!,
                    onBack = { selectedMovieDetail = null },
                    onPlay = { t, u -> onPlayFullScreen(t, u) }
                )
            } else if (activeMode != "LIVE") {
                Box(modifier = Modifier.weight(2.8f).fillMaxHeight().hudFrame(active = false, cutDp = 12f).padding(8.dp)) {
                    if (isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NeonPurpleGlow) }
                    else {
                        val filtered = streams.filter { it.name.contains(searchQuery, true) }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 95.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(count = filtered.size, key = { idx -> "${activeMode}_${filtered[idx].id}_$idx" }) { idx ->
                                val item = filtered[idx]
                                Column(modifier = Modifier.hudFrame(active = false, cutDp = 7f).clickable { if (activeMode == "MOVIES") selectedMovieDetail = item else onPlayFullScreen(item.name, item.streamUrl) }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.fillMaxWidth().height(115.dp).background(BgDark, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                        ServerPosterImage(url = item.icon, isMovie = (activeMode == "MOVIES"))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.name, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
            Box(modifier = Modifier.weight(0.95f).fillMaxHeight().hudFrame(active = false, cutDp = 10f).padding(6.dp)) {
                if (isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NeonPurpleGlow) }
                else {
                    val filtered = streams.filter { it.name.contains(searchQuery, true) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(filtered.size) { idx ->
                            val item = filtered[idx]
                            val isSel = selectedStream == item
                            Row(modifier = Modifier.fillMaxWidth().hudFrame(active = isSel, cutDp = 5f).clickable {
                                selectedStream = item
                                if (activeMode == "LIVE") scope.launch { epgInfo = "Chargement EPG..."; epgInfo = fetchLiveEpg(account, item.id) }
                            }.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                                Text(item.name, color = Color.White, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1.85f).fillMaxHeight().hudFrame(active = false, cutDp = 12f).padding(8.dp)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedStream != null) {
                        val item = selectedStream!!
                        val ctx = LocalContext.current
                        val pv = if (activeMode == "LIVE") remember(item.streamUrl) { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(item.streamUrl)); prepare(); playWhenReady = true } } else null
                        DisposableEffect(item.streamUrl, activeMode) { onDispose { pv?.release() } }
                        if (activeMode == "LIVE" && pv != null) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1.15f).hudFrame(active = true, cutDp = 8f).padding(2.dp).clickable { pv.release(); onPlayFullScreen(item.name, item.streamUrl) }) {
                                AndroidView(factory = { c -> PlayerView(c).apply { player = pv; useController = false } }, update = { it.player = pv }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Column(modifier = Modifier.weight(0.85f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Text(item.name, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(if (activeMode == "LIVE") epgInfo else item.plot.ifBlank { "Flux prêt sur Juju Wan." }, color = Color(0xFF38BDF8), fontSize = 9.5.sp, lineHeight = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().hudFrame(active = true, cutDp = 7f).clickable { pv?.release(); onPlayFullScreen(item.name, item.streamUrl) }, contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Plein écran", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(modifier = Modifier.weight(0.7f).fillMaxHeight().hudFrame(active = false, cutDp = 7f).clickable { pv?.release(); onBack() }, contentAlignment = Alignment.Center) {
                                Text("Accueil", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sélectionnez une chaîne pour lancer l aperçu", color = Color.Gray, fontSize = 11.sp) }
                    }
                }
            }

            }        }
    }
}
object FastHttp {
    val client: OkHttpClient = run {
        val tm = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        val sc = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf<javax.net.ssl.TrustManager>(tm), java.security.SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(sc.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
    val imgGate = kotlinx.coroutines.sync.Semaphore(6)
    val streamsMemo = java.util.concurrent.ConcurrentHashMap<String, List<StreamItem>>()
    val epgMemo = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
}

object PosterCache {
    val lru = object : android.util.LruCache<String, android.graphics.Bitmap>(160) {}
}

@Composable
fun ServerPosterImage(url: String, isMovie: Boolean) {
    val cleanUrl = url.trim().let { if (it.startsWith("//")) "https:$it" else it }
    var bmp by remember(cleanUrl) { mutableStateOf(if (cleanUrl.isNotEmpty()) PosterCache.lru.get(cleanUrl) else null) }
    var loading by remember(cleanUrl) { mutableStateOf(bmp == null && cleanUrl.startsWith("http")) }
    LaunchedEffect(cleanUrl) {
        if (bmp == null && cleanUrl.startsWith("http")) {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                FastHttp.imgGate.acquire()
                try {
                    val req = Request.Builder().url(cleanUrl).header("User-Agent", "Mozilla/5.0").build()
                    val bytes = FastHttp.client.newCall(req).execute().body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    } else null
                } catch (_: Exception) { null } finally { FastHttp.imgGate.release() }
            }
            if (loaded != null) {
                PosterCache.lru.put(cleanUrl, loaded)
                bmp = loaded
            }
            loading = false
        } else {
            loading = false
        }
    }
    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).clipToBounds(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (loading) {
            CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(if (isMovie) Icons.Default.Movie else Icons.Default.VideoLibrary, null, tint = NeonPurpleGlow, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun BadgeChip(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = BgDark, border = BorderStroke(1.dp, NeonPurpleGlow)) {
        Text(label, color = NeonPurpleGlow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun FullScreenVideoPlayer(title: String, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var audioInfo by remember { mutableStateOf("Audio: Auto") }
    var audioIndex by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        val rf = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, rf).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                .setPreferredAudioLanguages("fr", "fre", "fra", "en", "eng")
                .build()
            volume = 1.0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { player = exoPlayer; useController = !url.endsWith(".m3u8") } },
            modifier = Modifier.fillMaxSize().clipToBounds()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.85f))) {
                    Text("← Quitter", color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
suspend fun fetchLiveEpg(acc: PlaylistAccount, sid: String): String = withContext(Dispatchers.IO) { try { val u = acc.url + "/player_api.php?username=" + acc.user + "&password=" + acc.pass + "&action=get_short_epg&stream_id=" + sid + "&limit=15"; val r = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "{}"; val a = JSONObject(r).optJSONArray("epg_listings"); if (a != null && a.length() > 0) { (0 until a.length()).joinToString("\n") { i -> val o = a.getJSONObject(i); val tt = try { String(android.util.Base64.decode(o.optString("title"), 0)) } catch (_: Exception) { o.optString("title") }; val h1 = o.optString("start").substringAfter(" ").take(5); val h2 = o.optString("end", o.optString("stop")).substringAfter(" ").take(5); if (h1.isNotEmpty() && h2.isNotEmpty()) "$h1 - $h2  |  $tt" else tt } } else "Aucun EPG disponible" } catch (_: Exception) { "EPG indisponible" } }
suspend fun fetchCategories(acc: PlaylistAccount, action: String): List<CategoryItem> = withContext(Dispatchers.IO) {
    try {
        val apiUrl = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
        val res = FastHttp.client.newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "[]"
        val arr = JSONArray(res)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            CategoryItem(obj.optString("category_id"), obj.optString("category_name"))
        }
    } catch (e: Exception) { emptyList() }
}

suspend fun fetchStreams(acc: PlaylistAccount, mode: String, catId: String?): List<StreamItem> = withContext(Dispatchers.IO) {
    try {
        val action = when (mode) { "LIVE" -> "get_live_streams"; "MOVIES" -> "get_vod_streams"; else -> "get_series" }
        val catParam = if (catId != null) "&category_id=$catId" else ""
        val apiUrl = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action$catParam"
        val res = FastHttp.client.newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "[]"
        val arr = JSONArray(res)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            val id = obj.optString("stream_id", obj.optString("series_id"))
            val ext = obj.optString("container_extension", "mp4")
            val streamLink = when (mode) {
                "LIVE" -> "${acc.url}/live/${acc.user}/${acc.pass}/$id.m3u8"
                "MOVIES" -> "${acc.url}/movie/${acc.user}/${acc.pass}/$id.$ext"
                else -> "${acc.url}/series/${acc.user}/${acc.pass}/$id.$ext"
            }
            StreamItem(id, obj.optString("name"), streamLink, obj.optString("stream_icon", obj.optString("cover", "")), obj.optString("plot", ""))
        }
    } catch (e: Exception) { emptyList() }
}

fun saveAccounts(prefs: android.content.SharedPreferences, list: List<PlaylistAccount>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("name", it.name); put("url", it.url); put("user", it.user); put("pass", it.pass)
        })
    }
    prefs.edit().putString("saved_playlists", arr.toString()).apply()
}

fun loadSavedAccounts(prefs: android.content.SharedPreferences): List<PlaylistAccount> {
    return try {
        val arr = JSONArray(prefs.getString("saved_playlists", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlaylistAccount(o.getString("name"), o.getString("url"), o.getString("user"), o.getString("pass"))
        }
    } catch (e: Exception) { emptyList() }
}

@Composable
fun CompactField(value: String, onChange: (String) -> Unit, hint: String, isPass: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, fontSize = 9.5.sp, maxLines = 1) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        visualTransformation = if (isPass) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().height(46.dp)
    )
}

@Composable
fun StepCheckRow(loadingText: String, doneText: String, status: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (status) {
                1 -> CircularProgressIndicator(color = NeonPurpleGlow, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                2 -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                else -> Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (status == 2) doneText else loadingText,
                color = if (status == 2) Color(0xFF4ADE80) else if (status == 1) Color.White else Color.Gray,
                fontSize = 13.sp,
                fontWeight = if (status >= 1) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (status == 2) {
            Text("OK", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}