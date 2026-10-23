using Avalonia.Controls;
using Avalonia.Input;
using SimDeck.Companion.App.ViewModels;

namespace SimDeck.Companion.App.Views;

public partial class LayoutsView : UserControl
{
    public LayoutsView()
    {
        InitializeComponent();
        Canvas.SelectionRequested += ids => Vm?.Select(ids);
        Canvas.MoveRequested += (id, x, y) => Vm?.MoveTo(id, x, y);
        Canvas.ResizeRequested += (id, w, h) => Vm?.ResizeTo(id, w, h);
        Canvas.KeyDown += OnCanvasKey;
    }

    LayoutsViewModel? Vm => DataContext as LayoutsViewModel;

    void OnCanvasKey(object? sender, KeyEventArgs e)
    {
        if (Vm is not { } vm) return;
        bool ctrl = e.KeyModifiers.HasFlag(KeyModifiers.Control) || e.KeyModifiers.HasFlag(KeyModifiers.Meta);
        switch (e.Key)
        {
            case Key.Delete or Key.Back: vm.DeleteCommand.Execute(null); break;
            case Key.C when ctrl: vm.CopyCommand.Execute(null); break;
            case Key.V when ctrl: vm.PasteCommand.Execute(null); break;
            case Key.D when ctrl: vm.DuplicateCommand.Execute(null); break;
            case Key.Z when ctrl && e.KeyModifiers.HasFlag(KeyModifiers.Shift): vm.RedoCommand.Execute(null); break;
            case Key.Z when ctrl: vm.UndoCommand.Execute(null); break;
            case Key.Y when ctrl: vm.RedoCommand.Execute(null); break;
            case Key.Left: vm.Nudge(-1, 0); break;
            case Key.Right: vm.Nudge(1, 0); break;
            case Key.Up: vm.Nudge(0, -1); break;
            case Key.Down: vm.Nudge(0, 1); break;
            case Key.Escape: vm.Select(Array.Empty<string>()); break;
            default: return;
        }
        e.Handled = true;
    }
}
