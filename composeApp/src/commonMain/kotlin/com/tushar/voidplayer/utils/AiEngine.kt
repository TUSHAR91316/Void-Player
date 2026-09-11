package com.tushar.voidplayer.utils

import androidx.compose.ui.graphics.Color
import com.tushar.voidplayer.model.AiCategory
import com.tushar.voidplayer.model.Song
import kotlin.math.abs
import kotlin.math.max

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
        // 1. Mood continuity
        val moodScore = if (a.dominantMood.isNotBlank() && b.dominantMood.isNotBlank() &&
            a.dominantMood.equals(b.dominantMood, ignoreCase = true)
        ) 1.0f else 0.5f

        // 2. Energy flow continuity (avoids violent acoustic energy spikes)
        val energyDelta = abs(a.acousticEnergy - b.acousticEnergy)
        val energyScore = max(0.0f, 1.0f - (energyDelta * 1.5f))

        // 3. Tempo compatibility
        var tempoScore = 0.7f
        if (a.bpm > 0 && b.bpm > 0) {
            val bpmDelta = abs(a.bpm - b.bpm).toFloat()
            tempoScore = max(0.0f, 1.0f - (bpmDelta / 60.0f))
        }

        return (moodScore * 0.45f) + (energyScore * 0.35f) + (tempoScore * 0.20f)
    }

    /**
     * Generates a harmonically ordered sequence of songs using flow transition optimization.
     */
    fun generateHarmonicQueue(sourceSongs: List<Song>, startingSong: Song? = null): List<Song> {
        val pool = sourceSongs.toMutableList()
        if (pool.size <= 1) return pool

        val result = mutableListOf<Song>()
        val current = startingSong ?: pool.first()
        result.add(current)
        pool.remove(current)

        var lastSong = current
        while (pool.isNotEmpty()) {
            var bestNext = pool.first()
            var bestScore = -1.0f

            for (candidate in pool) {
                val score = computeFlowScore(lastSong, candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestNext = candidate
                }
            }

            result.add(bestNext)
            pool.remove(bestNext)
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
    fun getAiDjNextSong(currentSong: Song, allSongs: List<Song>, queue: List<Song>): Song? {
        if (allSongs.size <= 2) return null
        val candidates = allSongs.filter { it.id != currentSong.id }
        if (candidates.isEmpty()) return null

        // Exclude songs already played in recent history if alternatives exist
        val recentIds = queue.takeLast(minOf(5, allSongs.size - 2)).map { it.id }.toSet()
        val eligibleCandidates = candidates.filter { it.id !in recentIds }.ifEmpty { candidates }

        // Compute SML harmonic flow score for each eligible candidate
        val scored = eligibleCandidates.map { candidate ->
            val flowScore = computeFlowScore(currentSong, candidate)
            candidate to flowScore
        }

        val bestMatches = scored.sortedByDescending { it.second }
        // Pick among top 3 matches with slight randomization for freshness
        val topPicks = bestMatches.take(3).map { it.first }
        return topPicks.randomOrNull() ?: eligibleCandidates.randomOrNull()
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { this.contains(it, ignoreCase = true) }
    }
}
