using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Platform.Storage;
using SimDeck.Companion.App.ViewModels;

namespace SimDeck.Companion.App.Views;

public partial class SettingsView : UserControl
{
    public SettingsView() => InitializeComponent();

    /// <summary>Picks BeamNG's user folder (the one the Launcher's Manage User Folder opens) when it isn't found by itself.</summary>
    async void ChooseBeamNgFolder(object? sender, RoutedEventArgs e)
    {
        if (DataContext is not MainViewModel vm || TopLevel.GetTopLevel(this)?.StorageProvider is not { } sp) return;
        try
        {
            var picked = await sp.OpenFolderPickerAsync(new FolderPickerOpenOptions
            {
                Title = "BeamNG.drive user folder (the one that holds the mods folder)",
                AllowMultiple = false,
            });
            if (picked.Count > 0 && picked[0].TryGetLocalPath() is { } path)
            {
                // Picking the mods folder itself works too.
                if (string.Equals(Path.GetFileName(path.TrimEnd('\\', '/')), "mods", StringComparison.OrdinalIgnoreCase))
                    path = Path.GetDirectoryName(path.TrimEnd('\\', '/')) ?? path;
                vm.SetBeamNgUserFolder(path);
            }
        }
        catch (Exception ex) { vm.Host.Log("warn", $"Could not open the folder picker: {ex.Message}"); }
    }
}
