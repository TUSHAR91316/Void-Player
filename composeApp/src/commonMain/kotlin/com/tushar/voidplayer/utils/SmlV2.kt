package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.math.abs
import kotlin.math.ln

/** Deterministic, tag-derived SML v2 features. These are estimates, not audio analysis. */
data class SmlFeatureVector(
    val energy: Double,
    val valence: Double,
    val danceability: Double,
    val acousticness: Double,
    val tempoNorm: Double,
    val durationNorm: Double
)

data class ListeningStats(
    val plays: Int = 0,
    val completions: Int = 0,
    val skips: Int = 0,
    val repeats: Int = 0,
    val lastPlayedEpochMillis: Long = 0
)

/**
 * SML v2.1 scoring spec. The C# port (desktop-winui/Services/SmlV2.cs) must produce identical
 * results; test-fixtures/sml-v2.json is the shared contract both test suites run.
 */
object SmlV2 {
    // Matched as whole words of the title/album/genre, so "sun" does not match "Sunday" and
    // "night" does not match "nightcore".
    private val positive = setOf("happy", "joy", "love", "sun", "sunshine", "summer", "smile", "dance", "celebrate", "bright", "party", "fun")
    private val mellow = setOf("sad", "blue", "blues", "rain", "alone", "lonely", "tears", "night", "sleep", "lullaby", "ambient", "piano", "acoustic")
    private val wordSeparator = Regex("[^\\p{L}\\p{N}]+")

    /** Largest effective BPM change (half/double-time aware) that radio picks accept. */
    const val MAX_RADIO_BPM_JUMP = 25

    /** Picks within this score of the best are treated as equally good. */
    const val PICK_SCORE_MARGIN = 0.05
    const val PICK_POOL_SIZE = 3

    /** Lightweight Latin diacritic folding for locale-independent library search. */
    fun foldForSearch(value: String): String = buildString(value.length) {
        for (char in value.lowercase()) {
            append(when (char) {
                in "àáâãäåāăą" -> 'a'; in "çćč" -> 'c'; in "ďđ" -> 'd'
                in "èéêëēėęě" -> 'e'; in "ìíîïīį" -> 'i'; in "ł" -> 'l'
                in "ñńň" -> 'n'; in "òóôõöøō" -> 'o'; in "ř" -> 'r'
                in "śšş" -> 's'; in "ť" -> 't'; in "ùúûüū" -> 'u'
                in "ýÿ" -> 'y'; in "žźż" -> 'z'; else -> char
            })
        }
    }

    fun features(song: Song): SmlFeatureVector {
        val words = "${song.title} ${song.album} ${song.genre}".lowercase().split(wordSeparator).toSet()
        val genre = song.genre.lowercase()
        val energy = song.acousticEnergy.toDouble().coerceIn(0.0, 1.0)
        val valence = when {
            words.any(positive::contains) -> 0.78
            words.any(mellow::contains) -> 0.32
            else -> 0.5
        }
        val danceability = when {
            listOf("dance", "disco", "house", "techno", "edm", "funk", "reggaeton").any(genre::contains) -> 0.85
            song.bpm in 90..140 -> 0.68
            song.bpm > 0 -> 0.42
            else -> 0.5
        }
        val acousticness = when {
            listOf("acoustic", "folk", "classical", "piano", "unplugged").any(genre::contains) -> 0.82
            listOf("metal", "edm", "electronic", "techno", "house").any(genre::contains) -> 0.16
            else -> 0.5
        }
        return SmlFeatureVector(
            energy,
            valence,
            danceability,
            acousticness,
            (song.bpm.coerceIn(0, 240) / 240.0),
            (song.duration.coerceAtLeast(0L) / 600_000.0).coerceIn(0.0, 1.0)
        )
    }

