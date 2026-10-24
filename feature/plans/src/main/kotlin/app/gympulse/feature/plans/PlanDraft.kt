package app.gympulse.feature.plans

import app.gympulse.core.data.repository.PlanExerciseDefaults
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.WorkoutPlan

/**
 * Eine Übung im Editor. Zahlenfelder bleiben als Text, damit halb getippte Eingaben
 * ("6", "62.") erhalten bleiben; geprüft und umgewandelt wird erst in [PlanDraft.validate].
 */
internal data class DraftExercise(
    val id: String,
    val exerciseId: String,
    /** Anzeigename; null, wenn die Übung nicht (mehr) gefunden wurde. */
    val name: String?,
    val supersetGroup: Int? = null,
    val sets: String = DEFAULT_SETS,
    val repsMin: String = DEFAULT_REPS_MIN,
    val repsMax: String = DEFAULT_REPS_MAX,
    val weight: String = "",
    val rest: String = "",
    /** Cardio: nur die Zielzeit in Minuten; Sätze, Wiederholungen, Gewicht und Pause entfallen. */
    val isCardio: Boolean = false,
    val durationMinutes: String = "",
) {
    companion object {
        val DEFAULT_SETS = PlanExerciseDefaults.SETS.toString()
        val DEFAULT_REPS_MIN = PlanExerciseDefaults.REPS_MIN.toString()
        val DEFAULT_REPS_MAX = PlanExerciseDefaults.REPS_MAX.toString()
    }
}

/** Fehler einer einzelnen Übungszeile. */
internal data class ItemErrors(
    val sets: Boolean = false,
    val reps: Boolean = false,
    val weight: Boolean = false,
    val rest: Boolean = false,
    val duration: Boolean = false,
) {
    val any: Boolean get() = sets || reps || weight || rest || duration
}

internal data class DraftValidation(
    val nameBlank: Boolean,
    val noExercises: Boolean,
    val itemErrors: Map<String, ItemErrors>,
) {
    val isValid: Boolean get() = !nameBlank && !noExercises && itemErrors.values.none { it.any }
}

/**
 * Bearbeitungsstand eines Plans. Alle Änderungen liefern eine neue Instanz, die Logik
 * (Reihenfolge, Supersätze, Validierung) ist damit ohne Android testbar.
 */
