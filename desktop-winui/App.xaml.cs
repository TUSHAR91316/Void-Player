using Microsoft.UI.Xaml;
using System.Linq;
using Windows.ApplicationModel.Activation;
using Windows.Storage;
using Microsoft.Windows.AppLifecycle;

namespace VoidPlayer.WinUI;

public partial class App : Application
{
    public static Window? CurrentWindow { get; private set; }
    private readonly AppInstance _singleInstance;

    public App()
    {
        _singleInstance = AppInstance.FindOrRegisterForKey("void-player-main");
        if (_singleInstance.IsCurrent)
            _singleInstance.Activated += OnRedirectedActivation;
        UnhandledException += (s, e) =>
        {
            try
            {
                WriteCrashLog($"Exception: {e.Exception}\r\nMessage: {e.Message}\r\nStack: {e.Exception?.StackTrace}");
            }
            catch { }
        };

        RequestedTheme = ApplicationTheme.Dark;
        InitializeComponent();
    }

    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        if (RedirectIfSecondary()) return;
        try
        {
            var window = new MainWindow();
            CurrentWindow = window;
            var activation = AppInstance.GetCurrent().GetActivatedEventArgs();
            if (activation.Kind == ExtendedActivationKind.File && activation.Data is IFileActivatedEventArgs fileArgs)
                window.QueueFileActivation(fileArgs.Files.OfType<StorageFile>().Select(file => file.Path));
            window.Activate();
        }
        catch (Exception ex)
        {
            WriteCrashLog(ex.ToString());
            throw;
        }
    }

    private bool RedirectIfSecondary()
    {
        if (_singleInstance.IsCurrent) return false;
        try
        {
            var activation = AppInstance.GetCurrent().GetActivatedEventArgs();
            _singleInstance.RedirectActivationToAsync(activation).AsTask().GetAwaiter().GetResult();
        }
        catch (Exception ex) { WriteCrashLog($"Activation redirection failed: {ex}"); }
        Exit();
        return true;
    }

    private void OnRedirectedActivation(object? sender, AppActivationArguments args)
    {
        if (args.Kind != ExtendedActivationKind.File || args.Data is not IFileActivatedEventArgs fileArgs) return;
        var paths = fileArgs.Files.OfType<StorageFile>().Select(file => file.Path).ToArray();
        if (CurrentWindow is MainWindow window)
            window.DispatcherQueue.TryEnqueue(() => { window.QueueFileActivation(paths); window.Activate(); });
    }

    private static void WriteCrashLog(string message)
    {
        var directory = System.IO.Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "VoidPlayer",
            "logs");
        System.IO.Directory.CreateDirectory(directory);
        System.IO.File.AppendAllText(
            System.IO.Path.Combine(directory, "crash.log"),
            $"[{DateTimeOffset.Now:O}] {message}{Environment.NewLine}{Environment.NewLine}");
    }
}
