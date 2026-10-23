namespace SimDeck.Companion.Layout;

/// <summary>Colours of one equipment theme, as the phone draws it by day (android ui/sim/SimTheme.kt).</summary>
public sealed record ThemeTokens(
    string Id, string Name, string Environment, string Panel, string PanelLine, string Cap, string CapHigh, string CapEdge,
    string Legend, string LegendMuted, string LampOff, IReadOnlyDictionary<string, string> Lamps, double Corner);

/// <summary>
/// The phone's theme and skin ids (docs/THEMES.md, android core model/Themes.kt), so the PC editor offers the same
/// styles and draws each control in its theme's colours. Keep in step with Themes.kt when a theme or skin is added.
/// </summary>
public static class LayoutCatalog
{
    public static readonly string[] Types = { "momentary", "latching", "switch", "radio", "slider", "rotary", "joystick", "rocker", "gauge", "indicator", "page" };

    public static string TypeName(string type) => type switch
    {
        "momentary" => "Button", "latching" => "Toggle", "switch" => "Selector", "radio" => "Button group", "slider" => "Slider",
        "rotary" => "Knob", "joystick" => "Joystick", "rocker" => "Rocker", "gauge" => "Gauge", "indicator" => "Lamp", "page" => "Page key",
        _ => type,
    };

    public static readonly string[] Accents = { "neutral", "amber", "green", "red", "blue", "cyan" };

    static IReadOnlyDictionary<string, string> Lamps(string neutral, string red = "#FF3B30", string amber = "#FFB000", string green = "#3DDC84", string blue = "#4C9BFF", string cyan = "#36C5F0") =>
        new Dictionary<string, string> { ["neutral"] = neutral, ["red"] = red, ["amber"] = amber, ["green"] = green, ["blue"] = blue, ["cyan"] = cyan };

    public static readonly IReadOnlyList<ThemeTokens> Themes = new[]
    {
        new ThemeTokens("race", "Race (house)", "#0A0D14", "#111622", "#2B3448", "#1C2333", "#262F44", "#2B3448", "#F2F4F8", "#A0A9BC", "#111622",
            Lamps("#FF2E7E", "#FF4B3A", "#FFB020", "#34E07F", "#4C9BFF", "#2EE6E6"), 6),
        new ThemeTokens("airliner", "Airliner", "#1E2226", "#6F7478", "#F2F2F2", "#232629", "#34383C", "#0E0F11", "#FFFFFF", "#E3E5E7", "#15171A", Lamps("#F5F5F5"), 2),
        new ThemeTokens("ga", "Light aircraft", "#141414", "#1C1D1F", "#3A3B3E", "#2A2B2E", "#3B3C40", "#0C0C0D", "#F2F2F2", "#B0B2B5", "#151516", Lamps("#F7F3E8"), 4),
        new ThemeTokens("heavy", "Heavy equipment", "#16181A", "#24272A", "#3A3F44", "#2E3236", "#3B4046", "#111315", "#E9ECEF", "#A9B0B7", "#1B1D20",
            Lamps("#FFB300", "#FF453A", "#FFB300", "#3EDC81"), 12),
        new ThemeTokens("agriculture", "Agriculture", "#16181A", "#24272A", "#3A3F44", "#2E3236", "#3B4046", "#111315", "#E9ECEF", "#A9B0B7", "#1B1D20",
            Lamps("#3EDC81", "#FF453A", "#FFB300", "#3EDC81"), 12),
        new ThemeTokens("commercial", "Truck & bus", "#121416", "#1F2225", "#34383D", "#2A2D31", "#363A3F", "#0E0F11", "#EDEFF2", "#A2A8AF", "#17191C", Lamps("#EDEFF2"), 6),
        new ThemeTokens("roadcar", "Road car", "#0E0F11", "#141518", "#A7ABB0", "#1B1D21", "#26292E", "#8E9399", "#F5F6F7", "#A6ABB1", "#121316", Lamps("#FFFFFF", blue: "#3B8CFF"), 10),
        new ThemeTokens("overland", "Overland", "#121310", "#1C1D1A", "#4A4C46", "#2A2B28", "#363833", "#0D0D0C", "#ECE9DF", "#A8A496", "#181917", Lamps("#FFB000"), 4),
        new ThemeTokens("motorsport", "Motorsport", "#0B0B0C", "#17181A", "#2C2E31", "#232528", "#2F3236", "#09090A", "#FFFFFF", "#A9ADB3", "#141517",
            Lamps("#FFFFFF", "#FF2D2D", green: "#2EE66B", blue: "#2F7BFF"), 4),
    };

    public static ThemeTokens Theme(string? id) => Themes.FirstOrDefault(t => t.Id == id) ?? Themes[0];

    public static readonly IReadOnlyDictionary<string, string[]> SkinsByType = new Dictionary<string, string[]>
    {
        ["momentary"] = new[] { "cap", "lit-push", "ring-push", "rubber-key", "rocker-mom", "glass-tile", "mushroom" },
        ["latching"] = new[] { "cap", "toggle-bat", "lit-push-split", "rocker-lamp", "push-pull", "key", "glass-toggle", "ring-push", "rubber-key", "lit-push", "gear-lever" },
        ["switch"] = new[] { "slide-select", "rotary-pointer", "gated-lever" },
        ["radio"] = new[] { "cap", "lit-row", "annunciator-row", "glass-tile" },
        ["slider"] = new[] { "fader", "hydraulic-lever", "thrust-lever", "quadrant-knob", "hand-throttle", "pedal", "stalk" },
        ["rotary"] = new[] { "encoder", "concentric", "multi-pos", "trim-wheel", "thumb-roller", "chrome-dial" },
        ["joystick"] = new[] { "gate", "pilot-grip", "sidestick", "yoke", "hat" },
        ["rocker"] = new[] { "rocker-3", "toggle-3", "window-switch" },
        ["gauge"] = new[] { "readout", "steam", "segment", "bar", "tape", "shift-lights", "inclinometer" },
        ["indicator"] = new[] { "telltale", "annunciator", "master", "master-warning", "led-dot" },
        ["page"] = new[] { "cap", "page-tab" },
    };

