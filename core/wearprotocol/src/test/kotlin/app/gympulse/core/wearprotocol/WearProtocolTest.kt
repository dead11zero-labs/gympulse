package app.gympulse.core.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolTest {
    @Test
    fun snapshot_roundTrip() {
        val snapshot = Fixtures.snapshot(
            version = 42,
            session = Fixtures.session(restEndsAt = 99_000).let { session ->
                session.copy(
                    exercises = session.exercises.map { exercise ->
                        exercise.copy(sets = exercise.sets.map { it.copy(fieldUpdatedAt = SetFieldTimes(weightKg = 1_200)) })
                    },
                )
            },
            CommandAck(Fixtures.cmdId(1)),
            CommandAck(Fixtures.cmdId(2), AckResult.SESSION_MISMATCH),
        )
        val decoded = WearProtocol.decodeSnapshot(WearProtocol.encodeSnapshot(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun snapshot_withoutSession_roundTrip() {
        val snapshot = WorkoutSnapshot(version = 7, session = null)
        assertEquals(snapshot, WearProtocol.decodeSnapshot(WearProtocol.encodeSnapshot(snapshot)))
    }

    @Test
    fun allCommands_roundTripWithTypeDiscriminator() {
        val commands: List<WearCommand> = listOf(
            CompleteSet(Fixtures.cmdId(1), 10, Fixtures.SESSION, Fixtures.SET_1),
            CompleteSet(Fixtures.cmdId(2), 11, Fixtures.SESSION, Fixtures.SET_1, completed = false),
            UpdateSet(Fixtures.cmdId(3), 12, Fixtures.SESSION, Fixtures.SET_1, weightKg = 82.5, reps = 6),
            UpdateSet(Fixtures.cmdId(4), 13, Fixtures.SESSION, Fixtures.SET_1, reps = 6),
            StartWorkout(Fixtures.cmdId(5), 14, planId = "plan-push"),
            StartWorkout(Fixtures.cmdId(6), 15, planId = null),
            AdjustRest(Fixtures.cmdId(7), 16, Fixtures.SESSION, deltaSeconds = -15),
            SkipRest(Fixtures.cmdId(8), 17, Fixtures.SESSION),
            Finish(Fixtures.cmdId(9), 18, Fixtures.SESSION),
            Discard(Fixtures.cmdId(10), 19, Fixtures.SESSION),
            HeartRateSummary(Fixtures.cmdId(11), 20, Fixtures.SESSION, averageBpm = 118, maxBpm = 171, sampleCount = 2_400),
        )
        for (command in commands) {
            val bytes = WearProtocol.encodeCommand(command)
            assertEquals(command, WearProtocol.decodeCommand(bytes))
        }
        val json = WearProtocol.encodeCommand(commands[2]).decodeToString()
        assertTrue(json, json.contains("\"type\":\"update_set\""))
        assertTrue(json, json.contains("\"weightKg\":82.5"))
        assertTrue(json, json.contains("\"protocolVersion\":${WearProtocol.VERSION}"))
    }

    @Test
    fun heartRateSummary_plausibility() {
        fun summary(avg: Int, max: Int, count: Int = 100) =
            HeartRateSummary(Fixtures.cmdId(1), 1, Fixtures.SESSION, avg, max, count)
        assertTrue(summary(120, 160).isPlausible)
        assertTrue(summary(120, 120).isPlausible)
        assertFalse(summary(120, 110).isPlausible)
        assertFalse(summary(10, 160).isPlausible)
        assertFalse(summary(120, 300).isPlausible)
        assertFalse(summary(120, 160, count = 0).isPlausible)
    }

    @Test
    fun plans_roundTrip() {
        val plans = PlanSummaryList(
            generatedAt = 1_000,
            plans = listOf(PlanSummary("p1", "Push", 5, color = 0xFF3366FF), PlanSummary("p2", "Pull", 4)),
        )
        assertEquals(plans, WearProtocol.decodePlans(WearProtocol.encodePlans(plans)))
    }

    @Test
    fun decode_ignoresUnknownFields() {
        val json = """
            {"protocolVersion":1,"version":3,"futureField":true,
             "session":{"sessionId":"s","startedAt":1,"newThing":[1,2],
               "exercises":[{"sessionExerciseId":"se","exerciseId":"e","name":"Squat",
                 "sets":[{"setId":"x","number":1,"updatedAt":5,"tempo":"3-1-1"}]}]}}
        """.trimIndent()
        val decoded = WearProtocol.decodeSnapshot(json.encodeToByteArray())
        assertEquals(3L, decoded?.version)
        assertEquals("Squat", decoded?.session?.exercises?.single()?.name)
        assertEquals(WearSetType.WORKING, decoded?.session?.exercises?.single()?.sets?.single()?.setType)
    }

    @Test
    fun decode_returnsNullForGarbageUnknownTypeAndOtherProtocol() {
        assertNull(WearProtocol.decodeSnapshot("not json".encodeToByteArray()))
        assertNull(WearProtocol.decodeSnapshot("""{"version":1}""".encodeToByteArray()))
        assertNull(WearProtocol.decodeCommand("""{"command":{"type":"teleport","commandId":"x","issuedAt":1}}""".encodeToByteArray()))
        assertNull(WearProtocol.decodeSnapshot("""{"protocolVersion":99,"version":1,"session":null}""".encodeToByteArray()))
        assertNull(
            WearProtocol.decodeCommand(
                """{"protocolVersion":99,"command":{"type":"skip_rest","commandId":"x","issuedAt":1,"sessionId":"s"}}"""
                    .encodeToByteArray(),
            ),
        )
        assertNull(WearProtocol.decodePlans(ByteArray(0)))
    }

    @Test
    fun weight_staysInKgWithFractions() {
        val command = UpdateSet(Fixtures.cmdId(1), 1, Fixtures.SESSION, Fixtures.SET_1, weightKg = 22.75)
        val decoded = WearProtocol.decodeCommand(WearProtocol.encodeCommand(command)) as UpdateSet
        assertEquals(22.75, decoded.weightKg!!, 0.0)
    }

    @Test
    fun paths() {
        val id = Fixtures.cmdId(5)
        assertEquals("/cmd/$id", WearPaths.command(id))
        assertEquals("/cmd/$id", WearPaths.command(id.uppercase()))
        assertEquals(id, WearPaths.commandIdOf("/cmd/$id"))
        assertTrue(WearPaths.isCommandPath("/cmd/$id"))
        assertFalse(WearPaths.isCommandPath("/cmd/not-a-uuid"))
        assertFalse(WearPaths.isCommandPath("/cmd/$id/extra"))
        assertFalse(WearPaths.isCommandPath(WearPaths.ACTIVE_WORKOUT))
        assertEquals("/workout/active", WearPaths.ACTIVE_WORKOUT)
        assertEquals("/plans/summary", WearPaths.PLANS_SUMMARY)
        assertTrue(runCatching { WearPaths.command("nope") }.isFailure)
    }

    @Test
    fun commandFactory_createsUuidV7WithTimestamp() {
        val command = WearCommands.updateSet(Fixtures.SESSION, Fixtures.SET_1, weightKg = 80.0, now = 1_700_000_000_000)
        assertEquals(1_700_000_000_000, command.issuedAt)
        assertTrue(WearPaths.isCommandPath(WearPaths.command(command.commandId)))
        assertEquals('7', command.commandId[14])
        val ids = List(50) { WearCommands.skipRest(Fixtures.SESSION).commandId }.toSet()
        assertEquals(50, ids.size)
    }
}
