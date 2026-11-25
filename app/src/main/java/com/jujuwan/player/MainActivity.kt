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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class PlaylistAccount(val name: String, val url: String, val user: String, val pass: String)
data class CategoryItem(val id: String, val name: String)
data class StreamItem(val id: String, val name: String, val streamUrl: String, val plot: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            .statusBarsPadding().navigationBarsPadding().padding(10.dp)
    ) {
        if (currentScreen != "PLAYER") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Juju Wan", color = NeonPurpleGlow, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(12.dp))
                    val subtitle = when (currentScreen) {
                        "HOME" -> "Le lecteur premium"
                        "PLAYLISTS" -> "Ajouter une playlist"
                        "LIVE" -> "Télévision en direct"
                        "MOVIES" -> "Films"
                        "SERIES" -> "Séries"
                        else -> ""
                    }
                    Text(subtitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(currentDateTime, color = NeonPurpleGlow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        when (currentScreen) {
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
                    onBack = { currentScreen = "HOME" },
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
fun HomeDashboard(
    onOpenCategory: (String) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeonMainCard("Télévision\nen direct", Icons.Default.Tv, Modifier.weight(1f)) { onOpenCategory("LIVE") }
            NeonMainCard("Films", Icons.Default.Movie, Modifier.weight(1f)) { onOpenCategory("MOVIES") }
            NeonMainCard("Séries", Icons.Default.VideoLibrary, Modifier.weight(1f)) { onOpenCategory("SERIES") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeonBottomButton("Changer de playlist de serveur", Icons.Default.Dns, Modifier.weight(1f), onOpenPlaylists)
            NeonBottomButton("Réglage de l'application", Icons.Default.Settings, Modifier.weight(1f), onOpenSettings)
        }
    }
}

@Composable
fun NeonMainCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = CardDark,
        border = BorderStroke(2.dp, Brush.verticalGradient(listOf(NeonPurpleGlow, NeonPurple)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = NeonPurpleGlow, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun NeonBottomButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = CardDark,
        border = BorderStroke(1.5.dp, NeonPurple)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = NeonPurpleGlow, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PlaylistManagerScreen(
    prefs: android.content.SharedPreferences,
    onBack: () -> Unit,
    onSelectAccount: (PlaylistAccount) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var savedList by remember { mutableStateOf(loadSavedAccounts(prefs)) }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(2.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Xtream Codes API", color = NeonPurpleGlow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nom de la playlist (nommé comme vous le souhaitez)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL du serveur (http://...)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Nom d'utilisateur") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Mot de passe") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, NeonPurple)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Retour", color = Color.White)
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Se connecter", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(2.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(10.dp)) {
                Text("Mes listes enregistrées", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedList) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgDark,
                            border = BorderStroke(1.dp, NeonPurple),
                            modifier = Modifier.fillMaxWidth().clickable { onSelectAccount(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = {
                                    val updated = savedList.filter { it != item }
                                    saveAccounts(prefs, updated)
                                    savedList = updated
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = NeonPurpleGlow)
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
fun MediaBrowserScreen(
    mode: String,
    account: PlaylistAccount,
    onBack: () -> Unit,
    onPlayFullScreen: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedStream by remember { mutableStateOf<StreamItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(mode) {
        isLoading = true
        val actionCat = when (mode) {
            "LIVE" -> "get_live_categories"
            "MOVIES" -> "get_vod_categories"
            else -> "get_series_categories"
        }
        categories = listOf(CategoryItem("ALL", "Tout afficher")) + fetchCategories(account, actionCat)
        streams = fetchStreams(account, mode, null)
        isLoading = false
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Catégories", color = NeonPurpleGlow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher...", fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories.filter { it.name.contains(searchQuery, ignoreCase = true) }) { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategory == cat.id) NeonPurple else BgDark,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedCategory = cat.id
                                scope.launch {
                                    isLoading = true
                                    streams = fetchStreams(account, mode, if (cat.id == "ALL") null else cat.id)
                                    isLoading = false
                                }
                            }
                        ) {
                            Text(cat.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, NeonPurple)) {
                    Text("← Retour", color = Color.White)
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    when (mode) { "LIVE" -> "Chaînes"; "MOVIES" -> "Films"; else -> "Séries" },
                    color = NeonPurpleGlow, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonPurpleGlow)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(streams) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedStream == item) NeonPurple.copy(alpha = 0.4f) else BgDark,
                                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().clickable { selectedStream = item }
                            ) {
                                Text(item.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                if (selectedStream != null) {
                    val item = selectedStream!!
                    Column {
                        Text(item.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BadgeChip("4K / HD")
                            BadgeChip("HEVC")
                            BadgeChip("DTS / EAC3")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = item.plot.ifBlank { "Flux prêt pour la lecture haute définition sur Juju Wan." },
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { onPlayFullScreen(item.name, item.streamUrl) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Lancer en plein écran", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sélectionnez un élément à gauche", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = BgDark, border = BorderStroke(1.dp, NeonPurpleGlow)) {
        Text(label, color = NeonPurpleGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun FullScreenVideoPlayer(title: String, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
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
            factory = { PlayerView(it).apply { player = exoPlayer } },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(10.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.8f))) {
                Text("← Quitter", color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

suspend fun fetchCategories(acc: PlaylistAccount, action: String): List<CategoryItem> = withContext(Dispatchers.IO) {
    try {
        val apiUrl = "${acc.url}/player_api.php?username=${acc.user}&password=${acc.pass}&action=$action"
        val res = OkHttpClient().newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "[]"
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
        val res = OkHttpClient().newCall(Request.Builder().url(apiUrl).build()).execute().body?.string() ?: "[]"
        val arr = JSONArray(res)
        (0 until minOf(arr.length(), 150)).map {
            val obj = arr.getJSONObject(it)
            val id = obj.optString("stream_id", obj.optString("series_id"))
            val ext = obj.optString("container_extension", "mp4")
            val streamLink = when (mode) {
                "LIVE" -> "${acc.url}/live/${acc.user}/${acc.pass}/$id.m3u8"
                "MOVIES" -> "${acc.url}/movie/${acc.user}/${acc.pass}/$id.$ext"
                else -> "${acc.url}/series/${acc.user}/${acc.pass}/$id.$ext"
            }
            StreamItem(id, obj.optString("name"), streamLink, obj.optString("plot", ""))
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
