package app.gympulse.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.WeekAnalysisData
import app.gympulse.core.data.repository.WeekAnalysisRepository
import app.gympulse.core.data.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/** Datenzugriff der Wochenanalyse; in Tests durch eine Fake-Implementierung ersetzbar. */
interface WeekAnalysisDataSource {
    /** Sessions ab [from] samt Übungen, Namen in der Sprache des Geräts. */
    fun observe(from: Instant): Flow<WeekAnalysisData>
}

class DefaultWeekAnalysisDataSource @Inject constructor(
    private val repository: WeekAnalysisRepository,
) : WeekAnalysisDataSource {
    override fun observe(from: Instant): Flow<WeekAnalysisData> =
        repository.observe(from, Locale.getDefault().language)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeekAnalysisModule {
    @Binds
    abstract fun bindDataSource(impl: DefaultWeekAnalysisDataSource): WeekAnalysisDataSource
}

data class WeekAnalysisUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    /** [WEEKS_LOADED] Wochen, älteste zuerst; die letzte ist die laufende. */
    val weeks: List<WeekStats> = emptyList(),
    /** Index der gewählten Woche in [weeks]. */
    val selectedIndex: Int = WEEKS_LOADED - 1,
    val highlights: WeekHighlights = WeekHighlights(),
) {
    val selected: WeekStats? get() = weeks.getOrNull(selectedIndex)

    /** Die Woche davor, Grundlage der Deltas. */
    val previous: WeekStats? get() = weeks.getOrNull(selectedIndex - 1)

    /** Die letzten [WEEKS_SHOWN] Wochen für das Verlaufsdiagramm. */
    val trendWeeks: List<WeekStats> get() = weeks.takeLast(WEEKS_SHOWN)

    /** Position der gewählten Woche in [trendWeeks]. */
    val selectedTrendIndex: Int get() = selectedIndex - (weeks.size - trendWeeks.size)

    val canGoBack: Boolean get() = selectedTrendIndex > 0
    val canGoForward: Boolean get() = selectedIndex < weeks.size - 1
    val isCurrentWeek: Boolean get() = selectedIndex == weeks.size - 1
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekAnalysisViewModel @Inject constructor(
    private val data: WeekAnalysisDataSource,
    private val weekStartProvider: WeekStartProvider,
    private val time: TimeProvider,
) : ViewModel() {
    /** Wie viele Wochen die Auswahl hinter der laufenden Woche liegt (0 = laufende Woche). */
    private val weeksBack = MutableStateFlow(0)

    fun showPreviousWeek() {
        if (weeksBack.value < WEEKS_SHOWN - 1) weeksBack.value += 1
    }

    fun showNextWeek() {
        if (weeksBack.value > 0) weeksBack.value -= 1
    }

    /** [trendIndex] ist die Position im Verlaufsdiagramm (0 = älteste gezeigte Woche). */
    fun selectTrendWeek(trendIndex: Int) {
        weeksBack.value = (WEEKS_SHOWN - 1 - trendIndex).coerceIn(0, WEEKS_SHOWN - 1)
    }

    val uiState: StateFlow<WeekAnalysisUiState> = weekStartProvider.weekStartsOn.flatMapLatest { weekStartsOn ->
        val zone = time.zone()
        val today = time.now().atZone(zone).toLocalDate()
        val currentWeekStart = weekStartOf(today, weekStartsOn)
        val from = currentWeekStart.minusWeeks((WEEKS_LOADED - 1).toLong()).atStartOfDay(zone).toInstant()
        combine(data.observe(from), weeksBack) { loaded, back ->
            val weeks = buildWeeks(loaded.sessions, loaded.exercises, currentWeekStart, weekStartsOn, zone)
            val index = WEEKS_LOADED - 1 - back
            WeekAnalysisUiState(
                isLoading = false,
                today = today,
                weeks = weeks,
                selectedIndex = index,
                highlights = weekHighlights(weeks[index], weeks.subList(0, index)),
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        WeekAnalysisUiState(today = time.now().atZone(time.zone()).toLocalDate()),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
