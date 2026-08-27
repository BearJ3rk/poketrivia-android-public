package com.example.poketrivia

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Difficulty { EASY, HARD }
enum class Screen { HOME, SETUP, GAME, RESULT, LEADERBOARD, SETTINGS }
enum class RunMode(val label: String, val questionLimit: Int?, val lives: Int, val selectable: Boolean = true) {
    TEN("10", 10, 1, selectable = false),
    TWENTY_FIVE("25", 25, 1),
    FIFTY("50", 50, 2),
    ONE_HUNDRED("100", 100, 3),
    ALL("Entire Gen", null, 4),
    ENDLESS("Endless - Every Pokemon Ever", null, 5)
}
data class GameSettings(val runMode: RunMode = RunMode.TWENTY_FIVE)
data class Question(val answer: PokemonResponse, val species: SpeciesResponse, val choices: List<PokemonResponse>, val clues: List<String>)
data class UiState(
    val screen: Screen = Screen.HOME,
    val settings: GameSettings = GameSettings(),
    val generations: Set<Int> = (1..9).toSet(),
    val difficulty: Difficulty = Difficulty.EASY,
    val loading: Boolean = false,
    val question: Question? = null,
    val index: Int = 0,
    val score: Int = 0,
    val availablePokemon: Int = 0,
    val livesRemaining: Int = RunMode.TWENTY_FIVE.lives,
    val elapsedMs: Long = 0,
    val cluesShown: Int = 1,
    val musicVolume: Float = 0.75f,
    val criesVolume: Float = 0.50f,
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
                if (preferences.getBoolean("cries_enabled", true)) 0.50f else 0f
            )
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
        _state.update { it.copy(loading = true, screen = Screen.GAME, index = 0, score = 0, livesRemaining = s.settings.runMode.lives, elapsedMs = 0, message = null) }
        runCatching {
            sessionPool = s.generations
                .flatMap { repo.api.generation(it).species.map(NamedResource::name) }
                .distinct()
            remainingPokemon.clear()
            remainingPokemon.addAll(sessionPool.shuffled())
            _state.update { it.copy(availablePokemon = sessionPool.size) }
            nextQuestion()
        }.onFailure { error -> _state.update { it.copy(loading = false, screen = Screen.SETUP, message = error.message ?: "Could not load Pokémon") } }
    }

    private suspend fun nextQuestion() {
        val answerName = remainingPokemon.firstOrNull() ?: run {
            finishGame()
            return
        }
        val answer = repo.api.pokemon(answerName)
        val species = repo.api.species(answerName)
        val choices = (sessionPool.filterNot { it == answerName }.shuffled().take(3) + answerName)
            .shuffled()
            .map { repo.api.pokemon(it) }
        val description = species.flavor.firstOrNull { it.language.name == "en" }?.text?.replace(Regex("[\\n\\f]+"), " ") ?: "No Pokédex description available."
        val gen = species.generation.name.substringAfterLast('-').uppercase()
        val clues = listOf(description, "Height: ${answer.height / 10.0} m", "Shiny form", "First appeared in Generation $gen")
        remainingPokemon.removeFirst()
        _state.update { it.copy(loading = false, question = Question(answer, species, choices, clues), cluesShown = 1, message = null) }
        startQuestionTimer()
    }

    fun answer(value: String) = viewModelScope.launch {
        val current = _state.value
        stopQuestionTimer()
        val correct = value.trim().replace(" ", "-").equals(current.question?.answer?.name, ignoreCase = true)
        val nextScore = current.score + if (correct) 1 else 0
        val nextIndex = current.index + 1
        val nextLives = current.livesRemaining - if (correct) 0 else 1
        val requestedCountReached = current.settings.runMode.questionLimit?.let { nextIndex >= it } == true
        val allPokemonAnswered = remainingPokemon.isEmpty()
        if (nextLives <= 0 || requestedCountReached || allPokemonAnswered) {
            _state.update { it.copy(score = nextScore, index = nextIndex, livesRemaining = nextLives.coerceAtLeast(0)) }; finishGame()
        } else {
            _state.update { it.copy(score = nextScore, index = nextIndex, livesRemaining = nextLives, loading = true, message = if (correct) "Correct!" else "It was ${current.question?.answer?.name} — Poké Ball lost") }
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
        _state.update { UiState(settings = it.settings, generations = it.generations, difficulty = it.difficulty, musicVolume = it.musicVolume, criesVolume = it.criesVolume) }
    }
    fun saveScore(name: String) = viewModelScope.launch {
        val s = _state.value
        repo.save(ScoreEntity(player = name.trim().take(20).ifBlank { "Trainer" }, score = s.score, total = s.index, difficulty = s.difficulty.name, generation = s.generations.sorted().joinToString(","), durationMs = s.elapsedMs, runMode = s.settings.runMode.name, livesRemaining = s.livesRemaining))
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
