using System.Text.Json;
using System.Text.Json.Nodes;

namespace SimDeck.Companion.Layout;

/// <summary>
/// A phone profile open in the PC layout editor. It edits the profile file JSON (PROTOCOL.md, Profile file format)
/// directly, so fields the PC editor does not know survive untouched, and keeps an undo history. The same layout rules
/// as the phone apply: controls stay inside the grid and keep their minimum span.
/// </summary>
public sealed class LayoutDoc
{
    const int MaxUndo = 100;
    readonly Stack<string> _undo = new();
    readonly Stack<string> _redo = new();
    string _saved;

    public JsonObject Profile { get; private set; }
    /// <summary>The phone this profile came from.</summary>
    public string SessionKey { get; }

    public LayoutDoc(JsonObject profile, string sessionKey)
    {
        Profile = (JsonObject)profile.DeepClone();
        SessionKey = sessionKey;
        if (Profile["pages"] is not JsonArray) Profile["pages"] = new JsonArray(Page("p1", "Main"));
        _saved = Profile.ToJsonString();
    }

    public string Id => (string?)Profile["id"] ?? "";
    public string Name => (string?)Profile["name"] ?? "Profile";
    public string Theme => (string?)Profile["theme"] ?? "race";
    public bool Dirty => Profile.ToJsonString() != _saved;
    public bool CanUndo => _undo.Count > 0;
    public bool CanRedo => _redo.Count > 0;

    /// <summary>Marks the current state as the one the phone has (after a successful save).</summary>
    public void MarkSaved() => _saved = Profile.ToJsonString();

    JsonArray Pages => (JsonArray)Profile["pages"]!;
    public int PageCount => Pages.Count;
    public JsonObject PageAt(int i) => (JsonObject)Pages[Math.Clamp(i, 0, Pages.Count - 1)]!;
    public string PageName(int i) => (string?)PageAt(i)["name"] ?? $"Page {i + 1}";
    public int Columns(int i) => Num(PageAt(i), "columns", 8);
    public int Rows(int i) => Num(PageAt(i), "rows", 4);
    public string PageTheme(int i) => (string?)PageAt(i)["theme"] ?? Theme;
    public bool IsAuto(int i) => PageAt(i)["auto"] != null;

    public JsonArray ControlsArray(int page)
    {
        var p = PageAt(page);
        if (p["controls"] is not JsonArray a) { a = new JsonArray(); p["controls"] = a; }
        return a;
    }

    public IReadOnlyList<JsonObject> Controls(int page) => ControlsArray(page).OfType<JsonObject>().ToList();
    public JsonObject? Find(int page, string id) => Controls(page).FirstOrDefault(c => (string?)c["id"] == id);

    /// <summary>An integer field, whether the value was parsed from text or set from code (int, long or double).</summary>
    public static int Num(JsonObject o, string key, int fallback)
    {
        if (o[key] is not JsonValue v) return fallback;
        if (v.TryGetValue<int>(out var i)) return i;
        if (v.TryGetValue<long>(out var l)) return (int)l;
        if (v.TryGetValue<double>(out var d)) return (int)Math.Round(d);
        return fallback;
    }

    // ---------------------------------------------------------------- history

    void Snapshot()
    {
        _undo.Push(Profile.ToJsonString());
        if (_undo.Count > MaxUndo)
        {
            var keep = _undo.Take(MaxUndo).Reverse().ToList();
            _undo.Clear();
            foreach (var s in keep) _undo.Push(s);
        }
        _redo.Clear();
    }

    public bool Undo()
    {
        if (_undo.Count == 0) return false;
        _redo.Push(Profile.ToJsonString());
        Profile = (JsonObject)JsonNode.Parse(_undo.Pop())!;
        return true;
    }

    public bool Redo()
    {
        if (_redo.Count == 0) return false;
        _undo.Push(Profile.ToJsonString());
        Profile = (JsonObject)JsonNode.Parse(_redo.Pop())!;
        return true;
    }

    // ---------------------------------------------------------------- layout rules

