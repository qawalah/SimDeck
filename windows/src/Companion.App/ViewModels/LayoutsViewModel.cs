using System.Collections.ObjectModel;
using System.Text.Json.Nodes;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SimDeck.Companion.Engine;
using SimDeck.Companion.Layout;

namespace SimDeck.Companion.App.ViewModels;

public sealed class PhoneChoice
{
    public required string Key { get; init; }
    public required string DeviceId { get; init; }
    public required string Name { get; init; }
    public override string ToString() => Name;
}

public sealed class ProfileChoice
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public string Game { get; init; } = "";
    public bool BuiltIn { get; init; }
    public int Pages { get; init; }
    public string Detail => (BuiltIn ? "Built in · " : "") + (Pages == 1 ? "1 page" : $"{Pages} pages");
    public override string ToString() => Name;
}

public sealed class PageTab
{
    public required int Index { get; init; }
    public required string Name { get; init; }
    public bool Current { get; init; }
}

public sealed class Choice
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public override string ToString() => Name;
}

/// <summary>
/// The PC layout editor (SimDeck 1.3, docs/ROADMAP.md): pick a connected phone and one of its profiles, edit the pages
/// on a big screen with a mouse, and save the profile back to the phone. The phone stays the owner of its profiles:
/// it refuses a save while its own editor has the same profile open (see AppViewModel.saveFromPc).
/// </summary>
public sealed partial class LayoutsViewModel : ObservableObject
{
    readonly CompanionHost _host;
    readonly Dictionary<string, JsonArray> _lists = new(); // per device id, the last list the phone sent
    string? _pendingId;
    string? _discardArmed;
    bool _loading;

    // A clipboard shared by every profile and phone the editor opens, so controls and pages move between them.
    static JsonArray? _clip;
    static JsonObject? _pageClip;

    public ObservableCollection<PhoneChoice> PhoneChoices { get; } = new();
    public ObservableCollection<ProfileChoice> Profiles { get; } = new();
    public ObservableCollection<PageTab> PageTabs { get; } = new();
    public ObservableCollection<string> SkinChoices { get; } = new();

    public IReadOnlyList<string> AddTypes { get; } = LayoutCatalog.Types;
    public IReadOnlyList<Choice> AddTypeChoices { get; } = LayoutCatalog.Types.Select(t => new Choice { Id = t, Name = LayoutCatalog.TypeName(t) }).ToList();
    public IReadOnlyList<string> Accents { get; } = LayoutCatalog.Accents;
    public IReadOnlyList<Choice> ThemeChoices { get; } = LayoutCatalog.Themes.Select(t => new Choice { Id = t.Id, Name = t.Name }).ToList();
    public IReadOnlyList<Choice> PageThemeChoices { get; } =
        new[] { new Choice { Id = "", Name = "Profile's theme" } }.Concat(LayoutCatalog.Themes.Select(t => new Choice { Id = t.Id, Name = t.Name })).ToList();

    [ObservableProperty] private PhoneChoice? _selectedPhone;
    [ObservableProperty] private ProfileChoice? _selectedProfile;
    [ObservableProperty] private LayoutDoc? _doc;
    [ObservableProperty] private int _pageIndex;
    [ObservableProperty] private int _version;
    [ObservableProperty] private IReadOnlyList<string> _selected = Array.Empty<string>();
    [ObservableProperty] private string _status = "";
    [ObservableProperty] private bool _statusBad;
    [ObservableProperty] private Choice _addType;

    // Profile and page fields
    [ObservableProperty] private string _profileName = "";
    [ObservableProperty] private Choice? _profileTheme;
    [ObservableProperty] private bool _themeFollowsVehicle;
    [ObservableProperty] private string _pageName = "";
    [ObservableProperty] private decimal? _pageColumns;
    [ObservableProperty] private decimal? _pageRows;
    [ObservableProperty] private Choice? _pageTheme;

    // Inspector (one selected control)
    [ObservableProperty] private string _selType = "";
    [ObservableProperty] private string _selLabel = "";
    [ObservableProperty] private string _selIcon = "";
    [ObservableProperty] private string? _selAccent;
    [ObservableProperty] private string? _selSkin;
    [ObservableProperty] private decimal? _selW;
    [ObservableProperty] private decimal? _selH;
    [ObservableProperty] private string _selFeedback = "";
    [ObservableProperty] private string _selJson = "";
    [ObservableProperty] private string? _jsonError;

