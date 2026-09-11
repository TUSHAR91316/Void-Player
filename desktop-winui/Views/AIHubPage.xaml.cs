using System;
using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using VoidPlayer.WinUI.Models;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Views;

public sealed partial class AIHubPage : Page, INotifyPropertyChanged
{
    public MainViewModel? ViewModel { get; private set; }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string TotalPlaytimeBadgeText => ViewModel?.AiInsights?.TotalPlayTime ?? "9h 34m";

    public string PersonaDisplayTitle
    {
        get
        {
            if (ViewModel?.AiInsights == null) return "The Soulful Romantic";
            return ViewModel.AiInsights.PersonaTitle;
        }
    }

    public string SuggestedEqText => $"AI Suggested EQ: {ViewModel?.AiInsights?.RecommendedEq ?? "Vocal Pop Boost"}";

    public string PersonaDescriptionText => ViewModel?.AiInsights?.PersonaDescription ?? "Add a music folder to generate library insights.";

    public string LibraryProfileText => ViewModel?.AiInsights == null
        ? "No library analyzed"
        : $"{ViewModel.AiInsights.QualitySummary} | {ViewModel.AiInsights.StreamSummary} | {ViewModel.AiInsights.LibrarySizeText}";

    public string SmlStatusBadgeText => ViewModel?.AiInsights?.SmlStatusBadge ?? "SML Neural Engine v2.3 Active";
    public string AcousticEnergyBadgeText => ViewModel?.AiInsights != null
        ? $"⚡ Acoustic Energy: {ViewModel.AiInsights.AverageAcousticEnergyPercent}% • {ViewModel.AiInsights.AcousticEnergyLabel}"
        : "⚡ Acoustic Energy: Analyzing...";
    public string TopGenresBadgeText => ViewModel?.AiInsights?.TopGenresSummary ?? "Diverse genres";

    public string CategoryCountText => $"{ViewModel?.AiCategories.Count ?? 0} Collections";

    public AiCategoryItem? SelectedCategory { get; private set; }

    public Visibility CategoryDetailsVisibility => SelectedCategory == null ? Visibility.Collapsed : Visibility.Visible;

    public string SubtitleStatsText
    {
        get
        {
            if (ViewModel?.AiInsights == null) return "Awaiting library scan...";
            return $"Based on {ViewModel.AiInsights.AnalyzedTracksCount} tracks • {ViewModel.AiInsights.TotalPlayTime} total playtime";
        }
    }

    public AIHubPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        if (e.Parameter is MainViewModel vm)
        {
            ViewModel = vm;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(ViewModel)));
            ViewModel.PropertyChanged += (_, args) =>
            {
                if (args.PropertyName == nameof(ViewModel.AiInsights) ||
                    args.PropertyName == nameof(ViewModel.DynamicAccentBrush))
                {
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SubtitleStatsText)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TotalPlaytimeBadgeText)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(PersonaDisplayTitle)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SuggestedEqText)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(PersonaDescriptionText)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LibraryProfileText)));
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CategoryCountText)));
                }
            };
        }
    }

    private void OnCategoryClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is AiCategoryItem cat)
        {
            SelectedCategory = cat;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SelectedCategory)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CategoryDetailsVisibility)));
        }
    }

    private void OnPlayCategoryClick(object sender, RoutedEventArgs e)
    {
        if (SelectedCategory != null) ViewModel?.PlayAiCategory(SelectedCategory);
    }

    private void OnPlayCategorySongClick(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement element && element.DataContext is SongItem song)
        {
            ViewModel?.PlaySong(song);
        }
    }

    private void OnRefreshClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.RefreshAiHub();
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SubtitleStatsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TotalPlaytimeBadgeText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(PersonaDisplayTitle)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SuggestedEqText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(PersonaDescriptionText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LibraryProfileText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CategoryCountText)));
    }

    private void OnApplySuggestedEqClick(object sender, RoutedEventArgs e)
    {
        string recommendation = ViewModel?.AiInsights?.RecommendedEq ?? string.Empty;
        double[] bands = recommendation.Contains("Bass", StringComparison.OrdinalIgnoreCase)
            ? [8, 6, 2, 0, -1]
            : recommendation.Contains("Vocal", StringComparison.OrdinalIgnoreCase)
                ? [-2, 1, 5, 4, 2]
                : recommendation.Contains("Acoustic", StringComparison.OrdinalIgnoreCase)
                    ? [3, 2, 1, 2, 3]
                    : recommendation.Contains("Ambient", StringComparison.OrdinalIgnoreCase)
                        ? [2, 1, 0, 2, 3]
                        : [0, 0, 0, 0, 0];

        ViewModel?.Player.SetEqualizerBands(bands);
    }
}