internal data class PlanDraft(
    val planId: String,
    val name: String = "",
    val description: String = "",
    val items: List<DraftExercise> = emptyList(),
    val color: Long? = null,
    val isArchived: Boolean = false,
) {
    fun updateItem(itemId: String, transform: (DraftExercise) -> DraftExercise): PlanDraft =
        copy(items = items.map { if (it.id == itemId) transform(it) else it })

    /** Verschiebt eine Übung um [delta] Positionen (negativ = nach oben). */
    fun move(itemId: String, delta: Int): PlanDraft {
        val from = items.indexOfFirst { it.id == itemId }
        val to = from + delta
        if (from < 0 || to !in items.indices) return this
        val list = items.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(items = normalizeGroups(list))
    }

    fun remove(itemId: String): PlanDraft = copy(items = normalizeGroups(items.filterNot { it.id == itemId }))

    fun addExercises(exercises: List<Exercise>, newId: () -> String): PlanDraft {
        val added = exercises.map { exercise ->
            val cardio = exercise.category == ExerciseCategory.CARDIO
            val usesReps = PlanExerciseDefaults.usesReps(exercise.trackingType) && !cardio
            DraftExercise(
                id = newId(),
                exerciseId = exercise.id,
                name = exercise.name,
                isCardio = cardio,
                durationMinutes = if (cardio) PlanExerciseDefaults.CARDIO_MINUTES.toString() else "",
                sets = (if (cardio) PlanExerciseDefaults.SETS_DURATION else PlanExerciseDefaults.defaultSets(exercise.trackingType)).toString(),
                repsMin = if (usesReps) DraftExercise.DEFAULT_REPS_MIN else "",
                repsMax = if (usesReps) DraftExercise.DEFAULT_REPS_MAX else "",
            )
        }
        return copy(items = items + added)
    }

    /**
     * Verbindet die Übung mit der nächsten zu einem Supersatz oder trennt die Verbindung wieder,
     * wenn beide schon zur selben Gruppe gehören. Bei einer Trennung bilden die nachfolgenden
     * Mitglieder eine eigene Gruppe (oder fallen heraus, wenn sie allein bleiben).
     */
    fun toggleLinkWithNext(itemId: String): PlanDraft {
        val index = items.indexOfFirst { it.id == itemId }
        if (index < 0 || index >= items.lastIndex) return this
        val current = items[index]
        val next = items[index + 1]
        val newGroup = (items.maxOfOrNull { it.supersetGroup ?: 0 } ?: 0) + 1
        val changed = if (current.supersetGroup != null && current.supersetGroup == next.supersetGroup) {
            val old = current.supersetGroup
            items.mapIndexed { i, item ->
                if (i > index && item.supersetGroup == old) item.copy(supersetGroup = newGroup) else item
            }
        } else {
            val target = current.supersetGroup ?: newGroup
            val nextGroup = next.supersetGroup
            items.mapIndexed { i, item ->
                when {
                    i == index || i == index + 1 -> item.copy(supersetGroup = target)
                    nextGroup != null && item.supersetGroup == nextGroup -> item.copy(supersetGroup = target)
                    else -> item
                }
            }
        }
        return copy(items = normalizeGroups(changed))
    }

    fun validate(): DraftValidation = DraftValidation(
        nameBlank = name.isBlank(),
        noExercises = items.isEmpty(),
        itemErrors = items.associate { it.id to it.errors() },
    )

    /** Der fertige Plan oder null, wenn die Eingaben ungültig sind. */
    fun toPlanOrNull(): WorkoutPlan? {
        if (!validate().isValid) return null
        return WorkoutPlan(
            id = planId,
            name = name.trim(),
            description = description.trim().ifEmpty { null },
            color = color,
            isArchived = isArchived,
            exercises = items.mapIndexed { index, item ->
                if (item.isCardio) {
                    // Zielminuten stehen in targetRepsMin (kein eigenes Datenbankfeld).
                    PlanExercise(
                        id = item.id,
                        exerciseId = item.exerciseId,
                        sortOrder = index,
                        supersetGroup = item.supersetGroup,
                        targetSets = PlanExerciseDefaults.SETS_DURATION,
                        targetRepsMin = item.durationMinutes.toIntOrNull(),
                        targetRepsMax = null,
                        targetWeightKg = null,
                        restSeconds = null,
                    )
                } else {
                    PlanExercise(
                        id = item.id,
                        exerciseId = item.exerciseId,
                        sortOrder = index,
                        supersetGroup = item.supersetGroup,
                        targetSets = item.sets.toInt(),
                        targetRepsMin = item.repsMin.toIntOrNull(),
                        targetRepsMax = item.repsMax.toIntOrNull(),
                        targetWeightKg = parseDecimal(item.weight),
                        restSeconds = item.rest.toIntOrNull(),
                    )
                }
            },
        )
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
        const val MAX_SETS = 99
        const val MAX_REPS = 999
        const val MAX_WEIGHT_KG = 1000.0
        const val MAX_REST_SECONDS = 3600
        const val MAX_DURATION_MINUTES = 999

        /** Baut den Bearbeitungsstand aus einem gespeicherten Plan; [names] bildet Übungs-ID auf Namen ab. */
        fun from(plan: WorkoutPlan, names: Map<String, String>, cardioIds: Set<String> = emptySet()): PlanDraft = PlanDraft(
            planId = plan.id,
            name = plan.name,
            description = plan.description.orEmpty(),
            color = plan.color,
            isArchived = plan.isArchived,
            items = normalizeGroups(
                plan.exercises.sortedBy { it.sortOrder }.map { item ->
                    DraftExercise(
                        id = item.id,
                        exerciseId = item.exerciseId,
                        name = names[item.exerciseId],
                        supersetGroup = item.supersetGroup,
                        sets = item.targetSets.toString(),
                        repsMin = item.targetRepsMin?.toString().orEmpty(),
                        repsMax = item.targetRepsMax?.toString().orEmpty(),
                        weight = item.targetWeightKg?.let(::formatDecimal).orEmpty(),
                        rest = item.restSeconds?.toString().orEmpty(),
                        isCardio = item.exerciseId in cardioIds,
                        durationMinutes = if (item.exerciseId in cardioIds) item.targetRepsMin?.toString().orEmpty() else "",
                    )
                },
            ),
        )
    }
}

