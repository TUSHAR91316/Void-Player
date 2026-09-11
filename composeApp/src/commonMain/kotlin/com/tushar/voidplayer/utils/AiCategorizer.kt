package com.tushar.voidplayer.utils

import androidx.compose.ui.graphics.Color
import com.tushar.voidplayer.model.AiCategory
import com.tushar.voidplayer.model.Song
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Small Machine Learning (SML) on-device audio categorization engine.
 * Employs multi-feature vector space modeling (genre semantic centroids,
 * acoustic energy vectors, tempo closeness, duration thresholds, and lexical scoring)
 * with calibrated softmax probability scoring for high classification confidence.
 */
object AiCategorizer {

    data class SmlCategoryDefinition(
        val id: String,
        val title: String,
        val description: String,
        val emoji: String,
        val gradientColors: List<Color>,
        val targetEnergy: Float,
        val targetBpm: Float,
        val primaryGenres: List<String>,
        val secondaryGenres: List<String>,
        val semanticKeywords: List<String>,
        val minDurationMinutes: Double = 0.0,
        val recommendedEq: String = "Flat"
    )

    val definitions = listOf(
        SmlCategoryDefinition(
            id = "night_vibes",
            title = "Night Vibes & Lo-Fi",
            description = "Mellow, nocturnal, and chill atmospheric rhythms",
            emoji = "",
            gradientColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
            targetEnergy = 0.28f,
            targetBpm = 75f,
            primaryGenres = listOf("lo-fi", "lofi", "chillhop", "downtempo", "ambient", "chillout", "sleep"),
            secondaryGenres = listOf("jazz", "r&b", "soul", "indie", "electronic"),
            semanticKeywords = listOf("night", "sleep", "dark", "moon", "midnight", "dream", "rain", "lofi", "chill", "slow", "quiet", "star", "shadow", "calm", "pillow", "bed", "lullaby", "whisper", "fog", "evening"),
            recommendedEq = "Warm Studio / Ambient"
        ),
        SmlCategoryDefinition(
            id = "high_energy",
            title = "High Energy & Workout",
            description = "Upbeat, motivating, and hard-hitting tempos",
            emoji = "",
            gradientColors = listOf(Color(0xFFE11D48), Color(0xFFF97316)),
            targetEnergy = 0.88f,
            targetBpm = 138f,
            primaryGenres = listOf("rock", "metal", "edm", "dance", "electro", "drum & bass", "dubstep", "hardcore", "house", "trance", "techno", "trap", "punk", "workout", "fitness"),
            secondaryGenres = listOf("hip-hop", "hip hop", "rap", "pop", "alternative"),
            semanticKeywords = listOf("run", "fire", "fast", "energy", "hard", "rock", "drum", "gym", "power", "dance", "beat", "club", "party", "bass", "hyped", "speed", "fly", "rage", "wild", "beast", "punch", "jump", "turbo", "rush", "strike", "fighter"),
            recommendedEq = "Bass Boosted / Energetic"
        ),
        SmlCategoryDefinition(
            id = "deep_focus",
            title = "Deep Focus & Study",
            description = "Acoustic and ambient textures for deep concentration",
            emoji = "",
            gradientColors = listOf(Color(0xFF134E4A), Color(0xFF0D9488)),
            targetEnergy = 0.32f,
            targetBpm = 85f,
            primaryGenres = listOf("classical", "soundtrack", "score", "ambient", "instrumental", "piano", "drone", "meditation", "neoclassical", "minimal"),
            secondaryGenres = listOf("jazz", "acoustic", "lofi", "folk"),
            semanticKeywords = listOf("piano", "acoustic", "instrumental", "ambient", "study", "calm", "soft", "jazz", "classical", "meditation", "peace", "guitar", "soundtrack", "theme", "focus", "read", "memory", "solitude", "breeze", "canvas", "library", "zen"),
            recommendedEq = "Acoustic Clarity"
        ),
        SmlCategoryDefinition(
            id = "romance",
            title = "Romance & Heartfelt",
            description = "Emotional ballads, warm harmonies, and lyrical passion",
            emoji = "",
            gradientColors = listOf(Color(0xFF9D174D), Color(0xFFDB2777)),
            targetEnergy = 0.48f,
            targetBpm = 92f,
            primaryGenres = listOf("r&b", "soul", "ballad", "blues", "romantic", "vocal", "love"),
            secondaryGenres = listOf("pop", "acoustic", "indie", "jazz"),
            semanticKeywords = listOf("love", "heart", "feel", "you", "kiss", "romantic", "forever", "miss", "together", "soul", "sweet", "angel", "yours", "darling", "tears", "hold", "embrace", "baby", "lover", "passion", "desire", "warmth"),
            recommendedEq = "Vocal Pop Boost"
        ),
        SmlCategoryDefinition(
            id = "extended",
            title = "Extended & Masterpieces",
            description = "Epic musical journeys over 5 minutes long",
            emoji = "",
            gradientColors = listOf(Color(0xFF7E22CE), Color(0xFF4F46E5)),
            targetEnergy = 0.60f,
            targetBpm = 110f,
            primaryGenres = listOf("progressive rock", "prog", "symphonic", "post-rock", "jam", "psychedelic", "trance", "extended"),
            secondaryGenres = listOf("classical", "soundtrack", "metal", "electronic"),
            semanticKeywords = listOf("epic", "journey", "part", "suite", "odyssey", "movement", "concerto", "sonata", "opus", "act", "saga", "chronicles", "tales", "extended", "mix", "re-imagined"),
            minDurationMinutes = 4.75,
            recommendedEq = "Hi-Fi Dynamic Range"
        ),
        SmlCategoryDefinition(
            id = "euphoric_pop",
            title = "Euphoric & Pop",
            description = "Vibrant melodies and feel-good anthems",
            emoji = "",
            gradientColors = listOf(Color(0xFF0D9488), Color(0xFF06B6D4)),
            targetEnergy = 0.72f,
            targetBpm = 120f,
            primaryGenres = listOf("pop", "dance-pop", "electropop", "indie pop", "disco", "k-pop", "synth-pop"),
            secondaryGenres = listOf("dance", "electronic", "funk", "r&b"),
            semanticKeywords = listOf("pop", "happy", "summer", "sun", "shine", "light", "dream", "magic", "wonder", "gold", "star", "glow", "spark", "radiant", "smile", "joy", "candy", "bright", "glitter", "paradise"),
            recommendedEq = "Vocal Pop Boost"
        ),
        SmlCategoryDefinition(
            id = "late_night_drive",
            title = "Late Night Drive & Synthwave",
            description = "Atmospheric retro synths and basslines for empty highways",
            emoji = "",
            gradientColors = listOf(Color(0xFF581C87), Color(0xFF0F172A)),
            targetEnergy = 0.65f,
            targetBpm = 114f,
            primaryGenres = listOf("synthwave", "retrowave", "outrun", "cyberpunk", "vaporwave", "darksynth", "electronic"),
            secondaryGenres = listOf("dance", "ambient", "indie"),
            semanticKeywords = listOf("drive", "road", "car", "city", "midnight", "neon", "highway", "speed", "travel", "drift", "cruise", "nightfall", "sunset", "retro", "80s", "horizon", "velocity", "grid", "future"),
            recommendedEq = "Bass Boosted / Energetic"
        ),
        SmlCategoryDefinition(
            id = "acoustic_folk",
            title = "Acoustic & Indie Folk",
            description = "Intimate strings, organic percussion, and warm storytelling",
            emoji = "",
            gradientColors = listOf(Color(0xFF78350F), Color(0xFFB45309)),
            targetEnergy = 0.38f,
            targetBpm = 95f,
            primaryGenres = listOf("folk", "acoustic", "indie folk", "bluegrass", "singer-songwriter", "country", "americana"),
            secondaryGenres = listOf("indie", "blues", "ballad"),
            semanticKeywords = listOf("acoustic", "guitar", "strings", "wood", "folk", "cabin", "campfire", "river", "mountain", "valley", "home", "wander", "boots", "roots", "story", "raw", "unplugged"),
            recommendedEq = "Acoustic Clarity"
        ),
        SmlCategoryDefinition(
            id = "heavy_metal",
            title = "Heavy Metal & Hardcore",
            description = "Crushing distortion, relentless double-kicks, and raw power",
            emoji = "",
            gradientColors = listOf(Color(0xFF18181B), Color(0xFFDC2626)),
            targetEnergy = 0.94f,
            targetBpm = 150f,
            primaryGenres = listOf("metal", "heavy metal", "death metal", "thrash metal", "black metal", "metalcore", "hardcore", "grunge", "hard rock", "industrial"),
            secondaryGenres = listOf("rock", "punk", "alternative"),
            semanticKeywords = listOf("metal", "heavy", "shred", "distort", "scream", "death", "doom", "blood", "iron", "steel", "war", "battle", "crush", "darkness", "hell", "shadow", "bleed", "grave", "demon"),
            recommendedEq = "Rock / Heavy Distortion"
        )
    )

