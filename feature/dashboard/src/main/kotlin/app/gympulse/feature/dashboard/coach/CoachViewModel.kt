package app.gympulse.feature.dashboard.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.data.repository.InsightsRepository
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.insights.narration.AppLanguage
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.InsightSummaryBuilder
import app.gympulse.core.insights.narration.NarrationSource
import app.gympulse.core.insights.narration.SourceAwareNarrator
import app.gympulse.core.insights.narration.TemplateNarrator
import app.gympulse.core.insights.narration.narrateToString
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Zusammenfassung für den Coach, in der aktuellen App-Sprache. Alles lokal. */
interface CoachDataSource {
    fun observeSummary(): Flow<InsightSummary>
}

class DefaultCoachDataSource @Inject constructor(
    private val insights: InsightsRepository,
) : CoachDataSource {
    private val builder = InsightSummaryBuilder()

    override fun observeSummary(): Flow<InsightSummary> {
        val locale = Locale.getDefault()
        val language = AppLanguage.fromLocale(locale)
        return insights.observeReport(locale.language).map { report ->
            builder.build(report.insights, report.exerciseNames, language)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoachModule {
    @Binds
    abstract fun bindCoachDataSource(impl: DefaultCoachDataSource): CoachDataSource
}

/**
 * Text des Modells je Zusammenfassung, nur im Speicher: Wer das Dashboard erneut öffnet, startet das
 * Modell nicht noch einmal. Nichts davon wird gespeichert, geloggt oder gesichert.
 */
@Singleton
class CoachTextCache @Inject constructor() {
    data class Key(val language: AppLanguage, val summaryText: String)

    private val entries = LinkedHashMap<Key, String>()

    @Synchronized
    fun get(key: Key): String? = entries[key]

    @Synchronized
    fun put(key: Key, text: String) {
        entries.remove(key)
        entries[key] = text
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun clear() = entries.clear()

    private companion object {
        const val MAX_ENTRIES = 4
    }
}

/** Zustand der Coach-Karte. */
sealed interface CoachUiState {
    /** Feature aus oder nichts zu erzählen: keine Karte. */
    data object Hidden : CoachUiState

    /** Modell lädt bzw. hat noch keinen Text geliefert. */
    data object Loading : CoachUiState

    /**
     * @property source [NarrationSource.AI] zeigt den Hinweis "KI-generiert".
     * @property streaming Text wächst noch.
     */
    data class Content(val text: String, val source: NarrationSource, val streaming: Boolean) : CoachUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CoachViewModel @Inject constructor(
    featureGate: FeatureGate,
    dataSource: CoachDataSource,
    private val narrator: SourceAwareNarrator,
    downloads: ModelDownloadManager,
    private val cache: CoachTextCache,
) : ViewModel() {

    private val regenerations = MutableStateFlow(0)

    val uiState: StateFlow<CoachUiState> = featureGate.observe(Feature.AI_COACH)
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(CoachUiState.Hidden)
            } else {
                // Wechselt das Modell auf Ready (oder weg), wird neu formuliert.
                val modelReady = downloads.state.map { it is ModelDownloadState.Ready }.distinctUntilChanged()
                combine(
                    dataSource.observeSummary().distinctUntilChanged { a, b ->
                        a.language == b.language && a.text == b.text
                    },
                    modelReady,
                    regenerations,
                ) { summary, _, _ -> summary }
                    .flatMapLatest { summary -> narrate(summary) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CoachUiState.Hidden)

    /** Formuliert den Text neu, auch wenn ein gespeicherter vorliegt. */
    fun regenerate() {
        cache.clear()
        regenerations.value += 1
    }

    private fun narrate(summary: InsightSummary): Flow<CoachUiState> = flow {
        if (summary.isEmpty) {
            emit(CoachUiState.Hidden)
            return@flow
        }
        val key = CoachTextCache.Key(summary.language, summary.text)
        cache.get(key)?.let {
            emit(CoachUiState.Content(it, NarrationSource.AI, streaming = false))
            return@flow
        }
        emit(CoachUiState.Loading)
        val text = StringBuilder()
        var source = NarrationSource.TEMPLATE
        try {
            narrator.narrateWithSource(summary).collect { chunk ->
                source = chunk.source
                text.append(chunk.delta)
                emit(CoachUiState.Content(text.toString(), source, streaming = true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Modell fiel mitten im Text aus oder der Stream brach ab: lieber Textbausteine als ein Halbsatz.
            emit(CoachUiState.Content(TemplateNarrator().narrateToString(summary), NarrationSource.TEMPLATE, false))
            return@flow
        }
        val result = text.toString()
        if (result.isBlank()) {
            emit(CoachUiState.Hidden)
            return@flow
        }
        if (source == NarrationSource.AI) cache.put(key, result)
        emit(CoachUiState.Content(result, source, streaming = false))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
