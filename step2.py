p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()
t = t.replace("var isLoading by remember { mutableStateOf(false) }", "var isLoading by remember { mutableStateOf(false) }\nvar epgTitle by remember { mutableStateOf(\"\") }\nvar epgDesc by remember { mutableStateOf(\"\") }\nLaunchedEffect(selectedStream, mode) { val st = selectedStream; if (st != null && mode == \"LIVE\") { epgTitle = \"Chargement EPG...\"; epgDesc = \"\"; val r = fetchLiveEpg(account, st.id); epgTitle = r.first; epgDesc = r.second } }")
old_c3l = '''if (selectedStream != null) {
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
                            text = item.plot.ifBlank { "Flux prêt pour la lecture haute dérinition sur Juju Wan." },
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = { onPlayFullScreen(item.name, item.streamUrl) },
'''
new_c3l = '''if (selectedStream != null) {
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
                        onClick = { prevPlayer?.release(); onPlayFullScreen(item.name, item.streamUrl) },
'''
t = t.replace("ifBrank", "ifBlank")
t = t.replace(old_c3l, new_c3l)
open(p, "w").write(t)
