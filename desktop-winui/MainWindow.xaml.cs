using System;
using System.IO;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;

namespace VoidPlayer.WinUI;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        Title = "Void Player - Hi-Fi Audio Experience (v2.3)";
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);

        // Load Icon & Logo from AppContext.BaseDirectory for reliable unpackaged resolution
        var iconPath = Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico");
        if (File.Exists(iconPath))
        {
            AppWindow.SetIcon(iconPath);
        }

        var logoPath = Path.Combine(AppContext.BaseDirectory, "Assets", "Square44x44Logo.png");
        if (File.Exists(logoPath))
        {
            TitleBarIconImage.Source = new BitmapImage(new Uri(logoPath));
        }

        // Navigate the root frame to the main page on startup.
        RootFrame.Navigate(typeof(MainPage));
        Closed += OnClosed;
    }

    private void OnClosed(object sender, WindowEventArgs args)
    {
        if (RootFrame.Content is MainPage mainPage)
        {
            mainPage.ViewModel.Dispose();
        }
    }
}