    /**
     * Camelot notation for a key tag, or null when it cannot be read.
     *
     * Accepts Camelot ("8A"), Open Key ("1m" = 8A, "1d" = 8B) and note names ("Am", "A minor",
     * "Amin", "C", "Cmaj", "C major", "F#", "Bb"). A lowercase "m" suffix means minor; an
     * uppercase "M" means major.
     */
    fun camelot(key: String): String? {
        val normalized = key.trim().replace("♭", "b").replace("♯", "#").replace(" ", "")
        if (normalized.isEmpty()) return null
        Regex("(?i)^(1[0-2]|[1-9])([ab])$").matchEntire(normalized)?.let { direct ->
            return "${direct.groupValues[1]}${direct.groupValues[2].uppercase()}"
        }
        Regex("(?i)^(1[0-2]|[1-9])([dm])$").matchEntire(normalized)?.let { openKey ->
            val number = (openKey.groupValues[1].toInt() + 6) % 12 + 1
            return "$number${if (openKey.groupValues[2].equals("m", true)) "A" else "B"}"
        }
        val lower = normalized.lowercase()
        val (note, minor) = when {
            lower.endsWith("minor") -> lower.dropLast(5) to true
            lower.endsWith("major") -> lower.dropLast(5) to false
            lower.endsWith("min") -> lower.dropLast(3) to true
            lower.endsWith("maj") -> lower.dropLast(3) to false
            normalized.endsWith("m") -> lower.dropLast(1) to true
            normalized.endsWith("M") -> lower.dropLast(1) to false
            else -> lower to false
        }
        val enharmonic = mapOf("db" to "c#", "eb" to "d#", "gb" to "f#", "ab" to "g#", "bb" to "a#", "b#" to "c", "e#" to "f", "cb" to "b", "fb" to "e")
        val pitch = enharmonic[note] ?: note
        val major = mapOf("c" to 8, "g" to 9, "d" to 10, "a" to 11, "e" to 12, "b" to 1, "f#" to 2, "c#" to 3, "g#" to 4, "d#" to 5, "a#" to 6, "f" to 7)
        val minorMap = mapOf("a" to 8, "e" to 9, "b" to 10, "f#" to 11, "c#" to 12, "g#" to 1, "d#" to 2, "a#" to 3, "f" to 4, "c" to 5, "g" to 6, "d" to 7)
        val number = (if (minor) minorMap else major)[pitch] ?: return null
        return "$number${if (minor) "A" else "B"}"
    }

    /**
     * Mix compatibility of two keys on the Camelot wheel, 0..1. Unknown keys are neutral (0.5),
     * so a known clash ranks below missing data.
     *
     * Same key 1.0; adjacent number or relative major/minor 0.9; diagonal (adjacent number and
     * mode change) 0.7; two steps 0.6; then 0.15 less per further step, down to 0.
     */
    fun harmonicScore(firstKey: String, secondKey: String): Double {
        val first = camelot(firstKey) ?: return 0.5
        val second = camelot(secondKey) ?: return 0.5
        if (first == second) return 1.0
        val (a, modeA) = first.dropLast(1).toInt() to first.last()
        val (b, modeB) = second.dropLast(1).toInt() to second.last()
        val wheelDistance = minOf((a - b + 12) % 12, (b - a + 12) % 12)
        val modeChange = modeA != modeB
        return when {
            !modeChange && wheelDistance == 1 -> 0.9
            modeChange && wheelDistance == 0 -> 0.9
            modeChange && wheelDistance == 1 -> 0.7
            else -> {
                val steps = wheelDistance + if (modeChange) 1 else 0
                (0.6 - 0.15 * (steps - 2)).coerceIn(0.0, 0.6)
            }
        }
    }

    /**
     * Smallest BPM change between two tempos when either may be played at half or double time,
     * or -1 when either tempo is unknown.
     */
    fun effectiveBpmDelta(bpmA: Int, bpmB: Int): Int {
        if (bpmA <= 0 || bpmB <= 0) return -1
        return minOf(abs(bpmA - bpmB), abs(bpmA * 2 - bpmB), abs(bpmA - bpmB * 2))
    }

    /**
     * Tempo compatibility, 0..1: 1.0 for the same (or half/double) tempo, falling linearly to 0
     * at a 25 % change. Unknown tempo scores 0.6.
     */
    fun tempoScore(bpmA: Int, bpmB: Int): Double {
        if (bpmA <= 0 || bpmB <= 0) return 0.6
        val a = bpmA.toDouble()
        val b = bpmB.toDouble()
        val relativeChange = listOf(a to b, a * 2 to b, a to b * 2).minOf { (x, y) -> abs(x - y) / maxOf(x, y) }
        return (1.0 - relativeChange / 0.25).coerceIn(0.0, 1.0)
    }

