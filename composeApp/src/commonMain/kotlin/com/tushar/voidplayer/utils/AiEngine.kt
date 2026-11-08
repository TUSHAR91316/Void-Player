package com.tushar.voidplayer.utils

import androidx.compose.ui.graphics.Color
import com.tushar.voidplayer.model.AiCategory
import com.tushar.voidplayer.model.Song
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class AiInsightsData(
    val dominantVibe: String,
    val vibeEmoji: String,
    val personalityTitle: String,
    val personalityDescription: String,
    val totalHoursMinutes: String,
    val topArtist: String,
    val recommendedEq: String,
    val averageAcousticEnergy: Float = 0.5f,
    val topGenreSummary: String = "Acoustic, Electronic, Pop",
    val vibeBreakdown: List<Pair<String, Int>> // Vibe Name to Percentage
)

/**
 * Small Machine Learning (SML) audio feature extraction, harmonic flow
 * sequencing, and intelligence profiling engine.
 */
object AiEngine {

    /**
     * Calculates on-device acoustic energy based on genre, tempo, and duration dynamics.
     * Yields a normalized intensity score from 0.08 (calm/ambient) to 0.98 (maximum drive).
     */
    fun calculateAcousticEnergy(genre: String, bpm: Int, durationMs: Long): Float {
        val genreLower = genre.lowercase()
        var energy = 0.55f

        // 1. Genre centroid baseline
        when {
            genreLower.containsAny("metal", "hardcore", "death metal", "thrash", "punk", "industrial", "edm", "dubstep", "hardstyle") -> {
                energy = 0.92f
            }
            genreLower.containsAny("rock", "dance", "electro", "house", "trance", "techno", "trap", "workout") -> {
                energy = 0.82f
            }
            genreLower.containsAny("pop", "hip-hop", "hip hop", "rap", "synthwave", "retrowave", "funk") -> {
                energy = 0.68f
            }
            genreLower.containsAny("r&b", "soul", "reggae", "folk", "country", "ballad", "acoustic") -> {
                energy = 0.48f
            }
            genreLower.containsAny("lo-fi", "lofi", "chillhop", "ambient", "sleep", "meditation", "classical", "piano", "downtempo") -> {
                energy = 0.25f
            }
        }

        // 2. Tagged BPM adjustment
        if (bpm > 0) {
            val bpmNorm = ((bpm - 60f) / 100f).coerceIn(0.0f, 1.0f)
            energy = (energy * 0.6f) + (bpmNorm * 0.4f)
        }

        // 3. Duration curve adjustment
        val durationMinutes = durationMs / (1000f * 60f)
        if (durationMinutes > 6.0f) {
            energy -= 0.08f // Long tracks tend to be progressive or ambient
        } else if (durationMinutes in 0.5f..2.5f) {
            energy += 0.06f // Short tracks tend to be punchy singles
        }

        return energy.coerceIn(0.08f, 0.98f)
    }

    /**
     * Computes harmonic transition flow score between two tracks for AI DJ Smart Flow.
     * Higher score = smoother, more harmonious continuous transition.
     */
    fun computeFlowScore(a: Song, b: Song): Float {
        return SmlV2.flowScore(a, b, a.musicalKey, b.musicalKey).toFloat()
    }

    /**
     * Generates a harmonically ordered sequence of songs using flow transition optimization.
     */
    fun generateHarmonicQueue(sourceSongs: List<Song>, startingSong: Song? = null): List<Song> {
        val pool = sourceSongs.toMutableList()
        if (pool.size <= 1) return pool

        val result = mutableListOf<Song>()
        // Match by track identity: startingSong may be a copy with different metadata.
        val startIdx = startingSong?.let { s -> pool.indexOfFirst { it.isSameTrack(s) } } ?: -1
        val current = if (startIdx >= 0) pool.removeAt(startIdx) else pool.removeAt(0)
        result.add(current)

        var lastSong = current
        while (pool.isNotEmpty()) {
            var bestIdx = 0
            var bestScore = -1.0f

            for (i in pool.indices) {
                val score = computeFlowScore(lastSong, pool[i])
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }

            val bestNext = pool.removeAt(bestIdx)
            result.add(bestNext)
            lastSong = bestNext
        }

        return result
    }