    public bool HasPhone => SelectedPhone != null;
    public bool NoPhone => PhoneChoices.Count == 0;
    public bool HasDoc => Doc != null;
    public bool NoDoc => Doc == null;
    public bool HasSelection => Selected.Count > 0;
    public bool SingleSelection => Selected.Count == 1;
    public bool MultiSelection => Selected.Count > 1;
    public string SelectionText => Selected.Count switch { 0 => "", 1 => "1 control selected", var n => $"{n} controls selected" };
    public bool Dirty => Doc?.Dirty == true;
    public bool CanUndo => Doc?.CanUndo == true;
    public bool CanRedo => Doc?.CanRedo == true;
    public bool HasClip => _clip is { Count: > 0 };
    public bool HasPageClip => _pageClip != null;
    public string ClipText => _clip is { Count: > 0 } c ? (c.Count == 1 ? "1 control copied" : $"{c.Count} controls copied") : "";
    public bool IsAutoPage => Doc != null && Doc.IsAuto(PageIndex);
    public string Title => Doc == null ? "Layouts" : Doc.Name + (Doc.Dirty ? " •" : "");

    public LayoutsViewModel(CompanionHost host)
    {
        _host = host;
        _addType = AddTypeChoices[0];
        host.SessionsChanged += () => Ui(RefreshPhones);
        host.ProfilesListed += (s, list) => Ui(() => OnListed(s, list));
        host.ProfileReceived += (s, p) => Ui(() => OnReceived(s, p));
        host.ProfileSaved += (s, id, ok, message) => Ui(() => OnSaved(s, id, ok, message));
        RefreshPhones();
    }

    static void Ui(Action a)
    {
        if (Dispatcher.UIThread.CheckAccess()) a();
        else Dispatcher.UIThread.Post(a);
    }

    void Say(string text, bool bad = false) { Status = text; StatusBad = bad; }

    // ---------------------------------------------------------------- phones and profiles

    void RefreshPhones()
    {
        var keep = SelectedPhone?.DeviceId;
        var sessions = _host.Sessions.OrderBy(s => s.DeviceName).ToList();
        PhoneChoices.Clear();
        foreach (var s in sessions) PhoneChoices.Add(new PhoneChoice { Key = s.SessionKey, DeviceId = s.DeviceId, Name = s.DeviceName });
        SelectedPhone = PhoneChoices.FirstOrDefault(p => p.DeviceId == keep) ?? PhoneChoices.FirstOrDefault();
        OnPropertyChanged(nameof(NoPhone));
        if (Doc != null && SelectedPhone == null) Say("The phone disconnected. Your changes are kept here; save them when it reconnects.", bad: true);
    }

    partial void OnSelectedPhoneChanged(PhoneChoice? value)
    {
        OnPropertyChanged(nameof(HasPhone));
        ShowList();
        if (value != null && !_lists.ContainsKey(value.DeviceId)) _host.RequestProfiles(value.Key);
    }

    void OnListed(PhoneSession s, JsonArray list)
    {
        _lists[s.DeviceId] = list;
        if (SelectedPhone == null) RefreshPhones();
        if (SelectedPhone?.DeviceId == s.DeviceId) ShowList();
    }

    void ShowList()
    {
        var keep = SelectedProfile?.Id ?? Doc?.Id;
        _loading = true;
        Profiles.Clear();
        if (SelectedPhone != null && _lists.TryGetValue(SelectedPhone.DeviceId, out var list))
            foreach (var o in list.OfType<JsonObject>())
                Profiles.Add(new ProfileChoice
                {
                    Id = (string?)o["id"] ?? "", Name = (string?)o["name"] ?? "Profile", Game = (string?)o["game"] ?? "",
                    BuiltIn = o["builtIn"] is JsonValue b && b.TryGetValue<bool>(out var bi) && bi, Pages = LayoutDoc.Num(o, "pages", 1),
                });
        SelectedProfile = Profiles.FirstOrDefault(p => p.Id == keep);
        _loading = false;
    }

    partial void OnSelectedProfileChanged(ProfileChoice? value)
    {
        if (_loading || value == null || value.Id == Doc?.Id) return;
        Open(value.Id);
    }

    [RelayCommand]
    void Open(string id)
    {
        if (SelectedPhone == null) return;
        if (Doc is { Dirty: true } d && d.Id != id && _discardArmed != id)
        {
            _discardArmed = id;
            Say($"{d.Name} has unsaved changes. Save them, or pick the other profile again to drop them.", bad: true);
            _loading = true; SelectedProfile = Profiles.FirstOrDefault(p => p.Id == d.Id); _loading = false;
            return;
        }
        _discardArmed = null;
        _pendingId = id;
        if (_host.RequestProfile(SelectedPhone.Key, id)) Say("Loading from the phone…");
        else Say("The phone is not connected.", bad: true);
    }

