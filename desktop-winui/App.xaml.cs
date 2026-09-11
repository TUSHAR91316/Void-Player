using Microsoft.UI.Xaml;

namespace VoidPlayer.WinUI;

public partial class App : Application
{
    public static Window? CurrentWindow { get; private set; }

    public App()
    {
        UnhandledException += (s, e) =>
        {
            try
            {
                System.IO.File.WriteAllText(System.IO.Path.Combine(System.AppContext.BaseDirectory, "crash.log"),
                    $"Exception: {e.Exception}\r\nMessage: {e.Message}\r\nStack: {e.Exception?.StackTrace}");
            }
            catch { }
        };

        RequestedTheme = ApplicationTheme.Dark;
        InitializeComponent();
    }

    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        try
        {
            var window = new MainWindow();
            CurrentWindow = window;
            window.Activate();
        }
        catch (Exception ex)
        {
            System.IO.File.WriteAllText(System.IO.Path.Combine(System.AppContext.BaseDirectory, "crash.log"), ex.ToString());
            throw;
        }
    }
}