    /**
     * Deep SML listener intelligence profiling and personality computation.
     */
    fun generateInsights(songs: List<Song>): AiInsightsData {
        if (songs.isEmpty()) {
            return AiInsightsData(
                dominantVibe = "Silence",
                vibeEmoji = "",
                personalityTitle = "The Clean Slate",
                personalityDescription = "Load music folders to unlock Void SML audio intelligence and personality profiling.",
                totalHoursMinutes = "0m",
                topArtist = "None",
                recommendedEq = "Flat / Studio",
                averageAcousticEnergy = 0.5f,
                topGenreSummary = "Acoustic, Electronic, Pop",
                vibeBreakdown = emptyList()
            )
        }

        val totalDurationMs = songs.sumOf { it.duration }
        val hours = totalDurationMs / (1000 * 60 * 60)
        val mins = (totalDurationMs / (1000 * 60)) % 60
        val durationStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val categories = AiCategorizer.categorize(songs)
        val categoryCounts = categories.map { it.title to it.songs.size }.sortedByDescending { it.second }
        val totalCategoryTracks = categoryCounts.sumOf { it.second }.coerceAtLeast(1)

        val breakdown = categoryCounts.take(4).map {
            it.first to ((it.second * 100) / totalCategoryTracks)
        }

        val topCategory = categories.maxByOrNull { it.songs.size }

        val dominantVibe = topCategory?.title ?: "Eclectic Vibe"
        val vibeEmoji = ""

        val (personality, description, eq) = when {
            dominantVibe.contains("Night", ignoreCase = true) -> Triple(
                "The Midnight Wanderer",
                "Your library leans heavily toward mellow, nocturnal, and atmospheric sounds.",
                "Warm Studio / Ambient"
            )
            dominantVibe.contains("Energy", ignoreCase = true) -> Triple(
                "The High-Drive Dynamo",
                "Packed with high-tempo beats, rock power, and motivating workout rhythms.",
                "Bass Boosted / Energetic"
            )
            dominantVibe.contains("Focus", ignoreCase = true) -> Triple(
                "The Analytical Zen",
                "Full of peaceful acoustic, ambient, and instrumental concentration textures.",
                "Acoustic Clarity"
            )
            dominantVibe.contains("Romance", ignoreCase = true) -> Triple(
                "The Soulful Romantic",
                "Rich with emotional vocals, heartfelt harmonies, and expressive melodies.",
                "Vocal Pop Boost"
            )
            dominantVibe.contains("Extended", ignoreCase = true) -> Triple(
                "The Symphonic Explorer",
                "You love long, intricate musical compositions and prog-rock masterpieces.",
                "Hi-Fi Dynamic Range"
            )
            dominantVibe.contains("Drive", ignoreCase = true) -> Triple(
                "The Cyberpunk Pilot",
                "Cruising through neon-lit synths, retro electronic basslines, and nocturnal soundscapes.",
                "Bass Boosted / Energetic"
            )
            dominantVibe.contains("Metal", ignoreCase = true) -> Triple(
                "The Heavy Metal Juggernaut",
                "Driven by heavy distortion, relentless drumming, and aggressive riffs.",
                "Rock / Heavy Distortion"
            )
            else -> Triple(
                "The Eclectic Connoisseur",
                "A versatile sonic palette spanning across diverse moods, tempos, and genres.",
                "Dynamic Normalization"
            )
        }

        val topArtist = songs.groupBy { it.artist }
            .filter { it.key != "Unknown Artist" }
            .maxByOrNull { it.value.size }?.key ?: "Various Artists"

        val avgEnergy = songs.map { it.acousticEnergy }.average().toFloat()

        // Extract top genres
        val topGenres = songs
            .filter { it.genre.isNotBlank() }
            .flatMap { it.genre.split(',', '/', ';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val genreSummary = if (topGenres.isNotEmpty()) topGenres.joinToString(", ") else "Acoustic, Electronic, Pop"

        return AiInsightsData(
            dominantVibe = dominantVibe,
            vibeEmoji = vibeEmoji,
            personalityTitle = personality,
            personalityDescription = description,
            totalHoursMinutes = durationStr,
            topArtist = topArtist,
            recommendedEq = eq,
            averageAcousticEnergy = avgEnergy,
            topGenreSummary = genreSummary,
            vibeBreakdown = breakdown
        )
    }

    /**
     * AI DJ Smart Flow: Intelligently calculates the next most harmonious track
     * using SML harmonic transition scoring.
     */
    fun getAiDjNextSong(
        currentSong: Song,
        allSongs: List<Song>,
        recentlyPlayedIds: Collection<Long> = emptyList(),
        random: Random = Random.Default,
        localHour: Int = 12,
        listeningBiases: Map<Long, Double> = emptyMap()
    ): Song? {
        val candidates = allSongs.filter { !it.isSameTrack(currentSong) }
        if (candidates.isEmpty()) return null

        // Exclude songs already played in recent history if alternatives exist
        val freshIds = SmlV2.filterRadioCandidateIds(
            candidates.map { it.id.toString() },
            recentlyPlayedIds.map(Long::toString)
        ).toSet()
        val unplayed = candidates.filter { it.id.toString() in freshIds }
        // No tempo jump over SmlV2.MAX_RADIO_BPM_JUMP when both songs carry a BPM tag.
        val eligibleCandidates = SmlV2.filterTempoJumps(currentSong.bpm, unplayed) { it.bpm }

        // Library songs carry no mood until classified, which would flatten the mood term.
        val current = withResolvedMood(currentSong)
        val scored = eligibleCandidates.map { candidate ->
            candidate to SmlV2.radioScore(
                SmlV2.flowScore(current, withResolvedMood(candidate)), candidate, localHour,
                learnedBias = listeningBiases[candidate.id] ?: 0.0
            )
        }

        // Vary only among near-equal picks, so a clearly worse transition is never chosen.
        return SmlV2.pickPool(scored).random(random)
    }

    private fun withResolvedMood(song: Song): Song =
        if (song.dominantMood.isNotBlank()) song
        else song.copy(dominantMood = AiCategorizer.classifySong(song).first.title)

    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { this.contains(it, ignoreCase = true) }
    }
}
