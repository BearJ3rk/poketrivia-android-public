package com.example.poketrivia

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import java.io.File
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF09111F)
private val Panel = Color(0xFF121E31)
private val Yellow = Color(0xFFFFCB3C)
private val Blue = Color(0xFF3B82F6)
private val Muted = Color(0xFF9BA9BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { TriviaTheme { PokeTriviaApp() } }
    }
}

@Composable private fun TriviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, secondary = Blue, background = Navy, surface = Panel, onPrimary = Navy), content = content)
}

@Composable fun PokeTriviaApp(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cryPlayer = rememberCryPlayer()
    val backgroundMusic = rememberBackgroundMusicPlayer()
    val musicVolume = state.musicVolume
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(musicVolume) { backgroundMusic.setVolume(musicVolume) }
    DisposableEffect(lifecycleOwner, backgroundMusic) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            backgroundMusic.onAppForeground()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> backgroundMusic.onAppForeground()
                Lifecycle.Event.ON_STOP -> backgroundMusic.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color(0xFF0D1D35)))).safeDrawingPadding()) {
        when (state.screen) {
            Screen.HOME -> HomeScreen(state, vm)
            Screen.SETUP -> SetupScreen(state, vm)
            Screen.GAME -> GameScreen(state, vm, cryPlayer)
            Screen.RESULT -> ResultScreen(state, vm)
            Screen.LEADERBOARD -> LeaderboardScreen(vm)
            Screen.SETTINGS -> SettingsScreen(state, vm)
        }
        state.message?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Panel, RoundedCornerShape(14.dp)).padding(12.dp), color = Color.White) }
    }
}

