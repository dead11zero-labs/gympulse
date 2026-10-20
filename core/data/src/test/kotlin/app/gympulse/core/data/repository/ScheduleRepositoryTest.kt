package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.ScheduleDao
import app.gympulse.core.database.entity.ScheduleOverrideEntity
import app.gympulse.core.database.entity.ScheduleRuleEntity
import app.gympulse.core.model.ScheduleOverrideType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleRepositoryTest {
    private class FakeScheduleDao : ScheduleDao {
        val rules = MutableStateFlow<List<ScheduleRuleEntity>>(emptyList())
        val overrides = MutableStateFlow<List<ScheduleOverrideEntity>>(emptyList())

        override fun observeRules(): Flow<List<ScheduleRuleEntity>> =
            rules.map { list -> list.filter { it.deletedAt == null } }

        override fun observeOverrides(fromDate: String, toDate: String): Flow<List<ScheduleOverrideEntity>> =
            overrides.map { list -> list.filter { it.deletedAt == null && it.date >= fromDate && it.date <= toDate } }

        override suspend fun getRules(): List<ScheduleRuleEntity> = rules.value.filter { it.deletedAt == null }

        override suspend fun getOverridesOn(date: String): List<ScheduleOverrideEntity> =
            overrides.value.filter { it.deletedAt == null && it.date == date }

        override suspend fun upsertRule(rule: ScheduleRuleEntity) {
            rules.value = rules.value.filterNot { it.id == rule.id } + rule
        }

        override suspend fun upsertOverride(override: ScheduleOverrideEntity) {
            overrides.value = overrides.value.filterNot { it.id == override.id } + override
        }

        override suspend fun softDeleteRule(id: String, now: Long) {
            rules.value = rules.value.map { if (it.id == id) it.copy(deletedAt = now, updatedAt = now) else it }
        }

        override suspend fun softDeleteOverride(id: String, now: Long) {
            overrides.value = overrides.value.map {
                if (it.id == id) it.copy(deletedAt = now, updatedAt = now) else it
            }
        }
    }

    // Mittwoch, 21.10.2026
    private var now = Instant.parse("2026-10-21T09:30:00Z")
    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }
    private val dao = FakeScheduleDao()
    private val repo = ScheduleRepository(dao, time)

    private fun activeRules() = dao.rules.value.filter { it.deletedAt == null }

    @Test
    fun setWeekdayPlan_createsOpenRuleFromToday() = runTest {
        repo.setWeekdayPlan(DayOfWeek.MONDAY, "push")
        val rule = activeRules().single()
        assertEquals("push", rule.planId)
        assertEquals(1, rule.weekday)
        assertEquals("2026-10-21", rule.validFrom)
        assertNull(rule.validUntil)
    }

    @Test
    fun setWeekdayPlan_changeClosesOldRuleYesterday() = runTest {
        repo.setWeekdayPlan(DayOfWeek.MONDAY, "push")
        now = Instant.parse("2026-10-25T09:00:00Z")
        repo.setWeekdayPlan(DayOfWeek.MONDAY, "pull")
        val rules = activeRules().sortedBy { it.validFrom }
        assertEquals(2, rules.size)
        assertEquals("push", rules[0].planId)
        assertEquals("2026-10-24", rules[0].validUntil)
        assertEquals("pull", rules[1].planId)
        assertNull(rules[1].validUntil)
    }

    @Test
    fun setWeekdayPlan_changeOnSameDayReplacesRule() = runTest {
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, "legs")
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, "push")
        assertEquals("push", activeRules().single().planId)
    }

    @Test
    fun setWeekdayPlan_nullEndsAssignmentYesterday() = runTest {
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, "legs")
        now = Instant.parse("2026-10-30T09:00:00Z")
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, null)
        assertEquals("2026-10-29", activeRules().single().validUntil)
    }

    @Test
    fun setWeekdayPlan_samePlanIsNoOp() = runTest {
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, "legs")
        now = Instant.parse("2026-10-30T09:00:00Z")
        repo.setWeekdayPlan(DayOfWeek.FRIDAY, "legs")
        assertNull(activeRules().single().validUntil)
    }

    @Test
    fun moveDay_skipsSourceAndAddsTarget() = runTest {
        repo.moveDay(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22), "pull")
        val list = dao.overrides.value.sortedBy { it.date }
        assertEquals(ScheduleOverrideType.SKIPPED, list[0].type)
        assertEquals("2026-10-21", list[0].date)
        assertEquals(ScheduleOverrideType.MOVED, list[1].type)
        assertEquals("pull", list[1].planId)
    }

    @Test
    fun overrides_replaceAndClearPerDate() = runTest {
        val day = LocalDate.of(2026, 10, 23)
        repo.skipDay(day)
        repo.addPlan(day, "arms")
        assertEquals(ScheduleOverrideType.ADDED, dao.getOverridesOn("2026-10-23").single().type)
        repo.clearOverride(day)
        assertEquals(0, dao.getOverridesOn("2026-10-23").size)
    }
}
