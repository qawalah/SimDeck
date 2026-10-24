using System.IO.Compression;
using SimDeck.Companion.BeamNG;
using Xunit;

namespace SimDeck.Companion.Tests;

/// <summary>SimDeck 1.4.1: the companion installs and updates the BeamNG mod itself.</summary>
public sealed class ModInstallerTests : IDisposable
{
    readonly string _root = Path.Combine(Path.GetTempPath(), $"sd-mod-{Guid.NewGuid():N}");
    bool _running;

    public ModInstallerTests() => Directory.CreateDirectory(_root);
    public void Dispose() { try { Directory.Delete(_root, true); } catch { } }

    ModInstaller Make(string? custom = null) => new(() => custom, _root, () => _running);

    static void FakeMod(string path, string version)
    {
        using var z = ZipFile.Open(path, ZipArchiveMode.Create);
        using (var w = new StreamWriter(z.CreateEntry("mod_info/simdeck/info.json").Open())) w.Write($$"""{"version_string": "{{version}}"}""");
        using (var w = new StreamWriter(z.CreateEntry("lua/ge/extensions/simdeckLink.lua").Open())) w.Write($"local MOD_VERSION = \"{version}\"");
    }

    [Fact]
    public void The_built_in_mod_is_complete_and_versioned()
    {
        var zipBytes = ModInstaller.BuildZip();
        using var z = new ZipArchive(new MemoryStream(zipBytes));
        var names = z.Entries.Select(e => e.FullName).ToList();
        Assert.Contains("mod_info/simdeck/info.json", names);
        Assert.Contains("lua/ge/extensions/simdeckLink.lua", names);
        Assert.Contains("lua/vehicle/extensions/simdeckVehicle.lua", names);
        Assert.Contains("scripts/simdeck/modScript.lua", names);
        Assert.DoesNotContain(names, n => n.Contains('\\'));
        // the bundled version is the mod's own, and matches the GE extension's MOD_VERSION
        var link = new StreamReader(z.GetEntry("lua/ge/extensions/simdeckLink.lua")!.Open()).ReadToEnd();
        Assert.Contains($"MOD_VERSION = \"{ModInstaller.BundledVersion}\"", link);
        Assert.NotEqual("0", ModInstaller.BundledVersion);
    }

    [Fact]
    public void Finds_the_current_user_folder_then_the_newest_old_one()
    {
        Assert.Null(Make().FindUserFolder());
        Directory.CreateDirectory(Path.Combine(_root, "BeamNG.drive", "0.9"));
        Directory.CreateDirectory(Path.Combine(_root, "BeamNG.drive", "0.36"));
        Directory.CreateDirectory(Path.Combine(_root, "BeamNG.drive", "settings"));
        Assert.EndsWith(Path.Combine("BeamNG.drive", "0.36"), Make().FindUserFolder());
        var current = Path.Combine(_root, "BeamNG", "BeamNG.drive", "current");
        Directory.CreateDirectory(current);
        Assert.Equal(current, Make().FindUserFolder());
        var custom = Path.Combine(_root, "MyBeam");
        Directory.CreateDirectory(custom);
        Assert.Equal(custom, Make(custom).FindUserFolder());
        Assert.Null(Make(Path.Combine(_root, "missing")).FindUserFolder());
    }

    [Fact]
    public void Installs_updates_and_turns_off_old_copies_but_never_while_the_game_runs()
    {
        var user = Path.Combine(_root, "BeamNG", "BeamNG.drive", "current");
        var mods = Path.Combine(user, "mods");
        Directory.CreateDirectory(mods);
        File.WriteAllText(Path.Combine(mods, "gamecontroller.zip"), "old");
        FakeMod(Path.Combine(mods, "simdeck (1).zip"), "1.1.0");
        FakeMod(Path.Combine(mods, "simdeck.zip"), "1.2.0");
        using (var z = ZipFile.Open(Path.Combine(mods, "other_mod.zip"), ZipArchiveMode.Create)) z.CreateEntry("vehicles/x.jbeam");

        var st = Make().Check();
        Assert.Equal("1.2.0", st.InstalledVersion);
        Assert.Equal(2, st.OldCopies.Count);
        Assert.True(st.NeedsInstall);
        Assert.Contains("old cop", st.Summary);

        _running = true;
        var refused = Make().Install();
        Assert.False(refused.Ok);
        Assert.Contains("Close BeamNG", refused.Message);
        Assert.Equal("1.2.0", ModInstaller.ZipVersion(Path.Combine(mods, "simdeck.zip")));

        _running = false;
        var r = Make().Install();
        Assert.True(r.Ok, r.Message);
        Assert.Equal(ModInstaller.BundledVersion, ModInstaller.ZipVersion(Path.Combine(mods, "simdeck.zip")));
        Assert.True(File.Exists(Path.Combine(mods, "gamecontroller.zip" + ModInstaller.DisabledSuffix)));
        Assert.True(File.Exists(Path.Combine(mods, "simdeck (1).zip" + ModInstaller.DisabledSuffix)));
        Assert.True(File.Exists(Path.Combine(mods, "other_mod.zip")), "other mods are left alone");
        Assert.False(File.Exists(Path.Combine(mods, "simdeck.zip.new")));
        var after = Make().Check();
        Assert.True(after.UpToDate);
        Assert.False(after.NeedsInstall);
        Assert.Contains("up to date", after.Summary);
    }

    [Fact]
    public void A_newer_mod_is_not_downgraded_and_versions_compare_numerically()
    {
        var mods = Path.Combine(_root, "BeamNG", "BeamNG.drive", "current", "mods");
        Directory.CreateDirectory(mods);
        FakeMod(Path.Combine(mods, "simdeck.zip"), "99.0.0");
        var st = Make().Check();
        Assert.False(st.NeedsInstall);
        Assert.Contains("newer", st.Summary);
        Assert.True(ModInstaller.Compare("1.10.0", "1.9.9") > 0);
        Assert.Equal(0, ModInstaller.Compare("1.4", "1.4.0"));
        Assert.True(ModInstaller.Compare("1.3.0", "1.4.0") < 0);
    }

    [Fact]
    public void No_user_folder_means_no_install()
    {
        var r = Make().Install();
        Assert.False(r.Ok);
        Assert.Contains("user folder was not found", r.Message);
        Assert.False(Make().Check().NeedsInstall);
    }
}