@Composable private fun HomeScreen(s: UiState, vm: GameViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (s.spotlight == null) vm.refreshSpotlight()
        while (true) {
            delay(15_000)
            vm.refreshSpotlight()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Column(Modifier.padding(top = 28.dp)) {
            Text("WHO’S THAT", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("Pokémon?", color = Yellow, fontSize = 54.sp, lineHeight = 55.sp, fontWeight = FontWeight.Black)
            Text("Test your Pokédex knowledge across every region.", color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(22.dp))
        s.spotlight?.let { spotlight ->
            Card(
                onClick = {
                    val slug = serebiiSlug(spotlight.pokemon.name)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.serebii.net/pokemon/$slug/")))
                },
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        if (s.useOfficialArtwork) {
                            spotlight.pokemon.sprites.other.artwork.image ?: spotlight.pokemon.sprites.image
                        } else {
                            spotlight.pokemon.sprites.image ?: spotlight.pokemon.sprites.other.artwork.image
                        },
                        spotlight.pokemon.name,
                        Modifier.fillMaxWidth().height(190.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text("POKÉMON SPOTLIGHT", color = Yellow, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(spotlight.pokemon.name.pretty(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(spotlight.fact, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
                    Text("Tap to view on Serebii.net", color = Blue, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        } ?: Box(Modifier.fillMaxWidth().height(282.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(30.dp))
        }
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ vm.chooseGameMode(GameMode.WHO_THAT_POKEMON) }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("WHO’S THAT POKÉMON", fontWeight = FontWeight.Black) }
            Button({ vm.chooseGameMode(GameMode.NAME_THAT_POKEMON) }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7657D5))) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("NAME THAT POKÉMON", fontWeight = FontWeight.Black) }
            Button({ vm.chooseGameMode(GameMode.TYPE_MATCH) }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Icon(Icons.Default.Category, null); Spacer(Modifier.width(8.dp)); Text("TYPE QUIZ", fontWeight = FontWeight.Black) }
            OutlinedButton({ vm.navigate(Screen.LEADERBOARD) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(8.dp)); Text("LEADERBOARD") }
            TextButton({ vm.navigate(Screen.SETTINGS) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("SETTINGS") }
        }
    }
}

@Composable private fun SetupScreen(s: UiState, vm: GameViewModel) = Page(s.gameMode.label, { vm.navigate(Screen.HOME) }) {
    val regions = listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea")
    val regionsLocked = s.settings.runMode == RunMode.ENDLESS
    Label("GENERATIONS")
    Text(if (regionsLocked) "All regions are locked for ${s.settings.runMode.label} runs." else "Choose one, several, or all.", color = Muted, modifier = Modifier.padding(bottom = 10.dp))
    (1..9).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { gen ->
                Box(Modifier.weight(1f)) {
                    ChoiceChip("Gen $gen · ${regions[gen - 1]}", gen in s.generations, Modifier.fillMaxWidth(), enabled = !regionsLocked) { vm.toggleGeneration(gen) }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(16.dp))
    Label("POKÉMON PER RUN")
    RunMode.entries.filter { it.selectable }.forEach { mode ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setRunMode(mode) }.padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(s.settings.runMode == mode, { vm.setRunMode(mode) })
            Text(mode.label, color = Color.White, modifier = Modifier.weight(1f))
            PokeballLives(mode.lives)
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(vm::start, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Text("LET’S GO", fontWeight = FontWeight.Black) }
}

@Composable private fun GameScreen(s: UiState, vm: GameViewModel, cryPlayer: CryPlayer) {
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            icon = { Icon(Icons.Default.ExitToApp, null) },
            title = { Text("Leave this run?") },
            text = { Text("Your current score and time will not be saved.") },
            confirmButton = { TextButton({ confirmExit = false; cryPlayer.release(); vm.cancelGame() }) { Text("Leave run") } },
            dismissButton = { TextButton({ confirmExit = false }) { Text("Keep playing") } }
        )
    }
    if (s.loading || s.question == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IconButton({ confirmExit = true }, Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.ArrowBack, "Cancel run", tint = Color.White) }
            CircularProgressIndicator()
        }
        return
    }
    val q = s.question
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        val target = s.settings.runMode.questionLimit?.coerceAtMost(s.availablePokemon) ?: s.availablePokemon
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ confirmExit = true }) { Icon(Icons.Default.ArrowBack, "Cancel run", tint = Color.White) }
                Column {
                    Text("${s.settings.runMode.label.uppercase()} • ${s.index + 1} / $target", color = Muted, fontWeight = FontWeight.Bold)
                    PokeballLives(s.livesRemaining, totalLives = s.settings.runMode.lives)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(s.elapsedMs), color = Color.White, fontWeight = FontWeight.Black)
                Text("SCORE ${s.score}", color = Yellow, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        LinearProgressIndicator(progress = { s.index.toFloat() / target.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 14.dp))
        when (s.gameMode) {
            GameMode.WHO_THAT_POKEMON -> {
                Text("Tap the Pokémon", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                EasyQuestion(q, vm, cryPlayer, s.criesVolume)
            }
            GameMode.NAME_THAT_POKEMON -> {
                Text("Name that Pokémon", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                NameQuestion(q, s.cluesShown, s.availableNames, vm)
            }
            GameMode.TYPE_MATCH -> {
                Text("Identify its type${if (q.answer.types.size > 1) "s" else ""}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                TypeMatchQuestion(q, s, vm)
            }
        }
    }
}

@Composable private fun ColumnScope.EasyQuestion(q: Question, vm: GameViewModel, cryPlayer: CryPlayer, criesVolume: Float) {
    Text("Which one is ${q.answer.name.pretty()}?", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(vertical = 14.dp))
    q.choices.chunked(2).forEach { row -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { pokemon -> Card(onClick = { cryPlayer.play(pokemon.cries?.latest ?: pokemon.cries?.legacy, criesVolume); vm.answer(pokemon.name) }, modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) { AsyncImage(pokemon.sprites.other.artwork.image, pokemon.name, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) } } } }
}

@Composable private fun NameQuestion(q: Question, cluesShown: Int, availableNames: List<String>, vm: GameViewModel) {
    var answer by remember(q.answer.id) { mutableStateOf("") }
    val normalizedQuery = answer.trim().lowercase()
    val suggestions = if (normalizedQuery.isBlank()) emptyList() else availableNames
        .filter { it.pretty().lowercase().startsWith(normalizedQuery) }
        .take(5)
    Column(Modifier.fillMaxSize()) {
        q.clues.take(cluesShown).forEachIndexed { i, clue -> Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = if (i == cluesShown - 1) Color(0xFF19304D) else Panel)) { Column(Modifier.padding(16.dp)) { Text(clue, color = Color.White, fontSize = 16.sp); if (i == 2) AsyncImage(q.answer.sprites.other.artwork.shiny, "Shiny Pokémon clue", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit) } } }
        if (cluesShown < q.clues.size) TextButton(vm::revealClue, Modifier.align(Alignment.End)) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text("Reveal next clue") }
        Spacer(Modifier.weight(1f))
        if (suggestions.isNotEmpty() && suggestions.none { it.pretty().equals(answer, ignoreCase = true) }) {
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                suggestions.forEach { name ->
                    TextButton({ answer = name.pretty() }, Modifier.fillMaxWidth()) {
                        Text(name.pretty(), Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
        OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("Pokémon name") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.answer(answer) }))
        Button({ vm.answer(answer) }, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text("SUBMIT", fontWeight = FontWeight.Black) }
    }
}