    /** Similarity of the tag-derived texture features (valence, danceability, acousticness), 0..1. */
    fun textureScore(x: SmlFeatureVector, y: SmlFeatureVector): Double =
        1.0 - (abs(x.valence - y.valence) + abs(x.danceability - y.danceability) + abs(x.acousticness - y.acousticness)) / 3.0

    fun flowScore(a: Song, b: Song, keyA: String = "", keyB: String = ""): Double {
        val x = features(a); val y = features(b)
        val mood = if (a.dominantMood.isNotBlank() && a.dominantMood.equals(b.dominantMood, true)) 1.0 else 0.5
        val energy = (1.0 - abs(x.energy - y.energy)).coerceIn(0.0, 1.0)
        return 0.25 * mood + 0.20 * energy + 0.20 * tempoScore(a.bpm, b.bpm) +
            0.25 * harmonicScore(keyA.ifBlank { a.musicalKey }, keyB.ifBlank { b.musicalKey }) +
            0.10 * textureScore(x, y)
    }

    /** Keeps candidates outside recent history when possible, falling back for small libraries. */
    fun filterRadioCandidateIds(candidateIds: List<String>, recentlyPlayedIds: Collection<String>): List<String> {
        val recent = recentlyPlayedIds.toHashSet()
        val fresh = candidateIds.filterNot(recent::contains)
        return fresh.ifEmpty { candidateIds }
    }

    /**
     * Drops candidates whose tempo jumps more than [MAX_RADIO_BPM_JUMP] from the current song
     * (half/double-time aware). Candidates without a BPM tag are kept. When nothing passes,
     * every candidate is returned so radio never stalls.
     */
    fun <T> filterTempoJumps(currentBpm: Int, candidates: List<T>, bpmOf: (T) -> Int): List<T> {
        if (currentBpm <= 0) return candidates
        val smooth = candidates.filter { candidate ->
            val delta = effectiveBpmDelta(currentBpm, bpmOf(candidate))
            delta < 0 || delta <= MAX_RADIO_BPM_JUMP
        }
        return smooth.ifEmpty { candidates }
    }

    /**
     * The candidates a pick is drawn from: those within [PICK_SCORE_MARGIN] of the best score,
     * best first, at most [PICK_POOL_SIZE]. Randomizing only within this pool keeps variety
     * without ever choosing a clearly worse transition.
     */
    fun <T> pickPool(scored: List<Pair<T, Double>>): List<T> {
        if (scored.isEmpty()) return emptyList()
        val sorted = scored.sortedByDescending { it.second }
        val best = sorted.first().second
        return sorted.takeWhile { it.second >= best - PICK_SCORE_MARGIN - 1e-12 }.take(PICK_POOL_SIZE).map { it.first }
    }

    fun radioScore(flow: Double, song: Song, localHour: Int, learnedBias: Double = 0.0, circadianEnabled: Boolean = true): Double {
        val target = targetEnergy(localHour, circadianEnabled)
        val energyMatch = 1.0 - abs(features(song).energy - target)
        return flow + 0.05 * energyMatch + learnedBias.coerceIn(-0.25, 0.25)
    }

    fun listeningBias(stats: ListeningStats, nowEpochMillis: Long): Double {
        val raw = ln(1.0 + stats.completions + 2.0 * stats.repeats) - 1.5 * ln(1.0 + stats.skips)
        if (stats.lastPlayedEpochMillis <= 0) return raw
        val ageDays = ((nowEpochMillis - stats.lastPlayedEpochMillis).coerceAtLeast(0L) / 86_400_000.0)
        return raw * 0.5.pow(ageDays / 30.0)
    }

    fun targetEnergy(localHour: Int, enabled: Boolean = true): Double {
        if (!enabled) return 0.55
        return when (localHour.coerceIn(0, 23)) {
            in 5..10 -> 0.55
            in 11..16 -> 0.65
            in 17..21 -> 0.60
            else -> 0.30
        }
    }

    private fun Double.pow(power: Double): Double = kotlin.math.exp(ln(this.coerceAtLeast(1e-12)) * power)
}
