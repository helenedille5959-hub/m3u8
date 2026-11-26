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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
}

data class PlaylistAccount(val name: String, val url: String, val user: String, val pass: String)
data class CategoryItem(val id: String, val name: String)
data class StreamItem(val id: String, val name: String, val streamUrl: String, val plot: String = "")

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
        var liveStatus by remember { mutableStateOf(0) } // 0=attente, 1=en cours, 2=OK
        var movieStatus by remember { mutableStateOf(0) }
        var seriesStatus by remember { mutableStateOf(0) }

        LaunchedEffect(activeAccount) {
            val acc = activeAccount
            if (acc != null && ServerCache.loadedForUrl != "${acc.url}_${acc.user}") {
                isPreloading = true
                liveStatus = 1; movieStatus = 0; seriesStatus = 0
                ServerCache.liveCats = listOf(CategoryItem("ALL", "Tout afficher")) + fetchCategories(acc, "get_live_categories")
                ServerCache.liveStreams = fetchStreams(acc, "LIVE", null)
                liveStatus = 2

                movieStatus = 1
                ServerCache.movieCats = listOf(CategoryItem("ALL", "Tout afficher")) + fetchCategories(acc, "get_vod_categories")
                ServerCache.movieStreams = fetchStreams(acc, "MOVIES", null)
                movieStatus = 2

                seriesStatus = 1
                ServerCache.seriesCats = listOf(CategoryItem("ALL", "Tout afficher")) + fetchCategories(acc, "get_series_categories")
                ServerCache.seriesStreams = fetchStreams(acc, "SERIES", null)
                seriesStatus = 2

                ServerCache.loadedForUrl = "${acc.url}_${acc.user}"
                delay(1000)
                isPreloading = false
            }
        }

        if (isPreloading && currentScreen != "PLAYLISTS") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardDark,
                    border = BorderStroke(2.dp, NeonPurple),
                    modifier = Modifier.padding(20.dp).fillMaxWidth(0.75f)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Le contenu de votre serveur charge pour la première fois, cela va prendre un certain moment...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        StepCheckRow("Chargement des chaînes en direct", "Chaînes chargées", liveStatus)
                        StepCheckRow("Chargement des films", "Films chargés", movieStatus)
                        StepCheckRow("Chargement des séries", "Séries chargées", seriesStatus)
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
fun HomeDashboard(onOpenCategory: (String) -> Unit, onOpenPlaylists: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            NeonMainCard("Télévision en direct", Icons.Default.Tv, Modifier.weight(1f)) { onOpenCategory("LIVE") }
            NeonMainCard("Films", Icons.Default.Movie, Modifier.weight(1f)) { onOpenCategory("MOVIES") }
            NeonMainCard("Séries", Icons.Default.VideoLibrary, Modifier.weight(1f)) { onOpenCategory("SERIES") }
        }
        Row(modifier = Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            NeonBottomButton("Changer de playlist de serveur", Icons.Default.Dns, Modifier.weight(1f), onOpenPlaylists)
            NeonBottomButton("Réglage de l'application", Icons.Default.Settings, Modifier.weight(1f), onOpenSettings)
        }
    }
}

@Composable
fun NeonMainCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable { onClick() }.drawBehind {
        val c = 18.dp.toPx(); val n = 8.dp.toPx()
        val outer = Path().apply {
            moveTo(c, 0f); lineTo(size.width * 0.35f, 0f); lineTo(size.width * 0.38f, n * 0.5f)
            lineTo(size.width * 0.62f, n * 0.5f); lineTo(size.width * 0.65f, 0f); lineTo(size.width - c, 0f)
            lineTo(size.width, c); lineTo(size.width, size.height - c); lineTo(size.width - c, size.height)
            lineTo(size.width * 0.65f, size.height); lineTo(size.width * 0.62f, size.height - n * 0.5f)
            lineTo(size.width * 0.38f, size.height - n * 0.5f); lineTo(size.width * 0.35f, size.height)
            lineTo(c, size.height); lineTo(0f, size.height - c); lineTo(0f, c); close()
        }
        drawPath(outer, Brush.radialGradient(listOf(Color(0xFF472186), Color(0xFF1F143B), Color(0xFF0E0B1A)), Offset(size.width / 2f, size.height / 2f), size.maxDimension * 0.65f))
        drawPath(outer, NeonPurple.copy(alpha = 0.28f), style = Stroke(6.dp.toPx()))
        drawPath(outer, Brush.verticalGradient(listOf(NeonPurpleGlow, Color(0xFF38BDF8), NeonPurple)), style = Stroke(2.dp.toPx()))
        val inset = 7.dp.toPx()
        val inner = Path().apply {
            moveTo(c + inset * 0.5f, inset); lineTo(size.width - c - inset * 0.5f, inset)
            lineTo(size.width - in, c + inset * 0.5f); lineTo(size.width - in, size.height - c - inset * 0.5f)
            lineTo(size.width - c - inset * 0.5f, size.height - in); lineTo(c + inset * 0.5f, size.height - in)
            lineTo(inset, size.height - c - inset * 0.5f); lineTo(inset, c + inset * 0.5f); close()
        }
        drawPath(inner, NeonPurpleGlow.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = NeonPurpleGlow, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun NeonBottomButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable { onClick() }.drawBehind {
        val b = 12.dp.toPx()
        val hud = Path().apply { moveTo(b, 0f); lineTo(size.width - b, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width - b, size.height); lineTo(b, size.height); lineTo(0f, size.height / 2f); close() }
        drawPath(hud, Brush.horizontalGradient(listOf(Color(0xFF14E026), Color(0xFF2A1A4B), Color(0xFF14E026))))
        drawPath(hud, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), NeonPurpleGlow, Color(0xFF38BDF8))), style = Stroke(1.6.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NeonPurpleGlow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Colonne gauche : 4 cases fines + boutons Retour et Valider bien visibles
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
                Text("Xtream Codes API", color = NeonPurpleGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                SlimInputBox(name, { name = it }, "Nom de la playlist (nommé comme vous le souhaitez)")
                SlimInputBox(url, { url = it }, "URL du serveur (http://...)")
                SlimInputBox(user, { user = it }, "Nom d'utilisateur")
                SlimInputBox(pass, { pass = it }, "Mot de passe", isPassword = true)

                Row(modifier = Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

        // Colonne droite : Mes listes enregistrées
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
                Text(placeholder, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
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
        categories = when (mode) {
            "LIVE" -> ServerCache.liveCats
            "MOVIES" -> ServerCache.movieCats
            else -> ServerCache.seriesCats
        }
        streams = when (mode) {
            "LIVE" -> ServerCache.liveStreams
            "MOVIES" -> ServerCache.movieStreams
            else -> ServerCache.seriesStreams
        }
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
                Spacer(Modifier.height(6.dp))
                SlimInputBox(searchQuery, { searchQuery = it }, "Rechercher...")
                Spacer(Modifier.height(6.dp))
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
                                Text(item.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
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
                        Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            factory = { PlayerView(it).apply { player = exoPlayer; useController = (url.endsWith(".m3u8") == false) } },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.8f))) {
                Text("← Quitter", color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        (0 until arr.length()).map {
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

@Composable
fun CompactField(value: String, onChange: (String) -> Unit, hint: String, isPass: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, fontSize = 11.sp, maxLines = 1) },
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
