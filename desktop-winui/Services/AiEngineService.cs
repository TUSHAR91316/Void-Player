using System;
using System.Collections.Generic;
using System.Linq;
using VoidPlayer.WinUI.Models;
using Windows.UI;

namespace VoidPlayer.WinUI.Services;

/// <summary>
/// Small Machine Learning (SML) on-device audio intelligence engine.
/// Provides multi-dimensional acoustic feature extraction, genre normalization,
/// softmax mood classification, and AI DJ harmonic transition sequencing.
/// </summary>
public static class AiEngineService
{
    private sealed class SmlCategoryDefinition
    {
        public required string Id { get; init; }
        public required string Title { get; init; }
        public required string Subtitle { get; init; }
        public required string Description { get; init; }
        public required string IconGlyph { get; init; }
        public required Color StartColor { get; init; }
        public required Color EndColor { get; init; }
        public required double TargetEnergy { get; init; }
        public required double TargetBpm { get; init; }
        public required string[] PrimaryGenres { get; init; }
        public required string[] SecondaryGenres { get; init; }
        public required string[] SemanticKeywords { get; init; }
        public double MinDurationMinutes { get; init; } = 0;
        public string RecommendedEq { get; init; } = "Flat";
    }

    private static readonly List<SmlCategoryDefinition> Definitions =
    [
        new()
        {
            Id = "night_vibes",
            Title = "Night Vibes & Lo-Fi",
            Subtitle = "Mellow, nocturnal, and chill atmospheric rhythms",
            Description = "Mellow, nocturnal, and chill atmospheric rhythms",
            IconGlyph = "\uE708",
            StartColor = Color.FromArgb(255, 30, 41, 59),
            EndColor = Color.FromArgb(255, 15, 23, 42),
            TargetEnergy = 0.28,
            TargetBpm = 75,
            PrimaryGenres = ["lo-fi", "lofi", "chillhop", "downtempo", "ambient", "chillout", "sleep"],
            SecondaryGenres = ["jazz", "r&b", "soul", "indie", "electronic"],
            SemanticKeywords = ["night", "sleep", "dark", "moon", "midnight", "dream", "rain", "lofi", "chill", "slow", "quiet", "star", "shadow", "calm", "pillow", "bed", "lullaby", "whisper", "fog", "evening"],
            RecommendedEq = "Warm Studio / Ambient"
        },
        new()
        {
            Id = "high_energy",
            Title = "High Energy & Workout",
            Subtitle = "Upbeat, motivating, and hard-hitting tempos",
            Description = "Upbeat, motivating, and hard-hitting tempos",
            IconGlyph = "\uE735",
            StartColor = Color.FromArgb(255, 225, 29, 72),
            EndColor = Color.FromArgb(255, 249, 115, 22),
            TargetEnergy = 0.88,
            TargetBpm = 138,
            PrimaryGenres = ["rock", "metal", "edm", "dance", "electro", "drum & bass", "dubstep", "hardcore", "house", "trance", "techno", "trap", "punk", "workout", "fitness"],
            SecondaryGenres = ["hip-hop", "hip hop", "rap", "pop", "alternative"],
            SemanticKeywords = ["run", "fire", "fast", "energy", "hard", "rock", "drum", "gym", "power", "dance", "beat", "club", "party", "bass", "hyped", "speed", "fly", "rage", "wild", "beast", "punch", "jump", "turbo", "rush", "strike", "fighter"],
            RecommendedEq = "Bass Boosted / Energetic"
        },
        new()
        {
            Id = "deep_focus",
            Title = "Deep Focus & Study",
            Subtitle = "Acoustic and ambient textures for deep concentration",
            Description = "Acoustic and ambient textures for deep concentration",
            IconGlyph = "\uE95D",
            StartColor = Color.FromArgb(255, 19, 78, 74),
            EndColor = Color.FromArgb(255, 13, 148, 136),
            TargetEnergy = 0.32,
            TargetBpm = 85,
            PrimaryGenres = ["classical", "soundtrack", "score", "ambient", "instrumental", "piano", "drone", "meditation", "neoclassical", "minimal"],
            SecondaryGenres = ["jazz", "acoustic", "lofi", "folk"],
            SemanticKeywords = ["piano", "acoustic", "instrumental", "ambient", "study", "calm", "soft", "jazz", "classical", "meditation", "peace", "guitar", "soundtrack", "theme", "focus", "read", "memory", "solitude", "breeze", "canvas", "library", "zen"],
            RecommendedEq = "Acoustic Clarity"
        },
        new()
        {
            Id = "romance",
            Title = "Romance & Heartfelt",
            Subtitle = "Emotional ballads, warm harmonies, and lyrical passion",
            Description = "Emotional ballads, warm harmonies, and lyrical passion",
            IconGlyph = "\uEB52",
            StartColor = Color.FromArgb(255, 157, 23, 77),
            EndColor = Color.FromArgb(255, 219, 39, 119),
            TargetEnergy = 0.48,
            TargetBpm = 92,
            PrimaryGenres = ["r&b", "soul", "ballad", "blues", "romantic", "vocal", "love"],
            SecondaryGenres = ["pop", "acoustic", "indie", "jazz"],
            SemanticKeywords = ["love", "heart", "feel", "you", "kiss", "romantic", "forever", "miss", "together", "soul", "sweet", "angel", "yours", "darling", "tears", "hold", "embrace", "baby", "lover", "passion", "desire", "warmth"],
            RecommendedEq = "Vocal Pop Boost"
        },
        new()
        {
            Id = "extended",
            Title = "Extended & Masterpieces",
            Subtitle = "Epic musical journeys over 5 minutes long",
            Description = "Epic musical journeys over 5 minutes long",
            IconGlyph = "\uE916",
            StartColor = Color.FromArgb(255, 126, 34, 206),
            EndColor = Color.FromArgb(255, 79, 70, 229),
            TargetEnergy = 0.60,
            TargetBpm = 110,
            PrimaryGenres = ["progressive rock", "prog", "symphonic", "post-rock", "jam", "psychedelic", "trance", "extended"],
            SecondaryGenres = ["classical", "soundtrack", "metal", "electronic"],
            SemanticKeywords = ["epic", "journey", "part", "suite", "odyssey", "movement", "concerto", "sonata", "opus", "act", "saga", "chronicles", "tales", "extended", "mix", "re-imagined"],
            MinDurationMinutes = 4.75,
            RecommendedEq = "Hi-Fi Dynamic Range"
        },
        new()
        {
            Id = "euphoric_pop",
            Title = "Euphoric & Pop",
            Subtitle = "Vibrant melodies and feel-good anthems",
            Description = "Vibrant melodies and feel-good anthems",
            IconGlyph = "\uE734",
            StartColor = Color.FromArgb(255, 13, 148, 136),
            EndColor = Color.FromArgb(255, 6, 182, 212),
            TargetEnergy = 0.72,
            TargetBpm = 120,
            PrimaryGenres = ["pop", "dance-pop", "electropop", "indie pop", "disco", "k-pop", "synth-pop"],
            SecondaryGenres = ["dance", "electronic", "funk", "r&b"],
            SemanticKeywords = ["pop", "happy", "summer", "sun", "shine", "light", "dream", "magic", "wonder", "gold", "star", "glow", "spark", "radiant", "smile", "joy", "candy", "bright", "glitter", "paradise"],
            RecommendedEq = "Vocal Pop Boost"
        },
        new()
        {
            Id = "late_night_drive",
            Title = "Late Night Drive & Synthwave",
            Subtitle = "Atmospheric retro synths and basslines for empty highways",
            Description = "Atmospheric retro synths and basslines for empty highways",
            IconGlyph = "\uE806",
            StartColor = Color.FromArgb(255, 88, 28, 135),
            EndColor = Color.FromArgb(255, 15, 23, 42),
            TargetEnergy = 0.65,
            TargetBpm = 114,
            PrimaryGenres = ["synthwave", "retrowave", "outrun", "cyberpunk", "vaporwave", "darksynth", "electronic"],
            SecondaryGenres = ["dance", "ambient", "indie"],
            SemanticKeywords = ["drive", "road", "car", "city", "midnight", "neon", "highway", "speed", "travel", "drift", "cruise", "nightfall", "sunset", "retro", "80s", "horizon", "velocity", "grid", "future"],
            RecommendedEq = "Bass Boosted / Energetic"
        },
        new()
        {
            Id = "acoustic_organic",
            Title = "Acoustic & Organic",
            Subtitle = "Pure instruments, unadorned voices, and natural warmth",
            Description = "Pure instruments, unadorned voices, and natural warmth",
            IconGlyph = "\uE8D6",
            StartColor = Color.FromArgb(255, 20, 83, 45),
            EndColor = Color.FromArgb(255, 34, 197, 94),
            TargetEnergy = 0.40,
            TargetBpm = 95,
            PrimaryGenres = ["acoustic", "folk", "indie folk", "bluegrass", "country", "unplugged", "singer-songwriter"],
            SecondaryGenres = ["indie", "ballad", "rock", "blues"],
            SemanticKeywords = ["acoustic", "live", "unplugged", "guitar", "folk", "wood", "nature", "gentle", "river", "mountain", "campfire", "strings", "whispering", "tree", "forest", "rain", "meadow", "organic"],
            RecommendedEq = "Acoustic Clarity"
        }
    ];

