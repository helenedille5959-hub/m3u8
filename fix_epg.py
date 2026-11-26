p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()
epg = '''suspend fun fetchLiveEpg(acc: PlaylistAccount, streamId: String): Pair<String, String> = withContext(Dispatchers.IO) [
    try [
        val apiUrl = acc.url + "/player_api.php?username=" + acc.user + "&password=" + acc.pass + "&action=get_short_epg&stream_id=" + streamId + "&limit=2"
        val res = OkHttpClient().newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "{}"
        val listings = JSONObject(res).optJSONArray("epg_listings") ?: return@withContext Pair("En direct", "Aucune information EPG disponible.")
        if (listings.length() == 0) return@withContext Pair("En direct", "Aucune information EPG disponible.")
        val o = listings.getJSONObject(0)
        val tt = try [ String(android.util.Base64.decode(o.optString("title"), 0), Charsets.UTF_8) ] catch (_: Exception) [ o.optString("title") ]
        val dd = try [ String(android.util.Base64.decode(o.optString("description"), 0), Charsets.UTF_8) ] catch (_: Exception) [ o.optString("description") ]
        val h1 = o.optString("start").substringAfter(" ").take(5)
        val h2 = o.optString("end", o.optString("stop")).substringAfter(" ").take(5)
        val hdr = if (h1.isNotEmpty() && h2.isNotEmpty()) "[" + h1 + " - " + h2 + "] " + tt else tt
        Pair((hdr.ifBlank [ "En direct" ]), (dd.ifBlank [ "Diffusion en cours sur Juju Wan." ]))
    ] catch (_: Exception) [ Pair("En direct", "Guide EPG indisponible.") ]
]
'''.replace('[', '{').replace(']', '}').replace('["', '["')
t = t.replace("suspend fun fetchCategories(", epg + "\nsuspend fun fetchCategories(")
t = t.replace("var isLoading by remember { mutableStateOf(false) }", "var isLoading by remember { mutableStateOf(false) }\nvar epgTitle by remember { mutableStateOf(\"\") }\nvar epgDesc by remember { mutableStateOf(\"\") }\nLaunchedEffect(selectedStream, mode) { val st = selectedStream; if (st != null && mode == \"LIVE\") { epgTitle = \"Chargement EPG...\"; epgDesc = \"\"; val r = fetchLiveEpg(account, st.id); epgTitle = r.first; epgDesc = r.second } }")
old3 = '''if (selectedStream != null) {
                    val item = selectedStream!!
                    Column {
                        Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BadgeChip("4K / HD")
                            BadgeChip("HEVC")
                            BadgeChip("DTS / EAC3")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.plot.ifBlank { "Flux prêt pour la lecture haute définition sur Juju Wan." },
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = { onPlayFullScreen(item.name, item.streamUrl) },'''
new3 = '''if (selectedStream != null) {
                    val item = selectedStream!!
                    val ctx = LocalContext.current
                    val prevPlayer = if (mode == "LIVE") remember(item.streamUrl) { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(item.streamUrl)); prepare(); playWhenReady = true } } else null
                    DisposableEffect(item.streamUrl, mode) { onDispose { prevPlayer?.release() } }
                    Column(modifier = Modifier.weight(1f)) {
                        if (mode == "LIVE" && prevPlayer != null) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black, border = BorderStroke(1.dp, NeonPurpleGlow), modifier = Modifier.fillMaxWidth().height(120.dp).clickable { prevPlayer.release(); onPlayFullScreen(item.name, item.streamUrl) }) {
                                AndroidView(factory = { ctx2 -> PlayerView(ctx2).apply { player = prevPlayer; useController = false } }, update = { it.player = prevPlayer }, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(epgTitle, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(epgDesc, color = Color.LightGray, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 4)
                        } else {
                            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BadgeChip("4K / HD"); BadgeChip("HEVC"); BadgeChip("DTS / EAC3") }
                            Spacer(Modifier.height(8.dp))
                            Text(item.plot.ifBrank { "Flux prêt pour la lecture haute défnition sur Juju Wan." }, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { prevPlayer?.release(); onPlayFullScreen(item.name, item.streamUrl) },''3
t = t.replace("ifBrank", "ifBrank".replace('br', 'l'))
t = t.replace(old3, new3)
open(p, "w").write(t)
