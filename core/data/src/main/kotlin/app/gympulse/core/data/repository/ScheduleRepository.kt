package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.ScheduleDao
import app.gympulse.core.database.entity.ScheduleOverrideEntity
import app.gympulse.core.database.entity.ScheduleRuleEntity
import app.gympulse.core.model.Ids
import app.gympulse.core.model.ScheduleOverride
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.ScheduleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Wochenschema (Regeln je Wochentag) und Ausnahmen für einzelne Tage.
 *
 * Eine Regel gilt ab [ScheduleRule.validFrom]. Wird der Plan eines Wochentags geändert, endet die
 * alte Regel am Vortag und eine neue beginnt heute, damit vergangene Wochen unverändert bleiben.
 */
class ScheduleRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val time: TimeProvider,
) {
    fun observeRules(): Flow<List<ScheduleRule>> = dao.observeRules().map { list -> list.map { it.toModel() } }

    fun observeOverrides(from: LocalDate, to: LocalDate): Flow<List<ScheduleOverride>> =
        dao.observeOverrides(from.toString(), to.toString()).map { list -> list.map { it.toModel() } }

    /** Weist [weekday] ab heute [planId] zu; `null` entfernt die Zuweisung. */
    suspend fun setWeekdayPlan(weekday: DayOfWeek, planId: String?) {
        val now = time.now()
        val nowMs = now.toEpochMilli()
        val today = now.atZone(time.zone()).toLocalDate()
        val current = dao.getRules().filter { rule ->
            rule.weekday == weekday.value && (rule.validUntil == null || LocalDate.parse(rule.validUntil) >= today)
        }
        if (current.size == 1 && current[0].planId == planId && current[0].validUntil == null) return
        current.forEach { rule ->
            if (LocalDate.parse(rule.validFrom) >= today) {
                dao.softDeleteRule(rule.id, nowMs)
            } else {
                dao.upsertRule(rule.copy(validUntil = today.minusDays(1).toString(), updatedAt = nowMs))
            }
        }
        if (planId != null) {
            dao.upsertRule(
                ScheduleRuleEntity(
                    id = Ids.newId(nowMs),
                    planId = planId,
                    weekday = weekday.value,
                    validFrom = today.toString(),
                    validUntil = null,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                ),
            )
        }
    }

    /** Der Tag fällt aus; ein vorhandener Eintrag für dieses Datum wird ersetzt. */
    suspend fun skipDay(date: LocalDate) = replaceOverride(date, ScheduleOverrideType.SKIPPED, null)

    /** Zusätzlicher Plan an diesem Tag (z. B. ein anderer Plan als im Schema). */
    suspend fun addPlan(date: LocalDate, planId: String) =
        replaceOverride(date, ScheduleOverrideType.ADDED, planId)

    /** Verschiebt das Training von [from] nach [to]: [from] fällt aus, [planId] steht an [to]. */
    suspend fun moveDay(from: LocalDate, to: LocalDate, planId: String) {
        if (from == to) return
        replaceOverride(from, ScheduleOverrideType.SKIPPED, null)
        replaceOverride(to, ScheduleOverrideType.MOVED, planId)
    }

    /** Entfernt die Ausnahme dieses Tages; das Wochenschema gilt wieder. */
    suspend fun clearOverride(date: LocalDate) {
        val nowMs = time.now().toEpochMilli()
        dao.getOverridesOn(date.toString()).forEach { dao.softDeleteOverride(it.id, nowMs) }
    }

    private suspend fun replaceOverride(date: LocalDate, type: ScheduleOverrideType, planId: String?) {
        val nowMs = time.now().toEpochMilli()
        dao.getOverridesOn(date.toString()).forEach { dao.softDeleteOverride(it.id, nowMs) }
        dao.upsertOverride(
            ScheduleOverrideEntity(
                id = Ids.newId(nowMs),
                date = date.toString(),
                planId = planId,
                type = type,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }
}

private fun ScheduleRuleEntity.toModel() = ScheduleRule(
    id = id,
    planId = planId,
    weekday = weekday,
    validFrom = LocalDate.parse(validFrom),
    validUntil = validUntil?.let(LocalDate::parse),
)

private fun ScheduleOverrideEntity.toModel() = ScheduleOverride(
    id = id,
    date = LocalDate.parse(date),
    planId = planId,
    type = type,
)
