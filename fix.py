p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()

for imp in ["import androidx.compose.ui.draw.clip\n", "import androidx.compose.ui.draw.clipToBounds\n", "import androidx.compose.ui.graphics.asImageBitmap\n"]:
    if imp.strip() not in t:
        t = t.replace("import androidx.compose.ui.draw.drawBehind", imp + "import androidx.compose.ui.draw.drawBehind", 1)

s_sc = t.index("object ServerCache")
e_sc = t.index("suspend fun fetchLiveEpg")
sc = """object ServerCache {
    var loadedKey = ""
    var liveCats: List<CategoryItem> = emptyList()
    var movieCats: List<CategoryItem> = emptyList()
    var seriesCats: List<CategoryItem> = emptyList()
    var liveStreams: List<StreamItem> = emptyList()
    var movieStreams: List<StreamItem> = emptyList()
    var seriesStreams: List<StreamItem> = emptyList()

    suspend fun preload(acc: PlaylistAccount) = withContext(Dispatchers.IO) {
        val k = "${acc.url}_${acc.user}"
        if (loadedKey == k && liveStreams.isNotEmpty()) return@withContext
        PosterCache.lru.evictAll()
        fun reqArr(act: String) = try {
            val u = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$act"
            org.json.JSONArray(PosterCache.client.newCall(Request.Builder().url(u).build()).execute().body?.string() ?: "[]")
        } catch (_: Exception) { org.json.JSONArray() }
        fun cats(a: org.json.JSONArray) = buildList {
            add(CategoryItem("ALL", "Tout afficher"))
            for (i in 0 until a.length()) { val c = a.getJSONObject(i); add(CategoryItem(c.optString("category_id"), c.optString("category_name"))) }
        }
        liveCats = cats(reqArr("get_live_categories"))
        movieCats = cats(reqArr("get_vod_categories"))
        seriesCats = cats(reqArr("get_series_categories"))
        val aL = reqArr("get_live_streams")
        liveStreams = (0 until aL.length()).map { i -> val x = aL.getJSONObject(i); val id = x.optString("stream_id"); StreamItem(id, x.optString("name"), "${acc.url}/live/${acc.user}/${acc.pass}/$id.ts", x.optString("stream_icon", ""), "") }
        val aM = reqArr("get_vod_streams")
        movieStreams = (0 until aM.length()).map { i -> val x = aM.getJSONObject(i); val id = x.optString("stream_id"); val ext = x.optString("container_extension", "mp4").ifBlank { "mp4" }; StreamItem(id, x.optString("name"), "${acc.url}/movie/${acc.user}/${acc.pass}/$id.$ext", x.optString("stream_icon", x.optString("movie_image", "")), x.optString("plot", "")) }
        val aS = reqArr("get_series")
        seriesStreams = (0 until aS.length()).map { i -> val x = aS.getJSONObject(i); val id = x.optString("series_id", x.optString("stream_id")); StreamItem(id, x.optString("name"), "${acc.url}/series/${acc.user}/${acc.pass}/$id.mp4", x.optString("cover", x.optString("stream_icon", "")), x.optString("plot", "")) }
        loadedKey = k
    }
}

"""
t = t[:s_sc] + sc + t[e_sc:]

hud_pos = t.index("fun Modifier.hudFrame(")
start_sp = t.index("object PosterCache") if "object PosterCache" in t else hud_pos
sp = """object PosterCache {
    val lru = object : android.util.LruCache<String, android.graphics.Bitmap>(160) {}
    val gate = kotlinx.coroutines.sync.Semaphore(6)
    val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
}

@Composable
fun ServerPosterImage(url: String, isMovie: Boolean) {
    val cleanUrl = url.trim().let { if (it.startsWith("//")) "https:$it" else it }
    var bmp by remember(cleanUrl) { mutableStateOf(if (cleanUrl.isNotEmpty()) PosterCache.lru.get(cleanUrl) else null) }
    var loading by remember(cleanUrl) { mutableStateOf(cleanUrl.startsWith("http") && bmp == null) }
    LaunchedEffect(cleanUrl) {
        if (cleanUrl.startsWith("http")) {
            val c = PosterCache.lru.get(cleanUrl)
            if (c != null) { bmp = c; loading = false } else {
                loading = true
                val ld = withContext(Dispatchers.IO) {
                    PosterCache.gate.acquire()
                    try {
                        val b = PosterCache.client.newCall(Request.Builder().url(cleanUrl).header("User-Agent", "Mozilla/5.0").build()).execute().body?.bytes()
                        if (b != null && b.isNotEmpty()) android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) else null
                    } catch (_: Exception) { null } finally { PosterCache.gate.release() }
                }
                if (ld != null) { PosterCache.lru.put(cleanUrl, ld); bmp = ld }
                loading = false
            }
        } else { bmp = null; loading = false }
    }
    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).clipToBounds(), contentAlignment = Alignment.Center) {
        if (bmp != null) androidx.compose.foundation.Image(bitmap = bmp!!.asImageBitmap(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().clipToBounds())
        else if (loading) Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp)); Spacer(Modifier.height(4.dp)); Text("Chargement...", color = Color(0xFF38BDF8), fontSize = 8.sp) }
        else Icon(if (isMovie) Icons.Default.Movie else Icons.Default.VideoLibrary, null, tint = NeonPurpleGlow, modifier = Modifier.size(26.dp))
    }
}

"""
t = t[:start_sp] + sp + t[hud_pos:]

if "if (item.icon.isNotBlank()) {" in t:
    s_if = t.index("if (item.icon.isNotBlank()) {")
    e_if = t.index("Spacer(Modifier.height(4.dp))", s_if)
    t = t[:s_if] + 'ServerPosterImage(item.icon, activeMode == "MOVIES")\n                                    }\n                                    ' + t[e_if:]
t = t.replace("items(filtered.size) { idx ->", 'items(count = filtered.size, key = { idx -> "${activeMode}_${filtered[idx].id}_$idx" }) { idx ->', 1)
t = t.replace("ExoPlayer.Builder(context).build().apply {", "ExoPlayer.Builder(context, androidx.media3.exoplayer.DefaultRenderersFactory(context).setEnableDecoderFallback(true)).build().apply { volume = 1.0f;", 1)

assert "hudFrame" in t and "Chargement..." in t and t.count("{") == t.count("}")
open(p, "w").write(t)
print("OK_FIXED_AND_PUSHED")
