using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace SimDeck.Companion.BeamNG;

/// <summary>What the installer found in the game's user folder.</summary>
public sealed record ModStatus(
    string? UserFolder,
    string? ModsFolder,
    string? InstalledVersion,
    string BundledVersion,
    IReadOnlyList<string> OldCopies,
    bool GameRunning)
{
    public bool Installed => InstalledVersion != null;
    public bool UpToDate => InstalledVersion != null && ModInstaller.Compare(InstalledVersion, BundledVersion) >= 0 && OldCopies.Count == 0;
    public bool NeedsInstall => ModsFolder != null && (InstalledVersion == null || ModInstaller.Compare(InstalledVersion, BundledVersion) < 0 || OldCopies.Count > 0);

    public string Summary => ModsFolder == null
        ? "BeamNG.drive's user folder was not found. Start the game once, or choose its user folder."
        : InstalledVersion == null ? $"Not installed. The companion carries mod {BundledVersion}."
        : OldCopies.Count > 0 ? $"Mod {InstalledVersion} installed, plus {OldCopies.Count} old cop{(OldCopies.Count == 1 ? "y" : "ies")} that would clash."
        : ModInstaller.Compare(InstalledVersion, BundledVersion) < 0 ? $"Mod {InstalledVersion} installed; {BundledVersion} is ready to install."
        : ModInstaller.Compare(InstalledVersion, BundledVersion) > 0 ? $"Mod {InstalledVersion} installed (newer than this companion's {BundledVersion})."
        : $"Mod {InstalledVersion} installed and up to date.";
}

public sealed record ModInstallResult(bool Ok, string Message, string? Path = null);

/// <summary>
/// Installs and updates the SimDeck BeamNG mod from the copy built into the companion (SimDeck 1.4.1), so nobody has to copy
/// zips by hand: it finds the game's user folder, writes mods\simdeck.zip, and disables old copies (gamecontroller.zip,
/// simdeck_bridge.zip, renamed SimDeck zips) that would fight over the same port. It never writes while the game runs,
/// because BeamNG keeps its mod zips open.
/// </summary>
public sealed class ModInstaller
{
    public const string ZipName = "simdeck.zip";
    public const string DisabledSuffix = ".disabled-by-simdeck";
    const string ResourcePrefix = "simdeck-mod/";
    static readonly string[] OldNames = { "gamecontroller.zip", "simdeck_bridge.zip" };

    readonly Func<string?> _customUserFolder;
    readonly string _localAppData;
    readonly Func<bool> _gameRunning;
    readonly Func<byte[]> _zip;

    /// <param name="customUserFolder">A user folder chosen in Settings, or null to find it.</param>
    /// <param name="localAppData">%LOCALAPPDATA% (tests pass a temporary folder).</param>
    /// <param name="gameRunning">Whether BeamNG is running (tests pass a fake).</param>
    public ModInstaller(Func<string?> customUserFolder, string? localAppData = null, Func<bool>? gameRunning = null, Func<byte[]>? zip = null)
    {
        _customUserFolder = customUserFolder;
        _localAppData = localAppData ?? Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        _gameRunning = gameRunning ?? IsGameRunning;
        _zip = zip ?? BuildZip;
    }

    // ---------------------------------------------------------------- the built-in mod

    static IEnumerable<(string Path, string Resource)> Resources()
    {
        var asm = typeof(ModInstaller).Assembly;
        foreach (var name in asm.GetManifestResourceNames())
        {
            var norm = name.Replace('\\', '/');
            if (norm.StartsWith(ResourcePrefix, StringComparison.Ordinal)) yield return (norm[ResourcePrefix.Length..], name);
        }
    }

    /// <summary>The mod as BeamNG expects it: mod_info, scripts and lua at the zip root (beamng-mod/build.sh).</summary>
    public static byte[] BuildZip()
    {
        using var ms = new MemoryStream();
        using (var zip = new ZipArchive(ms, ZipArchiveMode.Create, leaveOpen: true))
        {
            var asm = typeof(ModInstaller).Assembly;
            foreach (var (path, res) in Resources().OrderBy(r => r.Path, StringComparer.Ordinal))
            {
                var entry = zip.CreateEntry(path, CompressionLevel.Optimal);
                entry.LastWriteTime = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
                using var src = asm.GetManifestResourceStream(res)!;
                using var dst = entry.Open();
                src.CopyTo(dst);
            }
        }
        return ms.ToArray();
    }

    static string? _bundled;

    /// <summary>The version of the mod built into this companion (its info.json).</summary>
    public static string BundledVersion => _bundled ??= ReadBundledVersion();

    static string ReadBundledVersion()
    {
        var asm = typeof(ModInstaller).Assembly;
        var info = Resources().FirstOrDefault(r => r.Path == "mod_info/simdeck/info.json");
        if (info.Resource == null) return "0";
        using var s = asm.GetManifestResourceStream(info.Resource)!;
        return VersionFromInfo(s) ?? "0";
    }

    static string? VersionFromInfo(Stream s)
    {
        try
        {
            using var doc = JsonDocument.Parse(s, new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip });
            return doc.RootElement.TryGetProperty("version_string", out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;
        }
        catch (JsonException) { return null; }
    }