    void OnReceived(PhoneSession s, JsonObject profile)
    {
        var id = (string?)profile["id"];
        if (SelectedPhone?.DeviceId != s.DeviceId || id == null || id != _pendingId) return;
        _pendingId = null;
        Doc = new LayoutDoc(profile, s.SessionKey);
        Say(Doc.PageCount == 0 ? "This profile has no pages." : $"Editing {Doc.Name}. Changes go to the phone when you press Save.");
    }

    void OnSaved(PhoneSession s, string id, bool ok, string message)
    {
        if (Doc == null || id != Doc.Id) return;
        if (ok)
        {
            Doc.MarkSaved();
            Changed(reloadInspector: false);
            Say(string.IsNullOrWhiteSpace(message) ? "Saved on the phone." : message);
        }
        else Say(string.IsNullOrWhiteSpace(message) ? "The phone did not save it." : message, bad: true);
    }

    [RelayCommand]
    void Refresh()
    {
        if (SelectedPhone == null) return;
        _host.RequestProfiles(SelectedPhone.Key);
        if (Doc != null && !Doc.Dirty) { _pendingId = Doc.Id; _host.RequestProfile(SelectedPhone.Key, Doc.Id); }
    }

    [RelayCommand]
    void Save()
    {
        if (Doc == null) return;
        if (SelectedPhone == null) { Say("Connect the phone to save.", bad: true); return; }
        if (_host.PutProfile(SelectedPhone.Key, Doc.ForPhone())) Say("Sending to the phone…");
        else Say("The phone is not connected.", bad: true);
    }

    [RelayCommand]
    void Revert()
    {
        if (Doc == null || SelectedPhone == null) return;
        _pendingId = Doc.Id;
        Doc = null;
        _host.RequestProfile(SelectedPhone.Key, _pendingId);
        Say("Loading the phone's copy again…");
    }

    // ---------------------------------------------------------------- document state

    partial void OnDocChanged(LayoutDoc? value)
    {
        PageIndex = 0;
        Selected = Array.Empty<string>();
        OnPropertyChanged(nameof(HasDoc));
        OnPropertyChanged(nameof(NoDoc));
        Changed();
    }

    partial void OnPageIndexChanged(int value)
    {
        Selected = Array.Empty<string>();
        LoadPageFields();
        if (Doc != null)
        {
            var tabs = PageTabs.Select(t => new PageTab { Index = t.Index, Name = t.Name, Current = t.Index == value }).ToList();
            PageTabs.Clear();
            foreach (var t in tabs) PageTabs.Add(t);
        }
        OnPropertyChanged(nameof(IsAutoPage));
    }

    [RelayCommand]
    void ShowPage(int index) => PageIndex = index;

    partial void OnSelectedChanged(IReadOnlyList<string> value)
    {
        OnPropertyChanged(nameof(HasSelection));
        OnPropertyChanged(nameof(SingleSelection));
        OnPropertyChanged(nameof(MultiSelection));
        OnPropertyChanged(nameof(SelectionText));
        LoadInspector();
    }

    /// <summary>After any edit: redraw, refresh the tabs and the fields, and the undo and save state.</summary>
    void Changed(bool reloadInspector = true)
    {
        Version++;
        _loading = true;
        PageTabs.Clear();
        if (Doc != null)
            for (int i = 0; i < Doc.PageCount; i++) PageTabs.Add(new PageTab { Index = i, Name = Doc.PageName(i), Current = i == PageIndex });
        if (Doc != null && PageIndex >= Doc.PageCount) PageIndex = Math.Max(0, Doc.PageCount - 1);
        if (Doc != null) Selected = Selected.Where(id => Doc.PageCount > 0 && Doc.Find(PageIndex, id) != null).ToList();
        ProfileName = Doc?.Name ?? "";
        ProfileTheme = ThemeChoices.FirstOrDefault(t => t.Id == Doc?.Theme) ?? ThemeChoices[0];
        ThemeFollowsVehicle = Doc?.Profile["themeFollowsVehicle"] is JsonValue f && f.TryGetValue<bool>(out var fv) && fv;
        _loading = false;
        LoadPageFields();
        if (reloadInspector) LoadInspector();
        foreach (var p in new[] { nameof(Dirty), nameof(CanUndo), nameof(CanRedo), nameof(Title), nameof(IsAutoPage), nameof(HasClip), nameof(HasPageClip), nameof(ClipText) })
            OnPropertyChanged(p);
    }

