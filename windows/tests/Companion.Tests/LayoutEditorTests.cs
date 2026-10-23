using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json.Nodes;
using SimDeck.Companion;
using SimDeck.Companion.Config;
using SimDeck.Companion.Input;
using SimDeck.Companion.Layout;
using Xunit;

namespace SimDeck.Companion.Tests;

/// <summary>SimDeck 1.3: editing a phone's layout from the PC (LayoutDoc and the profile messages).</summary>
public class LayoutEditorTests
{
    static JsonObject Sample() => (JsonObject)JsonNode.Parse("""
        {"id":"p-1","name":"Rig","game":"custom","schema":2,"unknownField":{"keep":true},
         "pages":[{"id":"a","name":"Main","columns":4,"rows":2,"controls":[
           {"id":"h","type":"momentary","label":"Horn","x":0,"y":0,"w":1,"h":1,"action":{"type":"key","keys":["H"]}},
           {"id":"s","type":"slider","label":"Throttle","x":3,"y":0,"w":1,"h":2}]}]}
        """)!;

    [Fact]
    public void Moves_and_resizes_stay_in_the_grid_with_minimum_spans()
    {
        var d = new LayoutDoc(Sample(), "k");
        d.Move(0, "h", 9, 9);
        var h = d.Find(0, "h")!;
        Assert.Equal(3, (int)h["x"]!); Assert.Equal(1, (int)h["y"]!);
        d.Resize(0, "s", 1, 1);
        Assert.Equal(2, (int)d.Find(0, "s")!["h"]!); // a vertical slider keeps two cells
        Assert.True(d.Dirty);
        Assert.True(d.Undo()); Assert.True(d.Undo());
        Assert.False(d.Dirty);
        Assert.True(d.Redo());
        Assert.Equal(3, (int)d.Find(0, "h")!["x"]!);
    }

    [Fact]
    public void Add_copy_paste_and_duplicate_use_free_spots_and_new_ids()
    {
        var d = new LayoutDoc(Sample(), "k");
        var id = d.Add(0, "latching")!;
        var c = d.Find(0, id)!;
        Assert.Equal(1, (int)c["x"]!); Assert.Equal(0, (int)c["y"]!);
        Assert.Equal("amber", (string?)c["accent"]);
        var clip = d.Copy(0, new[] { "h" });
        var pasted = d.Paste(0, clip);
        Assert.Single(pasted);
        Assert.NotEqual("h", pasted[0]);
        Assert.Equal("H", (string?)d.Find(0, pasted[0])!["action"]!["keys"]![0]);
        Assert.NotNull(d.Duplicate(0, "h"));
        // 4x2 page: horn, toggle, paste, duplicate on row 0-ish, slider takes column 3; a joystick (2x2) has no room left.
        Assert.Null(d.Add(0, "joystick"));
        var pg = d.PastePage(d.PageAt(0));
        Assert.Equal(2, d.PageCount);
        Assert.Equal("Main copy", d.PageName(pg));
        Assert.DoesNotContain(d.Controls(pg), x => (string?)x["id"] == "h");
    }

    [Fact]
    public void Unknown_fields_survive_and_the_schema_rises_for_newer_fields()
    {
        var d = new LayoutDoc(Sample(), "k");
        d.SetProfileField("theme", "heavy");
        var outp = d.ForPhone();
        Assert.True((bool)outp["unknownField"]!["keep"]!);
        Assert.Equal(4, (int)outp["schema"]!);
        d.SetField(0, "h", "backlight", "blue");
        Assert.Equal(5, (int)d.ForPhone()["schema"]!);
        d.SetPageField(0, "columns", 2);
        Assert.Equal(4, (int)d.PageAt(0)["columns"]!); // grids never shrink below 4 columns
    }

    [Fact]
    public void Skins_resolve_like_the_phone()
    {
        Assert.Equal("lit-push", LayoutCatalog.ResolveSkin("momentary", null, "airliner", null, false, "Nav", null));
        Assert.Equal("hydraulic-lever", LayoutCatalog.ResolveSkin("slider", null, "race", "bipolar", true, "Boom", null));
        Assert.Equal("quadrant-knob", LayoutCatalog.ResolveSkin("slider", null, "airliner", "unipolar", false, "Flaps", "flaps"));
        Assert.Equal("ring-push", LayoutCatalog.ResolveSkin("momentary", null, "roadcar", null, false, "Hazard", "hazard"));
        Assert.Equal("toggle-bat", LayoutCatalog.ResolveSkin("latching", "toggle-bat", "heavy", null, false, "X", null));
    }

    static JsonObject Fixture(string name) =>
        (JsonObject)JsonNode.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", name)))!;