    /// <summary>The SimDeck mod version inside a zip, or null when the zip is not a SimDeck mod.</summary>
    public static string? ZipVersion(string path)
    {
        try
        {
            using var zip = ZipFile.OpenRead(path);
            var info = zip.Entries.FirstOrDefault(e => e.FullName.Replace('\\', '/').Equals("mod_info/simdeck/info.json", StringComparison.OrdinalIgnoreCase));
            if (info != null) { using var s = info.Open(); if (VersionFromInfo(s) is { } v) return v; }
            var link = zip.Entries.FirstOrDefault(e => e.FullName.Replace('\\', '/').EndsWith("lua/ge/extensions/simdeckLink.lua", StringComparison.OrdinalIgnoreCase));
            if (link == null) return null;
            using var r = new StreamReader(link.Open());
            var m = Regex.Match(r.ReadToEnd(), "MOD_VERSION\\s*=\\s*\"([^\"]+)\"");
            return m.Success ? m.Groups[1].Value : "0";
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException) { return null; }
    }

    /// <summary>Compares dotted versions numerically (1.10 &gt; 1.9); anything unreadable counts as 0.</summary>
    public static int Compare(string a, string b)
    {
        static int[] P(string v) => v.Split('.', '-', '+').Select(x => int.TryParse(x, out var n) ? n : 0).ToArray();
        var x = P(a); var y = P(b);
        for (int i = 0; i < Math.Max(x.Length, y.Length); i++)
        {
            int c = (i < x.Length ? x[i] : 0).CompareTo(i < y.Length ? y[i] : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    // ---------------------------------------------------------------- finding the game

    public static bool IsGameRunning()
    {
        try { return Process.GetProcesses().Any(p => { try { return p.ProcessName.StartsWith("BeamNG.drive", StringComparison.OrdinalIgnoreCase); } catch { return false; } }); }
        catch { return false; }
    }

    /// <summary>
    /// The game's user folder: the one chosen in Settings, else the current layout
    /// (%LOCALAPPDATA%\BeamNG\BeamNG.drive\current, 0.37 and later), else the newest version folder of the old layout
    /// (%LOCALAPPDATA%\BeamNG.drive\0.36 ...).
    /// </summary>
    public string? FindUserFolder()
    {
        var custom = _customUserFolder();
        if (!string.IsNullOrWhiteSpace(custom)) return Directory.Exists(custom) ? custom : null;
        var current = Path.Combine(_localAppData, "BeamNG", "BeamNG.drive", "current");
        if (Directory.Exists(current)) return current;
        var old = Path.Combine(_localAppData, "BeamNG.drive");
        if (Directory.Exists(old))
        {
            var newest = Directory.GetDirectories(old)
                .Select(d => (Dir: d, Name: Path.GetFileName(d)))
                .Where(d => Regex.IsMatch(d.Name, "^\\d+\\.\\d+(\\.\\d+)?$"))
                .OrderByDescending(d => d.Name, Comparer<string>.Create(Compare))
                .FirstOrDefault();
            if (newest.Dir != null) return newest.Dir;
        }
        return null;
    }

    public ModStatus Check()
    {
        var user = FindUserFolder();
        var mods = user == null ? null : Path.Combine(user, "mods");
        string? installed = null;
        var old = new List<string>();
        if (mods != null && Directory.Exists(mods))
        {
            var target = Path.Combine(mods, ZipName);
            if (File.Exists(target)) installed = ZipVersion(target);
            foreach (var f in Directory.GetFiles(mods, "*.zip"))
            {
                if (string.Equals(Path.GetFileName(f), ZipName, StringComparison.OrdinalIgnoreCase)) continue;
                if (OldNames.Contains(Path.GetFileName(f), StringComparer.OrdinalIgnoreCase) || ZipVersion(f) != null) old.Add(f);
            }
        }
        return new ModStatus(user, mods, installed, BundledVersion, old, _gameRunning());
    }

    /// <summary>
    /// Writes the built-in mod to mods\simdeck.zip (through a temporary file, so a failed write leaves the old one) and
    /// disables old copies by renaming them. Refuses while BeamNG runs.
    /// </summary>
    public ModInstallResult Install()
    {
        var st = Check();
        if (st.UserFolder == null || st.ModsFolder == null)
            return new ModInstallResult(false, "BeamNG.drive's user folder was not found. Start the game once, or choose its user folder in Settings.");
        if (st.GameRunning)
            return new ModInstallResult(false, "Close BeamNG.drive first: the game keeps its mods open while it runs. The companion installs the mod when the game closes.");
        var target = Path.Combine(st.ModsFolder, ZipName);
        var tmp = target + ".new";
        try
        {
            Directory.CreateDirectory(st.ModsFolder);
            File.WriteAllBytes(tmp, _zip());
            File.Move(tmp, target, overwrite: true);
            var disabled = new List<string>();
            foreach (var f in st.OldCopies)
            {
                var to = f + DisabledSuffix;
                if (File.Exists(to)) File.Delete(to);
                File.Move(f, to);
                disabled.Add(Path.GetFileName(f));
            }
            var msg = $"SimDeck mod {BundledVersion} installed in {st.ModsFolder}." +
                      (disabled.Count > 0 ? $" Turned off old copies: {string.Join(", ", disabled)}." : "") +
                      " BeamNG loads it the next time it starts.";
            return new ModInstallResult(true, msg, target);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            try { if (File.Exists(tmp)) File.Delete(tmp); } catch { }
            return new ModInstallResult(false, $"Could not write the mod to {st.ModsFolder}: {e.Message}");
        }
    }
}