    void LoadPageFields()
    {
        if (Doc == null || Doc.PageCount == 0) return;
        _loading = true;
        PageName = Doc.PageName(PageIndex);
        PageColumns = Doc.Columns(PageIndex);
        PageRows = Doc.Rows(PageIndex);
        var own = (string?)Doc.PageAt(PageIndex)["theme"] ?? "";
        PageTheme = PageThemeChoices.FirstOrDefault(t => t.Id == own) ?? PageThemeChoices[0];
        _loading = false;
    }

    JsonObject? One => Doc != null && Selected.Count == 1 ? Doc.Find(PageIndex, Selected[0]) : null;

    void LoadInspector()
    {
        _loading = true;
        JsonError = null;
        SkinChoices.Clear();
        if (One is { } c)
        {
            SelType = LayoutCatalog.TypeName((string?)c["type"] ?? "");
            SelLabel = (string?)c["label"] ?? "";
            SelIcon = (string?)c["icon"] ?? "";
            SelAccent = (string?)c["accent"] ?? "neutral";
            SkinChoices.Add("auto");
            if (LayoutCatalog.SkinsByType.TryGetValue((string?)c["type"] ?? "", out var skins)) foreach (var s in skins) SkinChoices.Add(s);
            SelSkin = (string?)c["skin"] ?? "auto";
            SelW = LayoutDoc.Num(c, "w", 1);
            SelH = LayoutDoc.Num(c, "h", 1);
            SelFeedback = (string?)c["feedbackKey"] ?? "";
            SelJson = LayoutDoc.Pretty(c);
        }
        else
        {
            SelType = SelLabel = SelIcon = SelFeedback = SelJson = "";
            SelAccent = SelSkin = null;
            SelW = SelH = null;
        }
        _loading = false;
    }

    // Field edits write straight into the document (one undo step each).
    void Field(string key, JsonNode? value)
    {
        if (_loading || Doc == null || Selected.Count != 1) return;
        Doc.SetField(PageIndex, Selected[0], key, value);
        Changed();
    }

    static JsonNode? Str(string? s) => string.IsNullOrWhiteSpace(s) ? null : JsonValue.Create(s.Trim());

    partial void OnSelLabelChanged(string value) => Field("label", JsonValue.Create(value));
    partial void OnSelIconChanged(string value) => Field("icon", Str(value));
    partial void OnSelAccentChanged(string? value) => Field("accent", value is null or "neutral" ? null : JsonValue.Create(value));
    partial void OnSelSkinChanged(string? value) => Field("skin", value is null or "auto" ? null : JsonValue.Create(value));
    partial void OnSelFeedbackChanged(string value) => Field("feedbackKey", Str(value));
    partial void OnSelWChanged(decimal? value) { if (!_loading && value is { } w && Doc != null && One is { } c) { Doc.Resize(PageIndex, Selected[0], (int)w, LayoutDoc.Num(c, "h", 1)); Changed(); } }
    partial void OnSelHChanged(decimal? value) { if (!_loading && value is { } h && Doc != null && One is { } c) { Doc.Resize(PageIndex, Selected[0], LayoutDoc.Num(c, "w", 1), (int)h); Changed(); } }

    [RelayCommand]
    void ApplyJson()
    {
        if (Doc == null || Selected.Count != 1) return;
        try
        {
            if (JsonNode.Parse(SelJson) is not JsonObject o) { JsonError = "That is not a JSON object."; return; }
            if ((string?)o["type"] is not { } t || !LayoutCatalog.Types.Contains(t)) { JsonError = "The control needs a known \"type\"."; return; }
            Doc.Replace(PageIndex, Selected[0], o);
            var id = (string?)o["id"];
            if (!string.IsNullOrEmpty(id) && Doc.Find(PageIndex, id) != null) Selected = new[] { id };
            JsonError = null;
            Changed();
        }
        catch (Exception e) { JsonError = e.Message; }
    }

    partial void OnProfileNameChanged(string value)
    {
        if (_loading || Doc == null || string.IsNullOrWhiteSpace(value)) return;
        Doc.SetProfileField("name", value.Trim()); Changed(false);
    }
    partial void OnProfileThemeChanged(Choice? value)
    {
        if (_loading || Doc == null || value == null) return;
        Doc.SetProfileField("theme", value.Id); Changed(false);
    }
    partial void OnThemeFollowsVehicleChanged(bool value)
    {
        if (_loading || Doc == null) return;
        Doc.SetProfileField("themeFollowsVehicle", value ? true : null); Changed(false);
    }
    partial void OnPageNameChanged(string value)
    {
        if (_loading || Doc == null || string.IsNullOrWhiteSpace(value)) return;
        Doc.SetPageField(PageIndex, "name", value.Trim()); Changed(false);
    }
    partial void OnPageColumnsChanged(decimal? value)
    {
        if (_loading || Doc == null || value == null) return;
        Doc.SetPageField(PageIndex, "columns", (int)value); Changed();
    }
    partial void OnPageRowsChanged(decimal? value)
    {
        if (_loading || Doc == null || value == null) return;
        Doc.SetPageField(PageIndex, "rows", (int)value); Changed();
    }
    partial void OnPageThemeChanged(Choice? value)
    {
        if (_loading || Doc == null || value == null) return;
        Doc.SetPageField(PageIndex, "theme", value.Id == "" ? null : value.Id); Changed(false);
    }