    static Dictionary<string, string> D(params string[] kv)
    {
        var d = new Dictionary<string, string>();
        for (int i = 0; i + 1 < kv.Length; i += 2) d[kv[i]] = kv[i + 1];
        return d;
    }

    static readonly Dictionary<string, Dictionary<string, string>> Defaults = new()
    {
        ["airliner"] = D("momentary", "lit-push", "latching", "lit-push-split", "switch", "rotary-pointer", "radio", "lit-row", "slider", "thrust-lever", "rotary", "concentric",
            "joystick", "sidestick", "rocker", "toggle-3", "gauge", "segment", "indicator", "annunciator", "page", "page-tab"),
        ["ga"] = D("momentary", "ring-push", "latching", "toggle-bat", "switch", "rotary-pointer", "radio", "lit-row", "slider", "quadrant-knob", "rotary", "encoder",
            "joystick", "yoke", "rocker", "toggle-3", "gauge", "steam", "indicator", "annunciator", "page", "page-tab"),
        ["heavy"] = D("momentary", "rubber-key", "latching", "rocker-lamp", "switch", "gated-lever", "radio", "annunciator-row", "slider", "hydraulic-lever", "rotary", "thumb-roller",
            "joystick", "pilot-grip", "rocker", "rocker-3", "gauge", "steam", "indicator", "led-dot", "page", "page-tab"),
        ["agriculture"] = D("momentary", "rubber-key", "latching", "rocker-lamp", "switch", "gated-lever", "radio", "annunciator-row", "slider", "hydraulic-lever", "rotary", "thumb-roller",
            "joystick", "pilot-grip", "rocker", "rocker-3", "gauge", "bar", "indicator", "led-dot", "page", "page-tab"),
        ["commercial"] = D("momentary", "rocker-mom", "latching", "rocker-lamp", "switch", "rotary-pointer", "radio", "lit-row", "slider", "stalk", "rotary", "encoder",
            "joystick", "gate", "rocker", "rocker-3", "gauge", "steam", "indicator", "telltale", "page", "page-tab"),
        ["roadcar"] = D("momentary", "glass-tile", "latching", "glass-toggle", "switch", "gated-lever", "radio", "glass-tile", "slider", "pedal", "rotary", "chrome-dial",
            "joystick", "gate", "rocker", "window-switch", "gauge", "steam", "indicator", "telltale", "page", "page-tab"),
        ["overland"] = D("momentary", "ring-push", "latching", "rocker-lamp", "switch", "rotary-pointer", "radio", "annunciator-row", "slider", "hand-throttle", "rotary", "encoder",
            "joystick", "gate", "rocker", "rocker-3", "gauge", "steam", "indicator", "telltale", "page", "page-tab"),
        ["motorsport"] = D("momentary", "ring-push", "latching", "toggle-bat", "switch", "rotary-pointer", "radio", "lit-row", "slider", "pedal", "rotary", "multi-pos",
            "joystick", "gate", "rocker", "toggle-3", "gauge", "segment", "indicator", "led-dot", "page", "page-tab"),
    };

    static readonly HashSet<string> PhysicalInRoadCar = new() { "signal_left", "signal_right", "hazard", "horn", "wiper" };

    /// <summary>The skin a control is drawn with under [theme] (same rules as Skins.resolve on the phone).</summary>
    public static string ResolveSkin(string type, string? skin, string theme, string? range, bool springReturn, string? label, string? icon)
    {
        if (!SkinsByType.TryGetValue(type, out var valid)) return "cap";
        if (skin != null && valid.Contains(skin)) return skin;
        if (type == "slider" && range == "bipolar" && springReturn && theme is "race" or "heavy" or "agriculture" or "overland" or "commercial") return "hydraulic-lever";
        if (theme == "roadcar" && icon != null && PhysicalInRoadCar.Contains(icon) && type is "momentary" or "latching") return "ring-push";
        if (theme == "airliner" && type == "slider" && range != "bipolar")
        {
            var n = ((label ?? "") + " " + (icon ?? "")).ToLowerInvariant();
            if (new[] { "flap", "gear", "prop", "mix" }.Any(n.Contains)) return "quadrant-knob";
        }
        if (theme is "airliner" or "ga" && type == "slider" && range == "bipolar") return "fader";
        return Defaults.TryGetValue(theme, out var d) && d.TryGetValue(type, out var s) ? s : valid[0];
    }

    /// <summary>Whether a skin draws round (knobs, round buttons, dials).</summary>
    public static bool IsRound(string skin) => skin is "ring-push" or "mushroom" or "push-pull" or "key" or "encoder" or "concentric" or "multi-pos"
        or "chrome-dial" or "steam" or "inclinometer" or "rotary-pointer" or "gate" or "sidestick" or "hat" or "led-dot";
}
