using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using SimDeck.Companion.BeamNG;
using SimDeck.Companion.Config;
using SimDeck.Companion.Input;
using SimDeck.Companion.Protocol;

namespace SimDeck.Companion.Tests;

/// <summary>SimDeck 1.1: vehicle features, analog BeamNG actions, hydraulics, vJoy configuration checks, foreground app.</summary>
public sealed class SimDeckFeatureTests : IDisposable
{
    readonly RecordingOutputs _out = new();
    readonly CompanionHost _host;
    readonly ConfigStore _store;
    string? _foreground = "explorer.exe";

    static int FreeTcp() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); int p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
    static int FreeUdp() { using var u = new UdpClient(0); return ((IPEndPoint)u.Client.LocalEndPoint!).Port; }

    public SimDeckFeatureTests()
    {
        _store = new ConfigStore(Path.Combine(Path.GetTempPath(), $"sd-host-{Guid.NewGuid():N}.json"));
        var c = _store.Config;
        c.ServerName = "TEST";
        c.WsPort = FreeTcp(); c.UdpPort = FreeUdp(); c.DiscoveryPort = FreeUdp(); c.BeamNgPort = FreeTcp(); c.OutGaugePort = FreeUdp();
        c.EnableMdns = false;
        _out.Describer = d => d == 1 ? new VJoyDeviceInfo(1, 8, new[] { "X", "Y", "Z", "RX", "RY", "RZ" }) : null;
        _host = new CompanionHost(_store, new InputRouter(_out, _out, _out),
            new HostOptions { Bind = IPAddress.Loopback, ForegroundApp = () => _foreground });
        _host.Pairing.UsePin("424242");
        _host.Start();
    }

    public void Dispose() => _host.Dispose();

    sealed class Phone : IAsyncDisposable
    {
        public readonly ClientWebSocket Ws = new();
        readonly List<JsonObject> _inbox = new();

        public async Task Connect(int port)
        {
            await Ws.ConnectAsync(new Uri($"ws://127.0.0.1:{port}/ws"), CancellationToken.None);
            _ = Task.Run(async () =>
            {
                var buf = new byte[1 << 20];
                try
                {
                    while (Ws.State == WebSocketState.Open)
                    {
                        int n = 0;
                        WebSocketReceiveResult r;
                        do { r = await Ws.ReceiveAsync(new ArraySegment<byte>(buf, n, buf.Length - n), CancellationToken.None); n += r.Count; }
                        while (!r.EndOfMessage);
                        if (r.MessageType == WebSocketMessageType.Close) break;
                        if (JsonNode.Parse(Encoding.UTF8.GetString(buf, 0, n)) is JsonObject o) lock (_inbox) _inbox.Add(o);
                    }
                }
                catch { }
            });
        }

        public Task Send(string json) => Ws.SendAsync(Encoding.UTF8.GetBytes(json), WebSocketMessageType.Text, true, CancellationToken.None);

        public async Task<JsonObject> Expect(string type, int ms = 3000, Func<JsonObject, bool>? where = null)
        {
            var until = DateTime.UtcNow.AddMilliseconds(ms);
            while (DateTime.UtcNow < until)
            {
                lock (_inbox)
                {
                    var m = _inbox.FirstOrDefault(x => (string?)x["type"] == type && (where == null || where(x)));
                    if (m != null) { _inbox.Remove(m); return m; }
                }
                await Task.Delay(10);
            }
            throw new TimeoutException($"no {type}");
        }

        public async ValueTask DisposeAsync()
        {
            try { await Ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "", CancellationToken.None); } catch { }
            Ws.Dispose();
        }
    }

    async Task<Phone> Pair()
    {
        var p = new Phone();
        await p.Connect(_store.Config.WsPort);
        await p.Send($$"""{"v":1,"type":"hello","deviceId":"d1","deviceName":"T","appVersion":"1","pin":"{{_host.Pairing.Pin}}"}""");
        await p.Expect("welcome");
        return p;
    }

    [Fact]
    public async Task Vjoy_configuration_problems_are_reported_on_profile_sync()
    {
        await using var p = await Pair();
        await p.Send("""
            {"v":1,"type":"profile.sync","profileId":"dozer","game":"custom","bindings":[
              {"id":"start","kind":"momentary","action":{"type":"vjoy_button","device":1,"button":1}},
              {"id":"rip","kind":"latching","action":{"type":"vjoy_button","device":1,"button":24}},
              {"id":"blade.x","kind":"slider","action":{"type":"vjoy_axis","device":1,"axis":"SL1"}},
              {"id":"hid","kind":"momentary","action":{"type":"vjoy_button","device":1,"button":40},"route":"hid"}]}
            """);
        await p.Expect("profile.ack");
        var buttons = await p.Expect("notice", where: m => ((string?)m["message"])!.Contains("buttons up to 24"));
        Assert.Equal("vjoy_config", (string?)buttons["code"]);
        Assert.Contains("has 8 buttons", (string?)buttons["message"]);
        var axes = await p.Expect("notice", where: m => ((string?)m["message"])!.Contains("SL1"));
        Assert.DoesNotContain("SL0", (string?)axes["message"]);
    }

    [Fact]
    public async Task Capabilities_describe_the_vjoy_devices()
    {
        await using var p = new Phone();
        await p.Connect(_store.Config.WsPort);
        await p.Send($$"""{"v":1,"type":"hello","deviceId":"d2","deviceName":"T","pin":"{{_host.Pairing.Pin}}"}""");
        var w = await p.Expect("welcome");
        var info = w["capabilities"]!["vjoyInfo"]!.AsArray();
        Assert.Equal(8, (int)info[0]!["buttons"]!);
        Assert.Equal(6, info[0]!["axes"]!.AsArray().Count);
    }

    [Fact]
    public async Task Foreground_app_is_sent_on_connect_and_on_change()
    {
        await using var p = await Pair();
        Assert.Equal("explorer.exe", (string?)(await p.Expect("foreground"))["exe"]);
        _foreground = "BeamNG.drive.x64.exe";
        Assert.Equal("BeamNG.drive.x64.exe", (string?)(await p.Expect("foreground", 3000))["exe"]);
    }

    [Fact]
    public async Task Vehicle_features_and_actions_are_relayed_and_drive_the_mod()
    {
        await using var p = await Pair();
        using var mod = new TcpClient();
        await mod.ConnectAsync(IPAddress.Loopback, _store.Config.BeamNgPort);
        var w = new StreamWriter(mod.GetStream()) { AutoFlush = true, NewLine = "\n" };
        var r = new StreamReader(mod.GetStream());
        await r.ReadLineAsync(); // state.request
        await w.WriteLineAsync("""{"v":1,"type":"hello","role":"beamng","modVersion":"1.1.0"}""");
        await w.WriteLineAsync("""{"v":1,"type":"features","vehicle":"pickup","list":[{"id":"action.bed_tilt","title":"Tilt bed","kind":"axis","centered":true,"source":"input_actions"},{"id":"hydro.dump_bed","title":"Dump bed","kind":"axis","source":"hydro"}],"diag":{"vehicle":"pickup","couplers":0}}""");
        var f = await p.Expect("beamng.features");
        Assert.Equal("pickup", (string?)f["vehicle"]);
        Assert.Equal(2, f["list"]!.AsArray().Count);
        Assert.Equal("pickup", (string?)f["diag"]!["vehicle"]);
        await w.WriteLineAsync("""{"v":1,"type":"actions","vehicle":"pickup","list":[{"name":"toggle_tailgate","title":"Tailgate","vehicle":true}]}""");
        Assert.Single((await p.Expect("beamng.actions"))["list"]!.AsArray());

        // a slider on an analog action and a held hydraulic
        await p.Send("""{"v":1,"type":"profile.sync","profileId":"auto","game":"beamng","bindings":[{"id":"tilt","kind":"slider","action":{"type":"beamng","command":"action.bed_tilt"}},{"id":"bed","kind":"slider","action":{"type":"beamng","command":"hydro.dump_bed"}}]}""");
        await p.Expect("profile.ack");
        await p.Send("""{"v":1,"type":"input","s":1,"id":"tilt","val":-0.4}""");
        await p.Send("""{"v":1,"type":"input","s":2,"id":"bed","val":0.7}""");
        var got = new List<(string, double)>();
        for (int i = 0; i < 12 && got.Count < 2; i++)
        {
            var m = JsonNode.Parse((await ReadLine(r))!)!;
            if ((string?)m["type"] == "command") got.Add(((string)m["command"]!, (double)m["value"]!));
        }
        Assert.Contains(("action.bed_tilt", -0.4), got);
        Assert.Contains(("hydro.dump_bed", 0.7), got);

        // the phone goes away: the hydraulic and the analog action get their 0
        await p.DisposeAsync();
        var zeros = new List<string>();
        for (int i = 0; i < 12 && zeros.Count < 2; i++)
        {
            var m = JsonNode.Parse((await ReadLine(r))!)!;
            if ((string?)m["type"] == "command" && (double)m["value"]! == 0) zeros.Add((string)m["command"]!);
        }
        Assert.Contains("hydro.dump_bed", zeros);
        Assert.Contains("action.bed_tilt", zeros);
    }

    static async Task<string?> ReadLine(StreamReader r)
    {
        using var cts = new CancellationTokenSource(3000);
        return await r.ReadLineAsync(cts.Token);
    }

    [Fact]
    public void Hydraulics_and_couplers_classify()
    {
        Assert.True(BeamNgCommands.IsHeld("hydro.dump_bed"));
        Assert.Equal(CommandStyle.Level, BeamNgCommands.Classify("hydro.dump_bed"));
        Assert.Equal(CommandStyle.Edge, BeamNgCommands.Classify("coupler.tailgateCoupler.toggle"));
    }
}