    /// <summary>Smallest span (w, h) at which a control stays usable (same as LayoutOps.minSpan on the phone).</summary>
    public static (int W, int H) MinSpan(JsonObject c)
    {
        var type = (string?)c["type"] ?? "momentary";
        return type switch
        {
            "switch" or "radio" => (Math.Max(2, (c["positions"] as JsonArray)?.Count ?? 2), 1),
            "rotary" or "joystick" => (2, 2),
            "slider" => (c["vertical"] is JsonValue v && v.TryGetValue<bool>(out var vert) && !vert) ? (2, 1) : (1, 2),
            _ => (1, 1),
        };
    }

    void Clamp(JsonObject c, int page)
    {
        int cols = Columns(page), rows = Rows(page);
        var (mw, mh) = MinSpan(c);
        int w = Math.Clamp(Math.Max(Num(c, "w", 1), mw), 1, cols);
        int h = Math.Clamp(Math.Max(Num(c, "h", 1), mh), 1, rows);
        c["w"] = w; c["h"] = h;
        c["x"] = Math.Clamp(Num(c, "x", 0), 0, cols - w);
        c["y"] = Math.Clamp(Num(c, "y", 0), 0, rows - h);
    }

    /// <summary>First spot on [page] where a w x h control fits without overlapping; null when none.</summary>
    public (int X, int Y)? FreeSpot(int page, int w, int h, string? ignoreId = null)
    {
        int cols = Columns(page), rows = Rows(page);
        if (w > cols || h > rows) return null;
        var others = Controls(page).Where(c => (string?)c["id"] != ignoreId).ToList();
        for (int y = 0; y <= rows - h; y++)
            for (int x = 0; x <= cols - w; x++)
                if (!others.Any(c => x < Num(c, "x", 0) + Num(c, "w", 1) && x + w > Num(c, "x", 0) && y < Num(c, "y", 0) + Num(c, "h", 1) && y + h > Num(c, "y", 0)))
                    return (x, y);
        return null;
    }

    static string NewId(string prefix) => prefix + Guid.NewGuid().ToString("N")[..8];

    // ---------------------------------------------------------------- edits

    public void Move(int page, string id, int x, int y)
    {
        if (Find(page, id) is not { } c) return;
        if (Num(c, "x", 0) == x && Num(c, "y", 0) == y) return;
        Snapshot();
        c["x"] = x; c["y"] = y;
        Clamp(c, page);
    }

    public void Resize(int page, string id, int w, int h)
    {
        if (Find(page, id) is not { } c) return;
        if (Num(c, "w", 1) == w && Num(c, "h", 1) == h) return;
        Snapshot();
        c["w"] = Math.Max(1, w); c["h"] = Math.Max(1, h);
        Clamp(c, page);
    }

    /// <summary>A new control of [type] with the phone's defaults, at the first free spot. Null when the page is full.</summary>
    public string? Add(int page, string type)
    {
        var c = DefaultControl(type);
        return Place(page, c, snapshot: true);
    }

    /// <summary>Places a copy of [template] (new id) at the first free spot, shrinking to its minimum span if needed.</summary>
    string? Place(int page, JsonObject template, bool snapshot)
    {
        if (IsAuto(page)) return null;
        var c = (JsonObject)template.DeepClone();
        c["id"] = NewId("c-");
        var (mw, mh) = MinSpan(c);
        int w = Num(c, "w", 1), h = Num(c, "h", 1);
        foreach (var (sw, sh) in new[] { (w, h), (Math.Min(w, Columns(page)), Math.Min(h, Rows(page))), (mw, mh) }.Distinct())
        {
            if (FreeSpot(page, sw, sh) is not { } spot) continue;
            if (snapshot) Snapshot();
            c["x"] = spot.X; c["y"] = spot.Y; c["w"] = sw; c["h"] = sh;
            ControlsArray(page).Add(c);
            Clamp(c, page);
            return (string)c["id"]!;
        }
        return null;
    }