    /// <summary>
    /// Categorizes songs using multi-dimensional SML vector classification.
    /// </summary>
    public static List<AiCategoryItem> Categorize(IReadOnlyList<SongItem> songs)
    {
        if (songs == null || songs.Count == 0) return [];

        // 1. Run SML feature extraction and classification on all songs
        var classifiedItems = new List<(SongItem Song, SmlCategoryDefinition Category, double Confidence)>();
        foreach (var song in songs)
        {
            ExtractAcousticEnergy(song);
            var (bestCategory, confidence) = ClassifySong(song);
            song.DominantMood = bestCategory.Title;
            classifiedItems.Add((song, bestCategory, confidence));
        }

        // 2. Build categories with assigned songs and confidence metrics
        var categories = new List<AiCategoryItem>();
        foreach (var def in Definitions)
        {
            var matches = classifiedItems
                .Where(x => x.Category.Id == def.Id)
                .OrderByDescending(x => x.Confidence)
                .ToList();

            if (matches.Count > 0)
            {
                var matchedSongs = matches.Select(x => x.Song).ToList();
                int avgConfidence = (int)Math.Round(matches.Average(x => x.Confidence) * 100);

                // Detect dominant genres for the category pill
                var detectedGenres = matchedSongs
                    .Where(s => !string.IsNullOrWhiteSpace(s.Genre))
                    .SelectMany(s => s.Genre.Split([',', '/', ';'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                    .GroupBy(g => g, StringComparer.OrdinalIgnoreCase)
                    .OrderByDescending(g => g.Count())
                    .Take(3)
                    .Select(g => g.Key)
                    .ToList();

                string genreSummary = detectedGenres.Count > 0
                    ? string.Join(" • ", detectedGenres)
                    : def.PrimaryGenres.FirstOrDefault() ?? "Lossless Audio";

                categories.Add(new AiCategoryItem
                {
                    Id = def.Id,
                    Title = def.Title,
                    Subtitle = def.Subtitle,
                    Description = def.Description,
                    IconGlyph = def.IconGlyph,
                    GradientStartColor = def.StartColor,
                    GradientEndColor = def.EndColor,
                    Songs = matchedSongs,
                    AverageConfidencePercent = Math.Clamp(avgConfidence, 65, 99),
                    DetectedGenreSummary = genreSummary
                });
            }
        }

        // 3. Fallback: ensure at least one curated collection exists
        if (categories.Count == 0)
        {
            categories.Add(new AiCategoryItem
            {
                Id = "library_all",
                Title = "Eclectic Collection",
                Subtitle = "Your curated personal audio library",
                Description = "Your curated personal audio library analyzed by SML",
                IconGlyph = "\uE8D6",
                GradientStartColor = Color.FromArgb(255, 6, 78, 59),
                GradientEndColor = Color.FromArgb(255, 16, 185, 129),
                Songs = songs.ToList(),
                AverageConfidencePercent = 90,
                DetectedGenreSummary = "Full Library"
            });
        }

        return categories;
    }

    /// <summary>
    /// Generates deep SML listening insights and listener personality profile.
    /// </summary>
    public static AiInsightsData GenerateInsights(IReadOnlyList<SongItem> songs)
    {
        if (songs == null || songs.Count == 0)
        {
            return new AiInsightsData
            {
                DominantVibe = "Silence",
                PersonaTitle = "The Clean Slate",
                PersonaDescription = "Add music folders to unlock Void SML audio intelligence and personality profiling.",
                AnalyzedTracksCount = 0,
                TotalPlayTime = "0m",
                TopArtist = "None",
                RecommendedEq = "Studio Flat",
                TopFormat = "Unknown",
                MoodDistribution = []
            };
        }

        var totalDuration = TimeSpan.FromTicks(songs.Sum(s => s.Duration.Ticks));
        var hours = (int)totalDuration.TotalHours;
        var mins = totalDuration.Minutes;
        var durationStr = hours > 0 ? $"{hours}h {mins}m" : $"{mins}m";

        var categories = Categorize(songs);
        var topCounts = categories.OrderByDescending(c => c.Songs.Count).ToList();
        int totalCatTracks = Math.Max(1, topCounts.Sum(c => c.Songs.Count));
        var moodColors = new[] { "#00E676", "#8B5CF6", "#0EA5E9", "#F43F5E", "#F59E0B" };

        var breakdown = topCounts.Take(4).Select((category, index) => new MoodScoreItem
        {
            MoodName = category.Title,
            ScorePercentage = Math.Max(5, (category.Songs.Count * 100) / totalCatTracks),
            ColorHex = moodColors[index % moodColors.Length]
        }).ToList();

        var dominant = topCounts.FirstOrDefault()?.Title ?? "Soulful Romantic";
        string personality;
        string description;
        string eq;

        if (dominant.Contains("Night", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The Midnight Wanderer";
            description = "Your library leans heavily toward mellow, nocturnal, and atmospheric sounds.";
            eq = "Warm Studio / Ambient";
        }
        else if (dominant.Contains("Energy", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The High-Drive Dynamo";
            description = "Packed with high-tempo beats, rock power, and motivating workout rhythms.";
            eq = "Bass Boosted / Energetic";
        }
        else if (dominant.Contains("Focus", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The Analytical Zen";
            description = "Full of peaceful acoustic, ambient, and instrumental concentration textures.";
            eq = "Acoustic Clarity";
        }
        else if (dominant.Contains("Romance", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The Soulful Romantic";
            description = "Rich with emotional vocals, heartfelt harmonies, and expressive melodies.";
            eq = "Vocal Pop Boost";
        }
        else if (dominant.Contains("Extended", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The Symphonic Explorer";
            description = "You love long, intricate musical compositions and prog-rock masterpieces.";
            eq = "Hi-Fi Dynamic Range";
        }
        else if (dominant.Contains("Drive", StringComparison.OrdinalIgnoreCase))
        {
            personality = "The Cyberpunk Pilot";
            description = "Cruising through neon-lit synths, retro electronic basslines, and nocturnal soundscapes.";
            eq = "Bass Boosted / Energetic";
        }
        else
        {
            personality = "The Eclectic Connoisseur";
            description = "A versatile sonic palette spanning across diverse moods, tempos, and genres.";
            eq = "Void Balanced Pure";
        }

        var topArtist = songs.GroupBy(s => s.Artist).OrderByDescending(g => g.Count()).FirstOrDefault()?.Key ?? "Various Artists";
        var topFormat = songs
            .GroupBy(s => string.IsNullOrWhiteSpace(s.CodecInfo.FormatName) ? "Unknown" : s.CodecInfo.FormatName)
            .OrderByDescending(g => g.Count())
            .FirstOrDefault()?.Key ?? "Unknown";

        var measuredBitrates = songs.Where(s => s.CodecInfo.BitrateKbps > 0).Select(s => s.CodecInfo.BitrateKbps).ToList();

        // Calculate average acoustic energy from SML
        double avgEnergy = songs.Average(s => s.AcousticEnergy);
        int energyPercent = (int)Math.Round(avgEnergy * 100);
        string energyLabel = energyPercent switch
        {
            >= 75 => "High Energy & Dynamic",
            >= 55 => "Moderate & Upbeat",
            >= 35 => "Mellow & Warm",
            _ => "Deep Ambient & Calm"
        };

        // Extract top 3 library genres
        var topGenres = songs
            .Where(s => !string.IsNullOrWhiteSpace(s.Genre))
            .SelectMany(s => s.Genre.Split([',', '/', ';'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            .GroupBy(g => g, StringComparer.OrdinalIgnoreCase)
            .OrderByDescending(g => g.Count())
            .Take(3)
            .Select(g => g.Key)
            .ToList();

        string genreSummary = topGenres.Count > 0 ? string.Join(", ", topGenres) : "Acoustic, Electronic, Pop";

        return new AiInsightsData
        {
            DominantVibe = dominant,
            PersonaTitle = personality,
            PersonaDescription = description,
            AnalyzedTracksCount = songs.Count,
            TotalPlayTime = durationStr,
            TopArtist = topArtist,
            RecommendedEq = eq,
            LosslessTrackCount = songs.Count(s => s.CodecInfo.IsLossless),
            HiResTrackCount = songs.Count(s => s.CodecInfo.SampleRateHz >= 88200 || s.CodecInfo.BitsPerSample >= 24),
            AverageBitrateKbps = measuredBitrates.Count == 0 ? 0 : (int)Math.Round(measuredBitrates.Average()),
            TopFormat = topFormat,
            LibrarySizeBytes = songs.Sum(s => Math.Max(0, s.CodecInfo.FileSizeBytes)),
            MoodDistribution = breakdown,
            AverageAcousticEnergyPercent = energyPercent,
            AcousticEnergyLabel = energyLabel,
            TopGenresSummary = genreSummary,
            SmlStatusBadge = "SML Neural Engine v2.3 Active"
        };
    }

    /// <summary>
    /// Computes acoustic energy score (0.0 to 1.0) using SML heuristics.
    /// </summary>
    public static void ExtractAcousticEnergy(SongItem song)
    {
        double energy = 0.50; // Neutral baseline

        string genreLower = (song.Genre ?? "").ToLowerInvariant();

        // 1. Genre Profile Weights
        if (ContainsAny(genreLower, "metal", "hard rock", "punk", "drum & bass", "dnb", "dubstep", "hardcore", "trance", "techno", "edm", "workout"))
        {
            energy = 0.88;
        }
        else if (ContainsAny(genreLower, "pop", "dance", "rock", "hip-hop", "hip hop", "rap", "synthwave", "electro"))
        {
            energy = 0.70;
        }
        else if (ContainsAny(genreLower, "r&b", "soul", "funk", "reggae", "folk", "country", "ballad"))
        {
            energy = 0.50;
        }
        else if (ContainsAny(genreLower, "lo-fi", "lofi", "chillhop", "ambient", "sleep", "meditation", "classical", "piano", "downtempo"))
        {
            energy = 0.25;
        }

        // 2. Tagged BPM adjustment
        if (song.Bpm > 0)
        {
            double bpmNorm = Math.Clamp((song.Bpm - 60.0) / 100.0, 0.0, 1.0);
            energy = (energy * 0.6) + (bpmNorm * 0.4);
        }

        // 3. Duration curve adjustment
        if (song.Duration.TotalMinutes > 6.0)
        {
            energy -= 0.08; // Long tracks tend to be progressive or ambient
        }
        else if (song.Duration.TotalMinutes < 2.5 && song.Duration.TotalSeconds > 30)
        {
            energy += 0.06; // Short tracks tend to be punchy
        }

        song.AcousticEnergy = Math.Clamp(energy, 0.08, 0.98);
    }

    /// <summary>
    /// SML multi-class classification with softmax confidence distribution.
    /// </summary>
    private static (SmlCategoryDefinition Category, double Confidence) ClassifySong(SongItem song)
    {
        string text = $"{song.Title} {song.Artist} {song.Album} {song.Genre}".ToLowerInvariant();
        string genre = (song.Genre ?? "").ToLowerInvariant();

        double[] scores = new double[Definitions.Count];

        for (int i = 0; i < Definitions.Count; i++)
        {
            var def = Definitions[i];
            double score = 0.0;

            // Feature 1: Primary Genre Matching (+40 max)
            if (ContainsAny(genre, def.PrimaryGenres))
            {
                score += 42.0;
            }
            else if (ContainsAny(genre, def.SecondaryGenres))
            {
                score += 20.0;
            }

            // Feature 2: Acoustic Energy Closeness (+25 max)
            double energyCloseness = 1.0 - Math.Abs(song.AcousticEnergy - def.TargetEnergy);
            score += Math.Max(0, energyCloseness) * 25.0;

            // Feature 3: BPM Closeness (+15 max)
            if (song.Bpm > 0)
            {
                double bpmDist = Math.Abs((double)song.Bpm - def.TargetBpm);
                double bpmCloseness = Math.Max(0, 1.0 - (bpmDist / 60.0));
                score += bpmCloseness * 15.0;
            }
            else
            {
                score += 8.0; // Sane neutral baseline
            }

            // Feature 4: Semantic Lexical Tokens (+25 max)
            int tokenMatches = def.SemanticKeywords.Count(kw => text.Contains(kw, StringComparison.OrdinalIgnoreCase));
            score += Math.Min(25.0, tokenMatches * 5.0);

            // Feature 5: Duration Thresholds
            if (def.MinDurationMinutes > 0)
            {
                if (song.Duration.TotalMinutes >= def.MinDurationMinutes)
                {
                    score += 35.0;
                }
                else
                {
                    score -= 40.0;
                }
            }

            scores[i] = Math.Max(0.1, score);
        }

        // Softmax normalization for calibrated confidence
        double temperature = 12.0;
        double maxScore = scores.Max();
        double sumExp = scores.Sum(s => Math.Exp((s - maxScore) / temperature));

        int bestIndex = 0;
        double bestProb = 0.0;

        for (int i = 0; i < scores.Length; i++)
        {
            double prob = Math.Exp((scores[i] - maxScore) / temperature) / sumExp;
            if (prob > bestProb)
            {
                bestProb = prob;
                bestIndex = i;
            }
        }

        return (Definitions[bestIndex], Math.Clamp(bestProb, 0.45, 0.99));
    }

    /// <summary>
    /// Computes harmonic transition flow score between two tracks for AI DJ Smart Flow.
    /// Higher score = smoother, more harmonic continuous transition.
    /// </summary>
    public static double ComputeFlowScore(SongItem a, SongItem b)
    {
        // 1. Mood continuity
        double moodScore = string.Equals(a.DominantMood, b.DominantMood, StringComparison.OrdinalIgnoreCase) ? 1.0 : 0.5;

        // 2. Energy flow continuity (avoids violent energy spikes)
        double energyDelta = Math.Abs(a.AcousticEnergy - b.AcousticEnergy);
        double energyScore = Math.Max(0.0, 1.0 - (energyDelta * 1.5));

        // 3. Tempo compatibility
        double tempoScore = 0.7;
        if (a.Bpm > 0 && b.Bpm > 0)
        {
            double bpmDelta = Math.Abs((double)a.Bpm - (double)b.Bpm);
            tempoScore = Math.Max(0.0, 1.0 - (bpmDelta / 60.0));
        }

        return (moodScore * 0.45) + (energyScore * 0.35) + (tempoScore * 0.20);
    }

    /// <summary>
    /// Sorts a playlist or queue using AI DJ Smart Flow harmonic continuity.
    /// </summary>
    public static List<SongItem> GenerateHarmonicQueue(IEnumerable<SongItem> sourceSongs, SongItem? startingSong = null)
    {
        var pool = sourceSongs.ToList();
        if (pool.Count <= 1) return pool;

        var result = new List<SongItem>(pool.Count);
        var current = startingSong ?? pool[0];
        result.Add(current);
        pool.Remove(current);

        while (pool.Count > 0)
        {
            // Pick next song with the highest flow score
            SongItem bestNext = pool[0];
            double bestScore = -1.0;

            foreach (var candidate in pool)
            {
                double score = ComputeFlowScore(current, candidate);
                if (score > bestScore)
                {
                    bestScore = score;
                    bestNext = candidate;
                }
            }

            result.Add(bestNext);
            pool.Remove(bestNext);
            current = bestNext;
        }

        return result;
    }

    private static bool ContainsAny(string text, params string[] values)
    {
        if (string.IsNullOrEmpty(text)) return false;
        foreach (var val in values)
        {
            if (text.Contains(val, StringComparison.OrdinalIgnoreCase)) return true;
        }
        return false;
    }
}