private val PokemonTypes = listOf(
    "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
    "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
)

@Composable private fun ColumnScope.TypeMatchQuestion(q: Question, s: UiState, vm: GameViewModel) {
    AsyncImage(
        q.answer.sprites.image ?: q.answer.sprites.other.artwork.image,
        "Pokémon to identify by type",
        Modifier.fillMaxWidth().height(210.dp).padding(vertical = 8.dp),
        contentScale = ContentScale.Fit
    )
    Text("Select every applicable type, then check your answer.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    PokemonTypes.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            row.forEach { type ->
                val eliminated = type in s.eliminatedTypes
                FilterChip(
                    selected = type in s.selectedTypes,
                    onClick = { vm.toggleType(type) },
                    label = {
                        Text(
                            type.pretty(),
                            textDecoration = if (eliminated) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            fontSize = 12.sp
                        )
                    },
                    enabled = !eliminated,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Button(vm::submitTypes, Modifier.fillMaxWidth().height(56.dp), enabled = s.selectedTypes.isNotEmpty()) {
        Text("NEXT", fontWeight = FontWeight.Black)
    }
}

@Composable private fun ResultScreen(s: UiState, vm: GameViewModel) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.EmojiEvents, null, tint = Yellow, modifier = Modifier.size(86.dp))
        Text("Run complete!", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("${s.score} correct", color = Yellow, fontSize = 42.sp, fontWeight = FontWeight.Black)
        Text(formatDuration(s.elapsedMs), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${s.settings.runMode.label} run", color = Muted, modifier = Modifier.padding(top = 6.dp))
        Text(s.gameMode.label, color = Muted, modifier = Modifier.padding(top = 2.dp))
        PokeballLives(s.livesRemaining, Modifier.padding(top = 10.dp), s.settings.runMode.lives)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 28.dp), label = { Text("Trainer name") }, singleLine = true)
        Button({ vm.saveScore(name) }, Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp)) { Text("SAVE SCORE") }
        TextButton({ vm.navigate(Screen.HOME) }) { Text("Skip") }
    }
}

