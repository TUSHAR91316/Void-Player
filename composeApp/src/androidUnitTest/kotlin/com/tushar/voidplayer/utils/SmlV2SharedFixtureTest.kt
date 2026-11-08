package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SmlV2SharedFixtureTest {
    @Test
    fun sharedJsonFixturesMatchTheKotlinPort() {
        fixtureObjects().forEach { fields ->
            when (fields.getValue("kind")) {
                "camelot" -> assertEquals(
                    fields.getValue("expected").takeUnless { it == "none" },
                    SmlV2.camelot(fields.getValue("a")),
                    "camelot(${fields["a"]})"
                )
                "bpmDelta" -> assertEquals(fields.int("expected"), SmlV2.effectiveBpmDelta(fields.int("bpmA"), fields.int("bpmB")))
                "tempoFilter" -> {
                    val candidates = fields.getValue("candidates").split('|').map { entry ->
                        entry.substringBefore(':') to entry.substringAfter(':').toInt()
                    }
                    val kept = SmlV2.filterTempoJumps(fields.int("currentBpm"), candidates) { it.second }
                    assertEquals(fields.getValue("expectedIds").split('|'), kept.map { it.first })
                }
                "pickPool" -> {
                    val scored = fields.getValue("scores").split('|').map { entry ->
                        entry.substringBefore(':') to entry.substringAfter(':').toDouble()
                    }
                    assertEquals(fields.getValue("expectedIds").split('|'), SmlV2.pickPool(scored))
                }
                "harmonic" -> assertEquals(fields.getValue("expected").toDouble(), SmlV2.harmonicScore(fields.getValue("a"), fields.getValue("b")), 1e-8, "harmonic(${fields["a"]}, ${fields["b"]})")
                "tempo" -> assertEquals(fields.getValue("expected").toDouble(), SmlV2.tempoScore(fields.int("bpmA"), fields.int("bpmB")), 1e-8)
                "target" -> assertEquals(fields.getValue("expected").toDouble(), SmlV2.targetEnergy(fields.int("hour")), 1e-8)
                "features" -> {
                    val features = SmlV2.features(fields.song("title", "genre", "", "", fields.double("energy")))
                    assertEquals(fields.double("energy"), features.energy, 1e-6)
                    assertEquals(fields.double("valence"), features.valence, 1e-8)
                    assertEquals(fields.double("danceability"), features.danceability, 1e-8)
                    assertEquals(fields.double("acousticness"), features.acousticness, 1e-8)
                    assertEquals(fields.double("tempoNorm"), features.tempoNorm, 1e-8)
                    assertEquals(fields.double("durationNorm"), features.durationNorm, 1e-8)
                }
                "flow" -> {
                    val a = fields.song("aTitle", "aGenre", "aMood", "aKey", fields.double("aEnergy"), "aBpm", "aDurationMs")
                    val b = fields.song("bTitle", "bGenre", "bMood", "bKey", fields.double("bEnergy"), "bBpm", "bDurationMs")
                    assertEquals(fields.double("expected"), SmlV2.flowScore(a, b), 1e-6)
                }
                "radioScore" -> {
                    val song = fields.song("title", "genre", "", "", fields.double("energy"), "bpm", "durationMs")
                    assertEquals(fields.double("expected"), SmlV2.radioScore(fields.double("flow"), song, fields.int("hour"), fields.double("bias")), 1e-8)
                }
                "bias" -> {
                    val stats = ListeningStats(
                        completions = fields.int("completions"),
                        skips = fields.int("skips"),
                        repeats = fields.int("repeats"),
                        lastPlayedEpochMillis = fields.long("lastPlayed")
                    )
                    assertEquals(fields.double("expected"), SmlV2.listeningBias(stats, fields.long("now")), 1e-8)
                }
                "dedupe" -> {
                    val candidates = fields.getValue("candidates").split('|')
                    val recent = fields.getValue("recent").split('|')
                    assertEquals(fields.getValue("expectedIds").split('|'), SmlV2.filterRadioCandidateIds(candidates, recent))
                }
                else -> error("Unknown SML fixture: ${fields["kind"]}")
            }
        }
    }

    private fun Map<String, String>.song(titleKey: String, genreKey: String, moodKey: String, keyKey: String, energy: Double, bpmKey: String = "bpm", durationKey: String = "durationMs"): Song =
        Song(
            id = 1L,
            title = getValue(titleKey),
            artist = "fixture artist",
            album = getOrDefault("album", "fixture album"),
            duration = getValue(durationKey).toLong(),
            uri = "fixture://track",
            genre = getOrDefault(genreKey, ""),
            bpm = getValue(bpmKey).toInt(),
            musicalKey = getOrDefault(keyKey, ""),
            acousticEnergy = energy.toFloat(),
            dominantMood = getOrDefault(moodKey, "")
        )

    private fun Map<String, String>.int(key: String) = getValue(key).toInt()
    private fun Map<String, String>.long(key: String) = getValue(key).toLong()
    private fun Map<String, String>.double(key: String) = getValue(key).toDouble()

    private fun fixtureObjects(): List<Map<String, String>> {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val fixture = directory?.resolve("test-fixtures/sml-v2.json")
            if (fixture?.isFile == true) {
                val text = fixture.readText()
                val objectPattern = Regex("\\{([^{}]*)}")
                val fieldPattern = Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|[^,]+)")
                return objectPattern.findAll(text).map { match ->
                    fieldPattern.findAll(match.groupValues[1]).associate { field ->
                        val value = field.groupValues[2].trim()
                        field.groupValues[1] to if (value.startsWith('"')) value.substring(1, value.length - 1) else value
                    }
                }.toList()
            }
            directory = directory?.parentFile
        }
        error("Could not find shared test-fixtures/sml-v2.json from ${System.getProperty("user.dir")}")
    }
}