    [Fact]
    public void Real_phone_profiles_round_trip_and_controls_copy_between_them()
    {
        // The fixtures are the phone's own export of two built-in profiles (android core ProfileJson.export).
        var util = new LayoutDoc(Fixture("builtin-utility.json"), "k");
        var msfs = new LayoutDoc(Fixture("builtin-msfs.json"), "k");
        Assert.Equal("heavy", util.Theme);
        Assert.Equal("airliner", msfs.Theme);
        Assert.Equal(util.Profile.ToJsonString(), util.ForPhone().ToJsonString()); // nothing lost or reordered when nothing changed
        foreach (var d in new[] { util, msfs })
            for (int p = 0; p < d.PageCount; p++)
                foreach (var c in d.Controls(p))
                {
                    Assert.InRange(LayoutDoc.Num(c, "x", -1) + LayoutDoc.Num(c, "w", 1), 1, d.Columns(p));
                    Assert.InRange(LayoutDoc.Num(c, "y", -1) + LayoutDoc.Num(c, "h", 1), 1, d.Rows(p));
                    Assert.Contains(LayoutCatalog.ResolveSkin((string)c["type"]!, (string?)c["skin"], d.PageTheme(p), (string?)c["range"], false, (string?)c["label"], (string?)c["icon"]),
                        LayoutCatalog.SkinsByType[(string)c["type"]!]);
                }
        // A lever from the excavator onto a new page of the flight profile keeps its action and stations, with a fresh id.
        var lever = util.Controls(1).First(c => (string?)c["type"] == "slider");
        var page = msfs.AddPage();
        var ids = msfs.Paste(page, util.Copy(1, new[] { (string)lever["id"]! }));
        var pasted = msfs.Find(page, Assert.Single(ids))!;
        Assert.NotEqual((string?)lever["id"], (string?)pasted["id"]);
        Assert.Equal(lever["action"]!.ToJsonString(), pasted["action"]!.ToJsonString());
        Assert.True((int)msfs.ForPhone()["schema"]! >= 4);
    }

    static int FreeTcp() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); int p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
    static int FreeUdp() { using var u = new UdpClient(0); return ((IPEndPoint)u.Client.LocalEndPoint!).Port; }

    [Fact]
    public async Task The_host_lists_fetches_and_saves_phone_profiles()
    {
        var store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"sd-layout-{Guid.NewGuid():N}.json"));
        var c = store.Config;
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp(); c.EnableMdns = false;
        var o = new RecordingOutputs();
        using var host = new CompanionHost(store, new InputRouter(o, o, o), new HostOptions { Bind = IPAddress.Loopback });
        host.Pairing.UsePin("111111");
        host.Start();

        JsonArray? listed = null; JsonObject? received = null; (string id, bool ok)? saved = null;
        host.ProfilesListed += (_, l) => listed = l;
        host.ProfileReceived += (_, p) => received = p;
        host.ProfileSaved += (_, id, ok, _) => saved = (id, ok);

        using var ws = new ClientWebSocket();
        await ws.ConnectAsync(new Uri($"ws://127.0.0.1:{c.WsPort}/ws"), CancellationToken.None);
        Task Send(string s) => ws.SendAsync(Encoding.UTF8.GetBytes(s), WebSocketMessageType.Text, true, CancellationToken.None);
        var inbox = new List<JsonObject>();
        _ = Task.Run(async () =>
        {
            var buf = new byte[1 << 20];
            try
            {
                while (ws.State == WebSocketState.Open)
                {
                    int n = 0; WebSocketReceiveResult r;
                    do { r = await ws.ReceiveAsync(new ArraySegment<byte>(buf, n, buf.Length - n), CancellationToken.None); n += r.Count; } while (!r.EndOfMessage);
                    if (JsonNode.Parse(Encoding.UTF8.GetString(buf, 0, n)) is JsonObject m) lock (inbox) inbox.Add(m);
                }
            }
            catch { }
        });
        async Task<JsonObject> Expect(string type)
        {
            for (int i = 0; i < 300; i++)
            {
                lock (inbox) { var m = inbox.FirstOrDefault(x => (string?)x["type"] == type); if (m != null) { inbox.Remove(m); return m; } }
                await Task.Delay(10);
            }
            throw new TimeoutException(type);
        }
        await Send("""{"v":1,"type":"hello","deviceId":"d","deviceName":"P","appVersion":"1.3.0","pin":"111111"}""");
        await Expect("welcome");
        await Expect("profiles.request"); // asked for right after pairing
        var key = host.Sessions.Single().SessionKey;

        await Send("""{"v":1,"type":"profiles.list","list":[{"id":"p-1","name":"Rig","game":"custom","builtIn":false,"pages":1}]}""");
        Assert.True(SpinWait.SpinUntil(() => listed != null, 3000));
        Assert.Equal("p-1", (string?)listed![0]!["id"]);

        Assert.True(host.RequestProfile(key, "p-1"));
        Assert.Equal("p-1", (string?)(await Expect("profile.get"))["id"]);
        await Send("{\"v\":1,\"type\":\"profile.data\",\"profile\":" + Sample().ToJsonString() + "}");
        Assert.True(SpinWait.SpinUntil(() => received != null, 3000));

        var doc = new LayoutDoc(received!, key);
        doc.Move(0, "h", 1, 1);
        Assert.True(host.PutProfile(key, doc.ForPhone()));
        var put = await Expect("profile.put");
        Assert.Equal(1, (int)put["profile"]!["pages"]![0]!["controls"]![0]!["x"]!);
        await Send("""{"v":1,"type":"profile.saved","id":"p-1","ok":true,"message":"Saved on the phone"}""");
        Assert.True(SpinWait.SpinUntil(() => saved != null, 3000));
        Assert.Equal(("p-1", true), saved!.Value);
        Assert.False(host.RequestProfiles("no-such-session"));
    }
}
