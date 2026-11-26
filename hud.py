p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()
imp = "import androidx.compose.ui.draw.drawBehind\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Path\nimport androidx.compose.ui.graphics.drawscope.Stroke\n"
t = t.replace("import androidx.compose.ui.graphics.Brush", imp + "import androidx.compose.ui.graphics.Brush", 1)
old_h = 'Text("Juju Wan", color = NeonPurpleGlow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)\n                    Spacer(Modifier.width(12.dp))\n                    val subtitle = when (currentScreen) {\n                        "HOME" -> "Le lecteur premium"\n                        "PLAYLISTS" -> "Ajouter une playlist"\n                        "LIVE" -> "Télévision en direct"\n                        "MOVIES" -> "Films"\n                        "SERIES" -> "Séries"\n                        else -> ""\n                    }\n                    Text(subtitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)'
new_h = '''Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.drawBehind {
    val w = size.width; val h = size.height
    val wing = Path().apply { moveTo(w*0.08f, h*0.24f); lineTo(w*0.92f, h*0.24f); lineTo(w, h*0.5f); lineTo(w*0.92f, h*0.76f); lineTo(w*0.08f, h*0.76f); lineTo(0f, h*0.5f); close() }
    drawPath(wing, Brush.horizontalGradient(listOf(Color(0xFF1A1033), Color(0xFF2D1959), Color(0xFF1A1033))))
    drawPath(wing, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), NeonPurpleGlow, Color(0xFF38BDF8))), style = Stroke(1.5.dp.toPx()))
    val sh = Path().apply { moveTo(w*0.5f, 0f); lineTo(w*0.66f, h*0.18f); lineTo(w*0.66f, h*0.72f); lineTo(w*0.5f, h); lineTo(w*0.34f, h*0.72f); lineTo(w*0.34f, h*0.18f); close() }
    drawPath(sh, Brush.radialGradient(listOf(Color(0xFF53249E), Color(0xFF180F30))))
    drawPath(sh, Brush.verticalGradient(listOf(Color(0xFF38BDF8), NeonPurpleGlow)), style = Stroke(2.dp.toPx()))
}.padding(horizontal = 14.dp, vertical = 6.dp)) {
    Text("JUJU", color = Color(0xFFE0B8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.width(10.dp))
    Text("JW", color = Color(0xFF38BDF8), fontSize = 17.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.width(10.dp))
    Text("WAN", color = Color(0xFFE0B8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
}'''
t = t.replace(old_h, new_h)
s = t.index("@Composable\nfun HomeDashboard(")
e = t.index("@Composable", t.index("fun PlaylistManagerScreen(") - 30)
t = t[:s] + '''@Composable
fun HomeDashboard(onOpenCategory: (String) -> Unit, onOpenPlaylists: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().drawBehind {
        val w = size.width; val h = size.height; val c = 14.dp.toPx()
        val fr = Path().apply { moveTo(c, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h - c); lineTo(w - c, h); lineTo(c, h); lineTo(0f, h - c); lineTo(0f, c); close() }
        drawPath(fr, Color(0xFF38BDF8).copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    }.padding(horizontal = 54.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            NeonMainCard("Télévision\\nen direct", Icons.Default.Tv, Modifier.weight(1f)) { onOpenCategory("LIVE") }
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
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
''' + t[e:]
open(p, "w").write(t)
