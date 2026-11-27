package com.jujuwan.player

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        PosterCache.lru.evictAll()
        FastHttp.streamsMemo.clear()
        FastHttp.epgMemo.clear()

        fun loadCats(action: String): List<CategoryItem> {
            return try {
                val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
                val body = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]"
                val arr = JSONArray(body)
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

        fun loadArr(action: String): JSONArray {
            return try {
                val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
                val body = FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]"
                JSONArray(body)
            } catch (_: Exception) { JSONArray() }
        }

        onStep(1, "Chargement des chaînes en direct...", 0.15f)
        liveCats = loadCats("get_live_categories")
        val aLive = loadArr("get_live_streams")
        liveStreams = (0 until aLive.length()).map { i ->
            val x = aLive.getJSONObject(i)
            val id = x.optString("stream_id")
            StreamItem(id, x.optString("name"), "${acc.url}/live/${acc.user}/${acc.pass}/$id.m3u8", x.optString("stream_icon", ""), "")
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
        epgTargets.forEachIndexed { idx, st ->
            try { fetchLiveEpg(acc, st.id) } catch (_: Exception) {}
            val pEpg = 0.60f + (0.18f * (idx + 1).toFloat() / maxOf(1, epgTargets.size))
            onStep(4, "Chargement du guide EPG (${idx + 1}/${epgTargets.size})...", pEpg)
        }

        onStep(5, "Chargement des jaquettes films & séries...", 0.80f)
        val rawIcons: List<String> = movieStreams.take(60).map { it.icon } + seriesStreams.take(60).map { it.icon }
        val urls: List<String> = rawIcons
            .map { raw -> val s = raw.trim(); if (s.startsWith("//")) "https:$s" else s }
            .filter { u -> u.startsWith("http") }
            .distinct()

        urls.forEachIndexed { idx, u ->
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
        delay(1200)
        loadedKey = k
        loadedForUrl = k
        isReady = true
    }
}

data class PlaylistAccount(val name: String, val url: String, val user: String, val pass: String)
data class CategoryItem(val id: String, val name: String)
data class StreamItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val icon: String = "",
    val plot: String = ""
)

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

git add app/src/main/java/com/jujuwan/player/MainActivity.kt && git commit -m "Fix chargement complet" && git push

