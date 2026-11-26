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
    var loadedKey: String = ""
    var liveCats: List<CategoryItem> = emptyList()
    var movieCats: List<CategoryItem> = emptyList()
    var seriesCats: List<CategoryItem> = emptyList()
    var liveStreams: List<StreamItem> = emptyList()
    var movieStreams: List<StreamItem> = emptyList()
    var seriesStreams: List<StreamItem> = emptyList()

    suspend fun preload(
        acc: PlaylistAccount,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val k = "${acc.url}_${acc.user}"
        if (loadedKey == k && liveStreams.isNotEmpty()) {
            onProgress("Données déjà prêtes", 1f)
            return@withContext
        }
        PosterCache.lru.evictAll()
        FastHttp.streamsMemo.clear()
        FastHttp.epgMemo.clear()

        fun reqArr(act: String) = try {
            val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$act"
            org.json.JSONArray(FastHttp.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]")
        } catch (_: Exception) { org.json.JSONArray() }

        fun cats(a: org.json.JSONArray) = buildList {
            add(CategoryItem("ALL", "Tout afficher"))
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                add(CategoryItem(c.optString("category_id"), c.optString("category_name")))
            }
        }

        // Etape 1 : TV en direct
        onProgress("1/5 • Chargement des chaînes TV en direct...", 0.15f)
        liveCats = cats(reqArr("get_live_categories"))
        val aL = reqArr("get_live_streams")
        liveStreams = (0 until aL.length()).map { i ->
            val x = aL.getJSONObject(i)
            val id = x.optString("stream_id")
            StreamItem(id, x.optString("name"), "${acc.url}/live/${acc.user}/${acc.pass}/$id.ts", x.optString("stream_icon", ""), "")
        }

        // Etape 2 : Films
        onProgress("2/5 • Chargement des Films (${liveStreams.size} chaînes prêtes)...", 0.35f)
        movieCats = cats(reqArr("get_vod_categories"))
        val aM = reqArr("get_vod_streams")
        movieStreams = (0 until aM.length()).map { i ->
            val x = aM.getJSONObject(i)
            val id = x.optString("stream_id")
            val ext = x.optString("container_extension", "mp4").ifBlank { "mp4" }
            StreamItem(id, x.optString("name"), "${acc.url}/movie/${acc.user}/${acc.pass}/$id.$ext", x.optString("stream_icon", x.optString("movie_image", "")), x.optString("plot", ""))
        }

        // Etape 3 : Series
        onProgress("3/5 • Chargement des Séries (${movieStreams.size} films prêts)...", 0.55f)
        seriesCats = cats(reqArr("get_series_categories"))
        val aS = reqArr("get_series")
        seriesStreams = (0 until aS.length()).map { i ->
            val x = aS.getJSONObject(i)
            val id = x.optString("series_id", x.optString("stream_id"))
            StreamItem(id, x.optString("name"), "${acc.url}/series/${acc.user}/${acc.pass}/$id.mp4", x.optString("cover", x.optString("stream_icon", "")), x.optString("plot", ""))
        }

        // Etape 4 : Prechargement du guide EPG (premieres chaines)
        onProgress("4/5 • Chargement du guide EPG...", 0.75f)
        liveStreams.take(12).forEach { st ->
            try { fetchLiveEpg(acc, st.id) } catch (_: Exception) {}
        }

        // Etape 5 : Prechargement des jaquettes Films & Series en cache memoire
        onProgress("5/5 • Chargement des jaquettes Films & Séries...", 0.90f)
        val posterUrls = (movieStreams.take(24).map { it.icon } + seriesStreams.take(24).map { it.icon })
            .map { it.trim().let { u -> if (u.startsWith("//")) "https:$u" else u } }
            .filter { it.startsWith("http") }
            .distinct()
        posterUrls.forEachIndexed { idx, u ->
            if (PosterCache.lru.get(u) == null) {
                try {
                    val req = Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()
                    val b = FastHttp.client.newCall(req).execute().body?.bytes()
                    if (b != null && b.isNotEmpty()) {
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, opts)?.let { PosterCache.lru.put(u, it) }
                    }
                } catch (_: Exception) {}
            }
            val pct = 0.85f + (0.15f * (idx + 1).toFloat() / maxOf(1, posterUrls.size))
            onProgress("5/5 • Jaquettes Films & Séries (${idx + 1}/${posterUrls.size})...", pct)
        }

        onProgress("Synchronisation terminée !", 1.0f)
        loadedKey = k
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