    public void Delete(int page, IEnumerable<string> ids)
    {
        var set = ids.ToHashSet();
        var arr = ControlsArray(page);
        var gone = arr.OfType<JsonObject>().Where(c => set.Contains((string?)c["id"] ?? "")).ToList();
        if (gone.Count == 0) return;
        Snapshot();
        foreach (var c in gone) arr.Remove(c);
    }

    /// <summary>Copies of the controls (for the clipboard), positions kept.</summary>
    public JsonArray Copy(int page, IEnumerable<string> ids)
    {
        var set = ids.ToHashSet();
        return new JsonArray(Controls(page).Where(c => set.Contains((string?)c["id"] ?? "")).Select(c => (JsonNode)c.DeepClone()).ToArray());
    }

    /// <summary>Pastes clipboard controls (from any page or profile) onto [page]; returns the new ids, in order.</summary>
    public List<string> Paste(int page, JsonArray clip)
    {
        var ids = new List<string>();
        if (clip.Count == 0 || IsAuto(page)) return ids;
        Snapshot();
        foreach (var n in clip.OfType<JsonObject>())
            if (Place(page, n, snapshot: false) is { } id) ids.Add(id);
        if (ids.Count == 0) Undo();
        return ids;
    }

    public string? Duplicate(int page, string id) => Find(page, id) is { } c ? Place(page, c, snapshot: true) : null;

    /// <summary>Replaces a control with [json] (the JSON editor), keeping its id and clamping it to the grid.</summary>
    public void Replace(int page, string id, JsonObject json)
    {
        var arr = ControlsArray(page);
        var i = arr.Select((n, k) => (n, k)).FirstOrDefault(t => (string?)(t.n as JsonObject)?["id"] == id).k;
        if (arr.ElementAtOrDefault(i) is not JsonObject old || (string?)old["id"] != id) return;
        Snapshot();
        var c = (JsonObject)json.DeepClone();
        c["id"] = id;
        if (c["type"] == null) c["type"] = old["type"]?.DeepClone();
        arr[i] = c;
        Clamp(c, page);
    }

    /// <summary>Sets one field of a control; null removes it.</summary>
    public void SetField(int page, string id, string key, JsonNode? value)
    {
        if (Find(page, id) is not { } c) return;
        if (JsonNode.DeepEquals(c[key], value)) return;
        Snapshot();
        if (value == null) c.Remove(key); else c[key] = value.DeepClone();
        if (key is "w" or "h" or "x" or "y" or "vertical" or "positions") Clamp(c, page);
    }

    public void SetProfileField(string key, JsonNode? value)
    {
        if (JsonNode.DeepEquals(Profile[key], value)) return;
        Snapshot();
        if (value == null) Profile.Remove(key); else Profile[key] = value.DeepClone();
    }

    public void SetPageField(int page, string key, JsonNode? value)
    {
        var p = PageAt(page);
        if (JsonNode.DeepEquals(p[key], value)) return;
        Snapshot();
        if (value == null) p.Remove(key); else p[key] = value.DeepClone();
        if (key is "columns" or "rows")
        {
            p["columns"] = Math.Clamp(Num(p, "columns", 8), 4, 12);
            p["rows"] = Math.Clamp(Num(p, "rows", 4), 2, 8);
            foreach (var c in Controls(page)) Clamp(c, page);
        }
    }

    public int AddPage()
    {
        Snapshot();
        var last = PageAt(PageCount - 1);
        Pages.Add(Page(NewId("pg-"), $"Page {PageCount + 1}", Num(last, "columns", 8), Num(last, "rows", 4)));
        return PageCount - 1;
    }

    /// <summary>Copies [page] (from any profile) in as a new last page with fresh ids.</summary>
    public int PastePage(JsonObject page)
    {
        Snapshot();
        var p = (JsonObject)page.DeepClone();
        p["id"] = NewId("pg-");
        var name = (string?)p["name"] ?? "Page";
        if (Enumerable.Range(0, PageCount).Any(i => PageName(i) == name)) p["name"] = name + " copy";
        if (p["controls"] is JsonArray cs) foreach (var c in cs.OfType<JsonObject>()) c["id"] = NewId("c-");
        Pages.Add(p);
        return PageCount - 1;
    }

