package com.example.poketrivia

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import coil3.compose.AsyncImage
import java.util.Locale

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
    val musicScreen = state.musicEnabled
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(musicScreen) { backgroundMusic.setEnabled(musicScreen) }
    DisposableEffect(lifecycleOwner, backgroundMusic) {
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
            Screen.HOME -> HomeScreen(vm)
            Screen.SETUP -> SetupScreen(state, vm)
            Screen.GAME -> GameScreen(state, vm, cryPlayer)
            Screen.RESULT -> ResultScreen(state, vm)
            Screen.LEADERBOARD -> LeaderboardScreen(vm)
            Screen.SETTINGS -> SettingsScreen(state, vm)
        }
        state.message?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Panel, RoundedCornerShape(14.dp)).padding(12.dp), color = Color.White) }
    }
}

@Composable private fun HomeScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(top = 54.dp)) {
            Text("WHO’S THAT", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("Pokémon?", color = Yellow, fontSize = 54.sp, lineHeight = 55.sp, fontWeight = FontWeight.Black)
            Text("Test your Pokédex knowledge across every region.", color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ vm.navigate(Screen.SETUP) }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("START A RUN", fontWeight = FontWeight.Black) }
            OutlinedButton({ vm.navigate(Screen.LEADERBOARD) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(8.dp)); Text("LEADERBOARD") }
            TextButton({ vm.navigate(Screen.SETTINGS) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("SETTINGS") }
        }
    }
}

@Composable private fun SetupScreen(s: UiState, vm: GameViewModel) = Page("Build your run", { vm.navigate(Screen.HOME) }) {
    val regions = listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea")
    val regionsLocked = s.settings.runMode == RunMode.ENDLESS
    Label("DIFFICULTY")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Difficulty.entries.forEach { ChoiceChip(it.name.lowercase().replaceFirstChar(Char::uppercase), s.difficulty == it) { vm.setDifficulty(it) } } }
    Spacer(Modifier.height(24.dp)); Label("GENERATIONS")
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
                    PokeballLives(s.livesRemaining)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(s.elapsedMs), color = Color.White, fontWeight = FontWeight.Black)
                Text("SCORE ${s.score}", color = Yellow, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        LinearProgressIndicator(progress = { s.index.toFloat() / target.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 14.dp))
        Text(if (s.difficulty == Difficulty.EASY) "Tap the Pokémon" else "Name that Pokémon", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        if (s.difficulty == Difficulty.EASY) EasyQuestion(q, vm, cryPlayer, s.criesEnabled) else HardQuestion(q, s.cluesShown, vm)
    }
}

@Composable private fun ColumnScope.EasyQuestion(q: Question, vm: GameViewModel, cryPlayer: CryPlayer, criesEnabled: Boolean) {
    Text("Which one is ${q.answer.name.pretty()}?", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(vertical = 14.dp))
    q.choices.chunked(2).forEach { row -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { pokemon -> Card(onClick = { if (criesEnabled) cryPlayer.play(pokemon.cries?.latest ?: pokemon.cries?.legacy); vm.answer(pokemon.name) }, modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) { AsyncImage(pokemon.sprites.other.artwork.image, pokemon.name, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) } } } }
}

@Composable private fun HardQuestion(q: Question, cluesShown: Int, vm: GameViewModel) {
    var answer by remember(q.answer.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        q.clues.take(cluesShown).forEachIndexed { i, clue -> Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = if (i == cluesShown - 1) Color(0xFF19304D) else Panel)) { Column(Modifier.padding(16.dp)) { Text(clue, color = Color.White, fontSize = 16.sp); if (i == 2) AsyncImage(q.answer.sprites.other.artwork.shiny, "Shiny Pokémon clue", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit) } } }
        if (cluesShown < q.clues.size) TextButton(vm::revealClue, Modifier.align(Alignment.End)) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text("Reveal next clue") }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("Pokémon name") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.answer(answer) }))
        Button({ vm.answer(answer) }, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text("SUBMIT", fontWeight = FontWeight.Black) }
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
        PokeballLives(s.livesRemaining, Modifier.padding(top = 10.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 28.dp), label = { Text("Trainer name") }, singleLine = true)
        Button({ vm.saveScore(name) }, Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp)) { Text("SAVE SCORE") }
        TextButton({ vm.navigate(Screen.HOME) }) { Text("Skip") }
    }
}