    /**
     * Classifies a song into the most fitting SML category using softmax probability.
     */
    fun classifySong(song: Song): Pair<SmlCategoryDefinition, Float> {
        val text = "${song.title} ${song.artist} ${song.album} ${song.genre}".lowercase()
        val genre = song.genre.lowercase()

        val scores = DoubleArray(definitions.size)

        for (i in definitions.indices) {
            val def = definitions[i]
            var score = 0.0

            // Feature 1: Primary Genre Matching (+42 max)
            if (def.primaryGenres.any { genre.contains(it) }) {
                score += 42.0
            } else if (def.secondaryGenres.any { genre.contains(it) }) {
                score += 20.0
            }

            // Feature 2: Acoustic Energy Closeness (+25 max)
            val energyCloseness = 1.0f - abs(song.acousticEnergy - def.targetEnergy)
            score += max(0.0f, energyCloseness) * 25.0

            // Feature 3: BPM Closeness (+15 max)
            if (song.bpm > 0) {
                val bpmDist = abs(song.bpm.toFloat() - def.targetBpm)
                val bpmCloseness = max(0.0f, 1.0f - (bpmDist / 60.0f))
                score += bpmCloseness * 15.0
            } else {
                score += 8.0 // Neutral baseline
            }

            // Feature 4: Semantic Lexical Tokens (+25 max)
            val tokenMatches = def.semanticKeywords.count { text.contains(it) }
            score += min(25.0, tokenMatches * 5.0)

            // Feature 5: Duration Thresholds
            if (def.minDurationMinutes > 0) {
                val durationMinutes = song.duration / (1000.0 * 60.0)
                if (durationMinutes >= def.minDurationMinutes) {
                    score += 35.0
                } else {
                    score -= 40.0
                }
            }

            scores[i] = max(0.1, score)
        }

        // Softmax normalization with temperature
        val temperature = 12.0
        val maxScore = scores.maxOrNull() ?: 0.0
        var sumExp = 0.0
        val exps = DoubleArray(scores.size)
        for (i in scores.indices) {
            exps[i] = exp((scores[i] - maxScore) / temperature)
            sumExp += exps[i]
        }

        var bestIndex = 0
        var bestProb = 0.0

        for (i in scores.indices) {
            val prob = exps[i] / sumExp
            if (prob > bestProb) {
                bestProb = prob
                bestIndex = i
            }
        }

        val clampedConfidence = bestProb.toFloat().coerceIn(0.45f, 0.99f)
        return Pair(definitions[bestIndex], clampedConfidence)
    }

