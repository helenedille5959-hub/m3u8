p = "app/src/main/java/com/jujuwan/player/MainActivity.kt"
t = open(p).read()
s = t.index("@Composable\nfun PlaylistManagerScreen(")
e = t.index("@Composable\nfun SlimInputBox(")
new_pm = '''@Composable
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
                    ) { Text("Xtream Codes API", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { isM3uMode = true },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isM3uMode) NeonPurple else BgDark)
                    ) { Text("Lien M3U (Auto)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
                        ) { Text("Coller", color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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

'''
open(p, "w").write(t[:s] + new_pm + t[e:])