    // ---------------------------------------------------------------- canvas and toolbar

    public void Select(IReadOnlyList<string> ids) => Selected = ids;

    public void MoveTo(string id, int x, int y)
    {
        if (Doc == null) return;
        Doc.Move(PageIndex, id, x, y); Changed();
    }

    public void ResizeTo(string id, int w, int h)
    {
        if (Doc == null) return;
        Doc.Resize(PageIndex, id, w, h); Changed();
    }

    /// <summary>Arrow keys: nudge every selected control one cell.</summary>
    public void Nudge(int dx, int dy)
    {
        if (Doc == null || Selected.Count == 0) return;
        foreach (var id in Selected)
            if (Doc.Find(PageIndex, id) is { } c) Doc.Move(PageIndex, id, LayoutDoc.Num(c, "x", 0) + dx, LayoutDoc.Num(c, "y", 0) + dy);
        Changed();
    }

    [RelayCommand]
    void Add()
    {
        if (Doc == null || IsAutoPage) return;
        var id = Doc.Add(PageIndex, AddType.Id);
        if (id == null) { Say($"No room for a {AddType.Name.ToLowerInvariant()} on this page. Make room, or add a row or column.", bad: true); return; }
        Selected = new[] { id };
        Changed();
        Say($"Added a {AddType.Name.ToLowerInvariant()}. Set what it does in the inspector, or paste one from another profile.");
    }

    [RelayCommand]
    void Delete()
    {
        if (Doc == null || Selected.Count == 0) return;
        Doc.Delete(PageIndex, Selected);
        Selected = Array.Empty<string>();
        Changed();
    }

    [RelayCommand]
    void Copy()
    {
        if (Doc == null || Selected.Count == 0) return;
        _clip = Doc.Copy(PageIndex, Selected);
        Changed(false);
        Say($"{ClipText}. Paste on any page of any profile, on this phone or another.");
    }

    [RelayCommand]
    void Paste()
    {
        if (Doc == null || _clip == null || IsAutoPage) return;
        var ids = Doc.Paste(PageIndex, _clip);
        if (ids.Count == 0) { Say("No room on this page for what you copied.", bad: true); return; }
        Selected = ids;
        Changed();
        if (ids.Count < _clip.Count) Say($"Pasted {ids.Count} of {_clip.Count}: the page ran out of room.", bad: true);
    }

    [RelayCommand]
    void Duplicate()
    {
        if (Doc == null || Selected.Count == 0) return;
        var ids = Selected.Select(id => Doc.Duplicate(PageIndex, id)).OfType<string>().ToList();
        if (ids.Count == 0) { Say("No room on this page for a copy.", bad: true); return; }
        Selected = ids;
        Changed();
    }

    [RelayCommand]
    void Undo() { if (Doc?.Undo() == true) Changed(); }

    [RelayCommand]
    void Redo() { if (Doc?.Redo() == true) Changed(); }

    [RelayCommand]
    void AddPage()
    {
        if (Doc == null) return;
        var i = Doc.AddPage();
        Changed();
        PageIndex = i;
    }

    [RelayCommand]
    void CopyPage()
    {
        if (Doc == null || Doc.PageCount == 0) return;
        _pageClip = (JsonObject)Doc.PageAt(PageIndex).DeepClone();
        Changed(false);
        Say($"Page \"{Doc.PageName(PageIndex)}\" copied. Paste it into any profile.");
    }

    [RelayCommand]
    void PastePage()
    {
        if (Doc == null || _pageClip == null) return;
        var i = Doc.PastePage(_pageClip);
        Changed();
        PageIndex = i;
    }

    [RelayCommand]
    void DeletePage()
    {
        if (Doc == null || Doc.PageCount <= 1) { Say("A profile keeps at least one page.", bad: true); return; }
        Doc.DeletePage(PageIndex);
        Changed();
        PageIndex = Math.Min(PageIndex, Doc.PageCount - 1);
        LoadPageFields();
    }
}
