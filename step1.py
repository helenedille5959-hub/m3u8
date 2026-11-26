p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()
epg = '''
suspend fun fetchLiveEpg(acc: PlaylistAccount, streamId: String): Pair<String, String> = withContext(Dispatchers.IO) {
    try {
        val apiUrl = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=get_short_epg&stream_id=$streamId&limit=2"
        val res = OkHttpClient().newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "{}"
        val listings = JSONObject(res).optJSONArray("epg_listings") ?: return@withContext Pair("En direct", "Aucune information EPG disponible.")
        if (listings.length() == 0) return@withContext Pair("En direct", "Aucune information EPG disponible.")
        val o = listings.getJSONObject(0)
        val tt = try { String(android.util.Base64.decode(o.optString("title"), 0), Charsets.UTF_8) } catch (_: Exception) { o.optString("title") }
        val dd = try { String(android.util.Base64.decode(o.optString("description"), 0), Charsets.UTF_8) } catch (_: Exception) { o.optString("description") }
        val h1 = o.optString("start"). substringAfter(" ").take(5)
        val h2 = o.optString("end", o.optString("stop")).substringAfter(" ").take(5)
        val hdr = if (h1.isNotEmpty() && h2.isNotEmpty()) "[$h1 - $h2] $tt" else tt
        var nextTxt = ""
        if (listings.length() > 1) {
            val o2 = listings.getJSONObject(1)
            val ntt = try { String(android.util.Base64.decode(o2.optString("title"), 0), Charsets.UTF_8) } catch (_: Exception) { o2.optString("title") }
            val nh = o2.optString("start").substringAfter(" ").take(5)
            if (ntt.isNotEmpty()) nextTxt = "\n\nÀ suivre ($nh) : $ntt"
        }
        Pair(hdr.ifBlank { "En direct" }, (dd.ifBlank { "Diffusion en cours sur Juju Wan." }) + nextTxt)
    } catch (_: Exception) { Pair("En direct", "Guide EPG indisponible.") }
}'''
t = t.replace("suspend fun fetchCategories(", epg + "\nsuspend fun fetchCategories(")
open(p, "w").write(t)