@Composable private fun LeaderboardScreen(vm: GameViewModel) {
    val scores by vm.leaderboard.collectAsStateWithLifecycle()
    Page("Leaderboard", { vm.navigate(Screen.HOME) }) {
        if (scores.isEmpty()) Text("No scores yet. Your first run could take the crown.", color = Muted)
        GameMode.entries.forEach { gameMode ->
            Text(gameMode.label, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
            RunMode.entries.filter { it.selectable }.forEach { mode ->
                val section = scores.filter { it.runMode == mode.name && it.gameMode == gameMode.name }
                    .sortedWith(compareByDescending<ScoreEntity> { it.score }.thenBy { if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE })
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(mode.label.uppercase(), color = Yellow, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    PokeballLives(mode.lives)
                }
                HorizontalDivider(Modifier.padding(top = 7.dp, bottom = 5.dp), color = Color(0xFF28364A))
                if (section.isEmpty()) {
                    Text("No scores yet", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    section.forEachIndexed { i, item -> LeaderboardRow(i, item, mode.lives) }
                }
            }
        }
    }
}

@Composable private fun LeaderboardRow(index: Int, item: ScoreEntity, totalLives: Int) = Row(
    Modifier.fillMaxWidth().padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text("${index + 1}", color = if (index < 3) Yellow else Muted, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(42.dp))
    Column(Modifier.weight(1f)) {
        Text(item.player, color = Color.White, fontWeight = FontWeight.Bold)
        Text("${item.difficulty.lowercase().pretty()} • ${item.generation.split(',').size} gen", color = Muted, fontSize = 12.sp)
        PokeballLives(item.livesRemaining, totalLives = totalLives)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("${item.score}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(if (item.durationMs > 0) formatDuration(item.durationMs) else "—", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun SettingsScreen(s: UiState, vm: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadedUpdate by remember { mutableStateOf<File?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        downloadedUpdate?.let { apk ->
            if (context.packageManager.canRequestPackageInstalls()) installApk(context, apk)
            else updateStatus = "Permission is still required to install updates."
        }
    }

    fun beginInstall(apk: File) {
        downloadedUpdate = apk
        if (context.packageManager.canRequestPackageInstalls()) {
            installApk(context, apk)
        } else {
            updateStatus = "Allow PokéTrivia to install updates, then return to the app."
            unknownSourcesLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    Page("Settings", { vm.navigate(Screen.HOME) }) {
        Label("SOUND")
        SettingVolume("Background music", "Music on every app screen", s.musicVolume, vm::setMusicVolume)
        SettingVolume("Pokémon cries", "Cry played when a Pokémon picture is tapped", s.criesVolume, vm::setCriesVolume)
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color(0xFF28364A))
        Label("MAIN MENU SPOTLIGHT")
        SettingSwitch(
            "High-resolution artwork",
            if (s.useOfficialArtwork) "Using official artwork" else "Using classic game sprites",
            s.useOfficialArtwork,
            vm::setUseOfficialArtwork
        )
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color(0xFF28364A))
        Label("APP UPDATES")
        Text("Check GitHub for a newer release of PokéTrivia.", color = Muted, modifier = Modifier.padding(bottom = 16.dp))
        Button(vm::checkUpdates, Modifier.fillMaxWidth()) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("CHECK FOR UPDATES") }
        s.update?.takeIf { it.tag.removePrefix("v") != BuildConfig.VERSION_NAME }?.let { release ->
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val apkUrl = release.apkUrl
                    if (apkUrl == null) {
                        updateStatus = "This release does not contain an APK."
                    } else {
                        downloadingUpdate = true
                        updateStatus = "Downloading ${release.tag}…"
                        scope.launch {
                            runCatching { downloadUpdateApk(context, apkUrl) }
                                .onSuccess { apk ->
                                    downloadingUpdate = false
                                    updateStatus = "Download complete. Opening Android installer…"
                                    beginInstall(apk)
                                }
                                .onFailure {
                                    downloadingUpdate = false
                                    updateStatus = "Update download failed. Check your connection and try again."
                                }
                        }
                    }
                },
                enabled = !downloadingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (downloadingUpdate) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (downloadingUpdate) "DOWNLOADING…" else "DOWNLOAD & INSTALL ${release.tag}")
            }
        }
        updateStatus?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(16.dp)); Text("Data and artwork provided by PokéAPI. Pokémon names and characters are trademarks of Nintendo, Game Freak, and Creatures.", color = Muted, fontSize = 12.sp)
    }
}

private suspend fun downloadUpdateApk(context: android.content.Context, url: String): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val destination = File(directory, "PokeTrivia-update.apk")
    val connection = URL(url).openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 60_000
    }
    connection.getInputStream().use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    destination
}