    public void DeletePage(int page)
    {
        if (PageCount <= 1) return;
        Snapshot();
        Pages.RemoveAt(page);
    }

    static JsonObject Page(string id, string name, int cols = 8, int rows = 4) =>
        new() { ["id"] = id, ["name"] = name, ["columns"] = cols, ["rows"] = rows, ["controls"] = new JsonArray() };

    static JsonObject Btn(int n) => new() { ["type"] = "vjoy_button", ["device"] = 1, ["button"] = n };

    /// <summary>A new control with the phone's defaults (LayoutOps.defaultControl).</summary>
    public static JsonObject DefaultControl(string type)
    {
        var c = new JsonObject { ["id"] = "", ["type"] = type, ["label"] = LayoutCatalog.TypeName(type), ["x"] = 0, ["y"] = 0, ["w"] = 1, ["h"] = 1 };
        switch (type)
        {
            case "momentary": c["action"] = Btn(1); break;
            case "latching": c["action"] = Btn(1); c["accent"] = "amber"; break;
            case "switch":
            case "radio":
                c["w"] = 3; c["accent"] = "cyan";
                c["positions"] = new JsonArray(new JsonObject { ["label"] = "1" }, new JsonObject { ["label"] = "2" }, new JsonObject { ["label"] = "3" });
                break;
            case "slider":
                c["h"] = 2; c["accent"] = "green"; c["range"] = "unipolar";
                c["action"] = new JsonObject { ["type"] = "vjoy_axis", ["device"] = 1, ["axis"] = "SL0", ["range"] = "unipolar" };
                break;
            case "rotary": c["w"] = 2; c["h"] = 2; c["accent"] = "cyan"; break;
            case "joystick":
                c["w"] = 2; c["h"] = 2; c["accent"] = "amber";
                c["action"] = new JsonObject { ["type"] = "vjoy_axis", ["device"] = 1, ["axis"] = "X" };
                c["actionY"] = new JsonObject { ["type"] = "vjoy_axis", ["device"] = 1, ["axis"] = "Y" };
                break;
            case "rocker":
                c["h"] = 2; c["accent"] = "cyan";
                c["cw"] = new JsonObject { ["type"] = "key", ["keys"] = new JsonArray("UP") };
                c["ccw"] = new JsonObject { ["type"] = "key", ["keys"] = new JsonArray("DOWN") };
                break;
            case "gauge": c["w"] = 2; c["accent"] = "cyan"; c["feedbackKey"] = "speed"; c["scale"] = 3.6; c["unit"] = "km/h"; c["label"] = "Speed"; break;
            case "indicator": c["accent"] = "amber"; c["icon"] = "check_engine"; c["feedbackKey"] = "parkingbrake"; break;
            case "page": c["icon"] = "page_next"; c["pageTarget"] = "next"; c["label"] = "Next page"; break;
        }
        return c;
    }

    /// <summary>
    /// The profile as it goes back to the phone: its schema raised when the edit used newer fields, so an older phone
    /// refuses it with a clear message instead of losing them.
    /// </summary>
    public JsonObject ForPhone()
    {
        var p = (JsonObject)Profile.DeepClone();
        int schema = Num(p, "schema", 1);
        var controls = Enumerable.Range(0, PageCount).SelectMany(Controls).ToList();
        // 6: a tuning page (SimDeck 1.4); 5: a control backlight (1.3); 4: equipment themes (1.2).
        int need = Enumerable.Range(0, PageCount).Any(i => (string?)PageAt(i)["auto"] == "beamng.tuning") ? 6
            : controls.Any(c => c["backlight"] != null) ? 5
            : p["theme"] != null || Enumerable.Range(0, PageCount).Any(i => PageAt(i)["theme"] != null || PageAt(i)["panels"] != null) ||
              controls.Any(c => c["skin"] != null || c["legends"] != null || c["ends"] != null || c["stations"] != null || c["bands"] != null) ? 4 : 1;
        p["schema"] = Math.Max(schema, need);
        return p;
    }

    public static string Pretty(JsonNode n) => n.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
}
