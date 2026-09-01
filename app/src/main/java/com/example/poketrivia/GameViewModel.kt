package com.example.poketrivia

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

enum class Difficulty { EASY, HARD }
enum class GameMode(val label: String) {
    WHO_THAT_POKEMON("Who’s That Pokémon"),
    NAME_THAT_POKEMON("Name That Pokémon"),
    TYPE_MATCH("Type Quiz")
}
enum class Screen { HOME, SETUP, GAME, RESULT, LEADERBOARD, SETTINGS }
enum class RunMode(val label: String, val questionLimit: Int?, val lives: Int, val selectable: Boolean = true) {
    TEN("10", 10, 1, selectable = false),
    TWENTY_FIVE("25", 25, 5),
    FIFTY("50", 50, 4),
    ONE_HUNDRED("100", 100, 3),
    ALL("Entire Gen", null, 2),
    ENDLESS("Endless - Every Pokemon Ever", null, 1)
}
data class GameSettings(val runMode: RunMode = RunMode.TWENTY_FIVE)
data class Question(val answer: PokemonResponse, val species: SpeciesResponse?, val choices: List<PokemonResponse>, val clues: List<String>)
data class PokemonSpotlight(val pokemon: PokemonResponse, val fact: String)
data class UiState(
    val screen: Screen = Screen.HOME,
    val settings: GameSettings = GameSettings(),
    val gameMode: GameMode = GameMode.WHO_THAT_POKEMON,
    val generations: Set<Int> = setOf(1),
    val difficulty: Difficulty = Difficulty.EASY,
    val loading: Boolean = false,
    val question: Question? = null,
    val index: Int = 0,
    val score: Int = 0,
    val availablePokemon: Int = 0,
    val availableNames: List<String> = emptyList(),
    val livesRemaining: Int = RunMode.TWENTY_FIVE.lives,
    val elapsedMs: Long = 0,
    val cluesShown: Int = 1,
    val selectedTypes: Set<String> = emptySet(),
    val eliminatedTypes: Set<String> = emptySet(),
    val eliminatedPokemon: Set<String> = emptySet(),
    val wrongPokemon: List<String> = emptyList(),
    val wrongPokemonTypes: Map<String, List<String>> = emptyMap(),
    val mistakeFlashId: Int = 0,
    val musicVolume: Float = 0.75f,
    val criesVolume: Float = 0.10f,
    val useOfficialArtwork: Boolean = true,
    val darkMode: Boolean = true,
    val spotlight: PokemonSpotlight? = null,
    val message: String? = null,
    val update: ReleaseResponse? = null
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TriviaApplication).repository
    private val preferences = app.getSharedPreferences("poke_trivia_settings", 0)
    private val _state = MutableStateFlow(
        UiState(
            musicVolume = preferences.getFloat(
                "music_volume",
                if (preferences.getBoolean("music_enabled", true)) 0.75f else 0f
            ),
            criesVolume = preferences.getFloat(
                "cries_volume",
                if (preferences.getBoolean("cries_enabled", true)) 0.10f else 0f
            ),
            useOfficialArtwork = preferences.getBoolean("use_official_artwork", true),
            darkMode = preferences.getBoolean("dark_mode", true)
        )
    )
    val state = _state.asStateFlow()
    val leaderboard = repo.scores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var sessionPool: List<String> = emptyList()
    private val remainingPokemon = ArrayDeque<String>()
    private var accumulatedMs = 0L
    private var questionStartedAt = 0L
    private var timerJob: Job? = null

    fun navigate(screen: Screen) { _state.update { it.copy(screen = screen, message = null) } }
    fun chooseGameMode(value: GameMode) {
        _state.update { it.copy(gameMode = value, screen = Screen.SETUP, message = null) }
    }
    fun refreshSpotlight() = viewModelScope.launch {
        runCatching {
            val currentId = _state.value.spotlight?.pokemon?.id
            val id = generateSequence { (1..1025).random() }.first { it != currentId }
            val pokemon = repo.api.pokemon(id.toString())
            val species = repo.api.species(id.toString())
            val facts = species.flavor
                .filter { it.language.name == "en" }
                .map { it.text.replace(Regex("[\\n\\f]+"), " ").trim() }
                .distinct()
            PokemonSpotlight(pokemon, facts.randomOrNull() ?: "A mysterious Pokémon awaits in the Pokédex.")
        }.onSuccess { spotlight ->
            _state.update { it.copy(spotlight = spotlight) }
        }
    }
    fun setDifficulty(value: Difficulty) { _state.update { it.copy(difficulty = value) } }
    fun setMusicVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        preferences.edit().putFloat("music_volume", volume).apply()
        _state.update { it.copy(musicVolume = volume) }
    }
    fun setCriesVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        preferences.edit().putFloat("cries_volume", volume).apply()
        _state.update { it.copy(criesVolume = volume) }
    }
    fun setUseOfficialArtwork(value: Boolean) {
        preferences.edit().putBoolean("use_official_artwork", value).apply()
        _state.update { it.copy(useOfficialArtwork = value) }
    }
    fun setDarkMode(value: Boolean) {
        preferences.edit().putBoolean("dark_mode", value).apply()
        _state.update { it.copy(darkMode = value) }
    }
    fun setRunMode(value: RunMode) {
        _state.update {
            it.copy(
                settings = GameSettings(value),
                generations = if (value == RunMode.ENDLESS) (1..9).toSet() else it.generations
            )
        }
    }
    fun toggleGeneration(value: Int) {
        _state.update { s ->
            if (s.settings.runMode == RunMode.ENDLESS) s
            else s.copy(generations = if (value in s.generations) s.generations - value else s.generations + value)
        }
    }

    fun start() = viewModelScope.launch {
        val s = _state.value
        if (s.generations.isEmpty()) { _state.update { it.copy(message = "Choose at least one generation") }; return@launch }
        timerJob?.cancel()
        accumulatedMs = 0L
        questionStartedAt = 0L
        _state.update {
            it.copy(
                loading = true,
                screen = Screen.GAME,
                index = 0,
                score = 0,
                livesRemaining = s.settings.runMode.lives,
                elapsedMs = 0,
                eliminatedPokemon = emptySet(),
                wrongPokemon = emptyList(),
                wrongPokemonTypes = emptyMap(),
                mistakeFlashId = 0,
                message = null
            )
        }
        runCatching {
            sessionPool = s.generations
                .flatMap { repo.api.generation(it).species.map(NamedResource::name) }
                .distinct()
            remainingPokemon.clear()
            remainingPokemon.addAll(sessionPool.shuffled())
            _state.update { it.copy(availablePokemon = sessionPool.size, availableNames = sessionPool.sorted()) }
            nextQuestion()
        }.onFailure { error -> _state.update { it.copy(loading = false, screen = Screen.SETUP, message = error.message ?: "Could not load Pokémon") } }
    }

    private suspend fun nextQuestion() {
        val answerName = remainingPokemon.firstOrNull() ?: run {
            finishGame()
            return
        }
        val answer = repo.api.pokemon(answerName)
        val needsIdentityData = _state.value.gameMode != GameMode.TYPE_MATCH
        val species = if (needsIdentityData) repo.api.species(answerName) else null
        val choices = if (_state.value.gameMode == GameMode.WHO_THAT_POKEMON) {
            (sessionPool.filterNot { it == answerName }.shuffled().take(3) + answerName)
                .shuffled()
                .map { repo.api.pokemon(it) }
        } else emptyList()
        val description = species?.flavor?.firstOrNull { it.language.name == "en" }?.text?.replace(Regex("[\\n\\f]+"), " ") ?: "No Pokédex description available."
        val gen = species?.generation?.name?.substringAfterLast('-')?.uppercase().orEmpty()
        val clues = if (_state.value.gameMode == GameMode.NAME_THAT_POKEMON) {
            listOf(
                description,
                "Height: ${answer.height / 10.0} m",
                shinyColorClue(answer.sprites.other.artwork.shiny),
                "First appeared in Generation $gen"
            )
        } else emptyList()
        remainingPokemon.removeFirst()
        _state.update {
            it.copy(
                loading = false,
                question = Question(answer, species, choices, clues),
                cluesShown = 1,
                selectedTypes = emptySet(),
                eliminatedTypes = emptySet(),
                eliminatedPokemon = emptySet(),
                message = null
            )
        }
        startQuestionTimer()
    }

    private suspend fun shinyColorClue(imageUrl: String?): String = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext "Shiny colors unavailable"
        val bitmap = runCatching {
            URL(imageUrl).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }.getInputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext "Shiny colors unavailable"

        try {
            val counts = mutableMapOf<String, Int>()
            val step = (minOf(bitmap.width, bitmap.height) / 60).coerceAtLeast(1)
            val hsv = FloatArray(3)
            for (y in 0 until bitmap.height step step) {
                for (x in 0 until bitmap.width step step) {
                    val pixel = bitmap.getPixel(x, y)
                    if (AndroidColor.alpha(pixel) < 128) continue
                    AndroidColor.colorToHSV(pixel, hsv)
                    val colorName = shinyColorName(hsv[0], hsv[1], hsv[2])
                    counts[colorName] = counts.getOrDefault(colorName, 0) + 1
                }
            }

            val ranked = counts.entries.sortedByDescending { it.value }
            val total = ranked.sumOf { it.value }.coerceAtLeast(1)
            val primary = ranked.firstOrNull()?.key ?: return@withContext "Shiny colors unavailable"
            val secondaryThreshold = (total * 0.15f).toInt()
            val secondary = ranked.drop(1).firstOrNull { it.value >= secondaryThreshold }?.key
            if (secondary == null) "Shiny color: $primary"
            else "Shiny colors: $primary and $secondary"
        } finally {
            bitmap.recycle()
        }
    }

    private fun shinyColorName(hue: Float, saturation: Float, value: Float): String = when {
        value < 0.16f -> "black"
        saturation < 0.12f && value > 0.88f -> "white"
        saturation < 0.18f -> "gray"
        hue < 15f || hue >= 345f -> "red"
        hue < 42f && value < 0.58f -> "brown"
        hue < 42f -> "orange"
        hue < 68f -> "yellow"
        hue < 170f -> "green"
        hue < 255f -> "blue"
        hue < 290f -> "purple"
        else -> "pink"
    }

    fun toggleType(type: String) {
        _state.update { state ->
            if (type in state.eliminatedTypes) state
            else state.copy(selectedTypes = if (type in state.selectedTypes) state.selectedTypes - type else state.selectedTypes + type)
        }
    }

    fun submitTypes() = viewModelScope.launch {
        val current = _state.value
        val question = current.question ?: return@launch
        val correctTypes = question.answer.types.map { it.type.name }.toSet()
        if (current.selectedTypes == correctTypes) {
            stopQuestionTimer()
            val nextScore = current.score + 1
            val nextIndex = current.index + 1
            val requestedCountReached = current.settings.runMode.questionLimit?.let { nextIndex >= it } == true
            if (requestedCountReached || remainingPokemon.isEmpty()) {
                _state.update { it.copy(score = nextScore, index = nextIndex) }
                finishGame()
            } else {
                _state.update { it.copy(score = nextScore, index = nextIndex, loading = true, message = "Correct!") }
                runCatching { nextQuestion() }.onFailure { _state.update { it.copy(loading = false, message = "Connection lost. Try again.") } }
            }
            return@launch
        }

        val wrongSelections = current.selectedTypes - correctTypes
        val nextLives = current.livesRemaining - 1
        _state.update {
            it.copy(
                livesRemaining = nextLives.coerceAtLeast(0),
                selectedTypes = it.selectedTypes - wrongSelections,
                eliminatedTypes = it.eliminatedTypes + wrongSelections,
                wrongPokemon = (it.wrongPokemon + question.answer.name).distinct(),
                wrongPokemonTypes = it.wrongPokemonTypes + (question.answer.name to question.answer.types.map { slot -> slot.type.name }),
                message = if (wrongSelections.isEmpty()) "One or more types are still missing — Poké Ball lost" else "Incorrect type crossed out — Poké Ball lost"
            )
        }
        if (nextLives <= 0) {
            stopQuestionTimer()
            finishGame()
        }
    }

    fun answer(value: String) = viewModelScope.launch {
        val current = _state.value
        val question = current.question ?: return@launch
        val normalizedAnswer = value.trim().replace(" ", "-")
        val correct = normalizedAnswer.equals(question.answer.name, ignoreCase = true)

        if (current.gameMode == GameMode.WHO_THAT_POKEMON && !correct) {
            if (normalizedAnswer in current.eliminatedPokemon) return@launch
            val nextLives = current.livesRemaining - 1
            _state.update {
                it.copy(
                    livesRemaining = nextLives.coerceAtLeast(0),
                    index = if (nextLives <= 0) it.index + 1 else it.index,
                    eliminatedPokemon = it.eliminatedPokemon + normalizedAnswer,
                    wrongPokemon = (it.wrongPokemon + question.answer.name).distinct(),
                    wrongPokemonTypes = it.wrongPokemonTypes + (question.answer.name to question.answer.types.map { slot -> slot.type.name }),
                    mistakeFlashId = it.mistakeFlashId + 1,
                    message = "${normalizedAnswer.displayName()} crossed out — Poké Ball lost"
                )
            }
            if (nextLives <= 0) {
                stopQuestionTimer()
                finishGame()
            }
            return@launch
        }

        stopQuestionTimer()
        val nextScore = current.score + if (correct) 1 else 0
        val nextIndex = current.index + 1
        val nextLives = current.livesRemaining - if (correct) 0 else 1
        val requestedCountReached = current.settings.runMode.questionLimit?.let { nextIndex >= it } == true
        val allPokemonAnswered = remainingPokemon.isEmpty()
        if (nextLives <= 0 || requestedCountReached || allPokemonAnswered) {
            _state.update {
                it.copy(
                    score = nextScore,
                    index = nextIndex,
                    livesRemaining = nextLives.coerceAtLeast(0),
                    wrongPokemon = if (correct) it.wrongPokemon else (it.wrongPokemon + question.answer.name).distinct(),
                    wrongPokemonTypes = if (correct) it.wrongPokemonTypes else it.wrongPokemonTypes +
                        (question.answer.name to question.answer.types.map { slot -> slot.type.name })
                )
            }
            finishGame()
        } else {
            _state.update {
                it.copy(
                    score = nextScore,
                    index = nextIndex,
                    livesRemaining = nextLives,
                    loading = true,
                    wrongPokemon = if (correct) it.wrongPokemon else (it.wrongPokemon + question.answer.name).distinct(),
                    wrongPokemonTypes = if (correct) it.wrongPokemonTypes else it.wrongPokemonTypes +
                        (question.answer.name to question.answer.types.map { slot -> slot.type.name }),
                    message = if (correct) "Correct!" else "It was ${question.answer.name.displayName()} — Poké Ball lost"
                )
            }
            runCatching { nextQuestion() }.onFailure { _state.update { it.copy(loading = false, message = "Connection lost. Try again.") } }
        }
    }

    fun revealClue() { _state.update { it.copy(cluesShown = (it.cluesShown + 1).coerceAtMost(4)) } }
    private fun finishGame() { _state.update { it.copy(screen = Screen.RESULT, loading = false) } }
    fun cancelGame() {
        timerJob?.cancel()
        timerJob = null
        questionStartedAt = 0L
        accumulatedMs = 0L
        remainingPokemon.clear()
        sessionPool = emptyList()
        _state.update { UiState(settings = it.settings, gameMode = it.gameMode, generations = it.generations, difficulty = it.difficulty, musicVolume = it.musicVolume, criesVolume = it.criesVolume, useOfficialArtwork = it.useOfficialArtwork, darkMode = it.darkMode, spotlight = it.spotlight) }
    }
    fun saveScore(name: String) = viewModelScope.launch {
        val s = _state.value
        val legacyDifficulty = when (s.gameMode) {
            GameMode.WHO_THAT_POKEMON -> "EASY"
            GameMode.NAME_THAT_POKEMON -> "HARD"
            GameMode.TYPE_MATCH -> "TYPE"
        }
        repo.save(ScoreEntity(player = name.trim().take(20).ifBlank { "Trainer" }, score = s.score, total = s.index, difficulty = legacyDifficulty, generation = s.generations.sorted().joinToString(","), durationMs = s.elapsedMs, runMode = s.settings.runMode.name, gameMode = s.gameMode.name, livesRemaining = s.livesRemaining))
        navigate(Screen.LEADERBOARD)
    }
    fun checkUpdates() = viewModelScope.launch {
        val repoName = BuildConfig.GITHUB_REPOSITORY
        if (repoName.startsWith("OWNER/")) { _state.update { it.copy(message = "Add the public GitHub repository in app/build.gradle.kts first") }; return@launch }
        runCatching { repo.api.release("https://api.github.com/repos/$repoName/releases/latest") }
            .onSuccess { release -> _state.update { it.copy(update = release, message = if (release.tag.removePrefix("v") != BuildConfig.VERSION_NAME) "Update ${release.tag} is available" else "You’re up to date") } }
            .onFailure { _state.update { it.copy(message = "Could not check for updates") } }
    }

    private fun startQuestionTimer() {
        questionStartedAt = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(elapsedMs = accumulatedMs + SystemClock.elapsedRealtime() - questionStartedAt) }
                delay(16)
            }
        }
    }

    private fun stopQuestionTimer() {
        timerJob?.cancel()
        timerJob = null
        if (questionStartedAt != 0L) {
            accumulatedMs += SystemClock.elapsedRealtime() - questionStartedAt
            questionStartedAt = 0L
            _state.update { it.copy(elapsedMs = accumulatedMs) }
        }
    }
}

private fun String.displayName(): String =
    replace('-', ' ').replaceFirstChar { it.uppercase() }