private fun installApk(context: android.content.Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

@Composable private fun Page(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
    IconButton(back) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp)); content()
}
@Composable private fun Label(text: String) = Text(text, color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 8.dp))
@Composable private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = FilterChip(selected, onClick, { Text(text) }, modifier, enabled = enabled)
@Composable private fun SettingVolume(title: String, description: String, volume: Float, onVolumeChange: (Float) -> Unit) = Column(
    Modifier.fillMaxWidth().padding(vertical = 10.dp)
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 12.sp)
        }
        Text("${(volume * 100).toInt()}%", color = if (volume > 0f) Yellow else Muted, fontWeight = FontWeight.Bold)
    }
    Slider(value = volume, onValueChange = onVolumeChange, valueRange = 0f..1f, steps = 19)
}
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Text(description, color = Muted, fontSize = 12.sp)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}
@Composable private fun PokeballLives(count: Int, modifier: Modifier = Modifier, totalLives: Int = count) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(count.coerceAtLeast(0)) {
        Canvas(Modifier.size(18.dp)) {
            val outline = 1.5.dp.toPx()
            val topColor = when (totalLives) {
                5 -> Color(0xFF2E9DEB) // Quick Ball
                4 -> Color(0xFFE84A4A) // Poké Ball
                3 -> Color(0xFF3279D8) // Great Ball
                2 -> Color(0xFF202633) // Ultra Ball
                else -> Color(0xFF8E5AC7) // Master Ball
            }
            drawCircle(Color.White)
            clipRect(top = 0f, bottom = size.height / 2f) {
                drawCircle(topColor)
                when (totalLives) {
                    5 -> {
                        drawLine(Color(0xFFFFD84A), androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .12f), androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .46f), strokeWidth = outline * 1.8f)
                        drawLine(Color(0xFFFFD84A), androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .12f), androidx.compose.ui.geometry.Offset(size.width * .58f, size.height * .46f), strokeWidth = outline * 1.8f)
                    }
                    3 -> {
                        drawLine(Color(0xFFE84A4A), androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .12f), androidx.compose.ui.geometry.Offset(size.width * .38f, size.height * .47f), strokeWidth = outline * 1.7f)
                        drawLine(Color(0xFFE84A4A), androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .12f), androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .47f), strokeWidth = outline * 1.7f)
                    }
                    2 -> {
                        drawLine(Color(0xFFFFD43B), androidx.compose.ui.geometry.Offset(size.width * .25f, 0f), androidx.compose.ui.geometry.Offset(size.width * .4f, size.height * .46f), strokeWidth = outline * 2f)
                        drawLine(Color(0xFFFFD43B), androidx.compose.ui.geometry.Offset(size.width * .75f, 0f), androidx.compose.ui.geometry.Offset(size.width * .6f, size.height * .46f), strokeWidth = outline * 2f)
                    }
                    1 -> {
                        drawCircle(Color(0xFFF09AC2), radius = size.minDimension * .10f, center = androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .25f))
                        drawCircle(Color(0xFFF09AC2), radius = size.minDimension * .10f, center = androidx.compose.ui.geometry.Offset(size.width * .75f, size.height * .25f))
                    }
                }
            }
            drawCircle(Color(0xFF111827), style = Stroke(outline))
            drawLine(Color(0xFF111827), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = outline)
            drawCircle(Color.White, radius = size.minDimension * .16f)
            drawCircle(Color(0xFF111827), radius = size.minDimension * .16f, style = Stroke(outline))
        }
    }
}
private fun String.pretty() = replace('-', ' ').replaceFirstChar(Char::uppercase)
private fun serebiiSlug(name: String) = when (name) {
    "nidoran-f" -> "nidoranf"
    "nidoran-m" -> "nidoranm"
    "mr-mime" -> "mr.mime"
    "mime-jr" -> "mimejr"
    "type-null" -> "typenull"
    "tapu-koko", "tapu-lele", "tapu-bulu", "tapu-fini" -> name.replace("-", "")
    else -> name
}
private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    val seconds = (milliseconds / 1_000) % 60
    val millis = milliseconds % 1_000
    return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
}

private class CryPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String?, volume: Float) {
        if (url.isNullOrBlank() || volume <= 0f) return
        release()
        val next = MediaPlayer()
        runCatching {
            next.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(url)
                setVolume(volume, volume)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { completed ->
                    completed.release()
                    if (mediaPlayer === completed) mediaPlayer = null
                }
                setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (mediaPlayer === failed) mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = next
        }.onFailure { next.release() }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable private fun rememberCryPlayer(): CryPlayer {
    val player = remember { CryPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private class BackgroundMusicPlayer(context: android.content.Context) {
    private val mediaPlayer = MediaPlayer.create(context, R.raw.pokemon_battle_music)?.apply {
        isLooping = true
        setVolume(0.75f, 0.75f)
    }
    private var volume = 0.75f
    private var appInForeground = false

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(volume, volume)
        updatePlayback()
    }

    fun onAppForeground() {
        appInForeground = true
        updatePlayback()
    }

    fun onAppBackground() {
        appInForeground = false
        mediaPlayer?.pause()
    }

    private fun updatePlayback() {
        if (volume > 0f && appInForeground) {
            if (mediaPlayer?.isPlaying == false) mediaPlayer.start()
        } else {
            mediaPlayer?.pause()
        }
    }

    fun release() {
        mediaPlayer?.release()
    }
}

@Composable private fun rememberBackgroundMusicPlayer(): BackgroundMusicPlayer {
    val context = LocalContext.current
    val player = remember(context) { BackgroundMusicPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