@Composable private fun LeaderboardScreen(vm: GameViewModel) {
    val scores by vm.leaderboard.collectAsStateWithLifecycle()
    Page("Leaderboard", { vm.navigate(Screen.HOME) }) {
        if (scores.isEmpty()) Text("No scores yet. Your first run could take the crown.", color = Muted)
        scores.forEachIndexed { i, item -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}", color = if (i < 3) Yellow else Muted, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(42.dp)); Column(Modifier.weight(1f)) { Text(item.player, color = Color.White, fontWeight = FontWeight.Bold); val mode = RunMode.entries.firstOrNull { it.name == item.runMode }; Text("${mode?.label ?: "Legacy"} • ${item.difficulty.lowercase().pretty()} • ${item.generation.split(',').size} gen", color = Muted, fontSize = 12.sp); PokeballLives(item.livesRemaining) }; Column(horizontalAlignment = Alignment.End) { Text("${item.score}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(if (item.durationMs > 0) formatDuration(item.durationMs) else "—", color = Muted, fontSize = 12.sp) } } }
    }
}

@Composable private fun SettingsScreen(s: UiState, vm: GameViewModel) {
    val context = LocalContext.current
    Page("Settings", { vm.navigate(Screen.HOME) }) {
        Label("SOUND")
        SettingSwitch("Background music", "Soft music on menus and during gameplay", s.musicEnabled, vm::setMusicEnabled)
        SettingSwitch("Pokémon cries", "Play a Pokémon’s cry when its picture is tapped", s.criesEnabled, vm::setCriesEnabled)
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color(0xFF28364A))
        Label("APP UPDATES")
        Text("Check GitHub for a newer release of PokéTrivia.", color = Muted, modifier = Modifier.padding(bottom = 16.dp))
        Button(vm::checkUpdates, Modifier.fillMaxWidth()) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("CHECK FOR UPDATES") }
        s.update?.let { release -> TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }, Modifier.fillMaxWidth()) { Text("Open release ${release.tag}") } }
        Spacer(Modifier.height(16.dp)); Text("Data and artwork provided by PokéAPI. Pokémon names and characters are trademarks of Nintendo, Game Freak, and Creatures.", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun Page(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
    IconButton(back) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp)); content()
}
@Composable private fun Label(text: String) = Text(text, color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 8.dp))
@Composable private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = FilterChip(selected, onClick, { Text(text) }, modifier, enabled = enabled)
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
@Composable private fun PokeballLives(count: Int, modifier: Modifier = Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(count.coerceAtLeast(0)) {
        Canvas(Modifier.size(18.dp)) {
            val outline = 1.5.dp.toPx()
            drawCircle(Color.White)
            clipRect(top = 0f, bottom = size.height / 2f) { drawCircle(Color(0xFFE84A4A)) }
            drawCircle(Color(0xFF111827), style = Stroke(outline))
            drawLine(Color(0xFF111827), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = outline)
            drawCircle(Color.White, radius = size.minDimension * .16f)
            drawCircle(Color(0xFF111827), radius = size.minDimension * .16f, style = Stroke(outline))
        }
    }
}
private fun String.pretty() = replace('-', ' ').replaceFirstChar(Char::uppercase)
private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    val seconds = (milliseconds / 1_000) % 60
    val millis = milliseconds % 1_000
    return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
}

private class CryPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String?) {
        if (url.isNullOrBlank()) return
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
    private val mediaPlayer = MediaPlayer.create(context, R.raw.soft_adventure_loop)?.apply {
        isLooping = true
        setVolume(0.12f, 0.12f)
    }
    private var enabled = false
    private var appInForeground = true

    fun setEnabled(value: Boolean) {
        enabled = value
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
        if (enabled && appInForeground) {
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