private fun DraftExercise.errors(): ItemErrors {
    if (isCardio) {
        val minutes = durationMinutes.toIntOrNull()
        return ItemErrors(duration = durationMinutes.isNotEmpty() && (minutes == null || minutes !in 1..PlanDraft.MAX_DURATION_MINUTES))
    }
    val setsValue = sets.toIntOrNull()
    val min = repsMin.toIntOrNull()
    val max = repsMax.toIntOrNull()
    val minInvalid = repsMin.isNotEmpty() && (min == null || min !in 1..PlanDraft.MAX_REPS)
    val maxInvalid = repsMax.isNotEmpty() && (max == null || max !in 1..PlanDraft.MAX_REPS)
    val rangeInvalid = min != null && max != null && min > max
    val weightValue = parseDecimal(weight)
    val restValue = rest.toIntOrNull()
    return ItemErrors(
        sets = setsValue == null || setsValue !in 1..PlanDraft.MAX_SETS,
        reps = minInvalid || maxInvalid || rangeInvalid,
        weight = weight.isNotBlank() && (weightValue == null || weightValue !in 0.0..PlanDraft.MAX_WEIGHT_KG),
        rest = rest.isNotEmpty() && (restValue == null || restValue !in 0..PlanDraft.MAX_REST_SECONDS),
    )
}

/**
 * Supersatz-Gruppen müssen zusammenhängen und mindestens zwei Übungen haben. Alles andere
 * wird gelöst; die verbleibenden Gruppen werden fortlaufend ab 1 nummeriert.
 */
internal fun normalizeGroups(items: List<DraftExercise>): List<DraftExercise> {
    val result = ArrayList<DraftExercise>(items.size)
    var nextGroup = 1
    var i = 0
    while (i < items.size) {
        val group = items[i].supersetGroup
        var end = i + 1
        if (group != null) {
            while (end < items.size && items[end].supersetGroup == group) end++
        }
        if (group != null && end - i >= 2) {
            for (k in i until end) result += items[k].copy(supersetGroup = nextGroup)
            nextGroup++
        } else {
            for (k in i until end) result += items[k].copy(supersetGroup = null)
        }
        i = end
    }
    return result
}

/** Beschriftungen wie "A1", "A2", "B1" je Übungs-ID; Übungen ohne Supersatz fehlen in der Map. */
internal fun supersetLabels(items: List<DraftExercise>): Map<String, String> {
    val counters = HashMap<Int, Int>()
    val labels = LinkedHashMap<String, String>()
    for (item in items) {
        val group = item.supersetGroup ?: continue
        val position = (counters[group] ?: 0) + 1
        counters[group] = position
        val letter = 'A' + (group - 1) % LETTER_COUNT
        labels[item.id] = "$letter$position"
    }
    return labels
}

private const val LETTER_COUNT = 26

/** Nimmt Punkt und Komma als Dezimaltrenner; null bei ungültiger oder nicht endlicher Zahl. */
internal fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Lässt nur Ziffern zu, begrenzt auf [maxLength] Zeichen. */
internal fun sanitizeDigits(text: String, maxLength: Int = 4): String =
    text.filter { it.isDigit() }.take(maxLength)

/** Lässt Ziffern und höchstens einen Dezimaltrenner (Punkt oder Komma) zu. */
internal fun sanitizeDecimal(text: String, maxLength: Int = 7): String {
    var separatorSeen = false
    val filtered = text.filter { char ->
        when {
            char.isDigit() -> true
            (char == '.' || char == ',') && !separatorSeen -> {
                separatorSeen = true
                true
            }
            else -> false
        }
    }
    return filtered.take(maxLength)
}

/** Übungen, deren Name [query] enthält (Groß-/Kleinschreibung egal); leere Suche liefert alle. */
internal fun filterExercises(exercises: List<Exercise>, query: String): List<Exercise> {
    val needle = query.trim()
    if (needle.isEmpty()) return exercises
    return exercises.filter { it.name.contains(needle, ignoreCase = true) }
}