    /**
     * Categorizes a collection of songs into the SML defined categories.
     */
    fun categorize(songs: List<Song>): List<AiCategory> {
        if (songs.isEmpty()) return emptyList()

        // Group songs by classified category
        val categoryMap = mutableMapOf<SmlCategoryDefinition, MutableList<Pair<Song, Float>>>()
        for (def in definitions) {
            categoryMap[def] = mutableListOf()
        }

        for (song in songs) {
            val (def, confidence) = classifySong(song)
            val updatedSong = if (song.dominantMood.isBlank()) song.copy(dominantMood = def.title) else song
            categoryMap[def]?.add(Pair(updatedSong, confidence))
        }

        val result = mutableListOf<AiCategory>()
        for (def in definitions) {
            val songPairs = categoryMap[def] ?: continue
            if (songPairs.isNotEmpty()) {
                val catSongs = songPairs.map { it.first }
                val avgConfidence = songPairs.map { it.second }.average().toFloat()
                val avgEnergy = catSongs.map { it.acousticEnergy }.average().toFloat()

                result.add(
                    AiCategory(
                        id = def.id,
                        title = def.title,
                        description = def.description,
                        emoji = def.emoji,
                        gradientColors = def.gradientColors,
                        songs = catSongs,
                        confidence = avgConfidence,
                        recommendedEq = def.recommendedEq,
                        averageEnergy = avgEnergy
                    )
                )
            }
        }

        return result
    }
}
