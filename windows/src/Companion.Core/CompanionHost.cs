using System.Collections.Concurrent;
using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using SimDeck.Companion.BeamNG;
using SimDeck.Companion.Config;
using SimDeck.Companion.Engine;
using SimDeck.Companion.Input;
using SimDeck.Companion.Net;
using SimDeck.Companion.Protocol;

namespace SimDeck.Companion;

public enum ServiceState { Off, Running, Failed }

/// <summary>Live status of one listener, for the UI.</summary>
public sealed record ServiceStatus(string Name, ServiceState State, string Detail);

/// <summary>A log line for the UI and the console host.</summary>
public sealed record LogEntry(DateTimeOffset At, string Level, string Message);

public sealed class HostOptions
{
    /// <summary>Optional platform mDNS registration (Windows DnsServiceRegister), used if the built-in responder cannot start.</summary>
    public Func<CompanionConfig, IDisposable?>? PlatformMdns { get; init; }
    public IDeviceIdentity Identity { get; init; } = new DefaultDeviceIdentity();
    public IPAddress Bind { get; init; } = IPAddress.Any;
    /// <summary>The phone pings every 2 s; after this much silence its session is closed and released.</summary>
    public TimeSpan IdleTimeout { get; init; } = TimeSpan.FromSeconds(8);
    /// <summary>Finds the phone's Bluetooth HID controller (Windows: SetupAPI); none elsewhere.</summary>
    public IBluetoothPads BluetoothPads { get; init; } = new NoBluetoothPads();
    /// <summary>How often the Bluetooth pad scan runs, on its own background task.</summary>
    public TimeSpan BluetoothScanInterval { get; init; } = TimeSpan.FromSeconds(2);
    /// <summary>The executable of the window in front (e.g. "BeamNG.drive.x64.exe"), for automatic profile switching. Null: not reported.</summary>
    public Func<string?>? ForegroundApp { get; init; }
    /// <summary>Installs and updates the BeamNG mod (1.4.1). Null: no mod management (the default off Windows and in tests).</summary>
    public ModInstaller? ModInstaller { get; init; }

    /// <summary>These options plus a mod installer.</summary>
    public HostOptions WithModInstaller(ModInstaller installer) => new()
    {
        PlatformMdns = PlatformMdns, Identity = Identity, Bind = Bind, IdleTimeout = IdleTimeout, BluetoothPads = BluetoothPads,
        BluetoothScanInterval = BluetoothScanInterval, ForegroundApp = ForegroundApp, ModInstaller = installer,
    };
}

/// <summary>
/// The companion: owns every listener, every phone session, the BeamNG link and the
/// telemetry relay. The tray app and the console host are thin shells around this.
/// </summary>
public sealed class CompanionHost : IDisposable
{
    const int MaxWsMessage = 1 << 20;
    static readonly TimeSpan HelloTimeout = TimeSpan.FromSeconds(10);
    static readonly TimeSpan OutGaugeStale = TimeSpan.FromSeconds(3);

    readonly ConfigStore _store;
    readonly HostOptions _options;
    readonly ConcurrentDictionary<string, PhoneSession> _sessions = new();
    readonly List<IDisposable> _services = new();
    readonly object _stateGate = new();
    readonly Dictionary<string, JsonNode?> _beamngState = new();
    readonly Dictionary<string, JsonNode?> _outgaugeState = new();
    readonly Dictionary<string, JsonNode?> _outgaugePending = new();
    readonly ConcurrentDictionary<string, ServiceStatus> _status = new();
    IReadOnlyList<string> _beamngCommands = Array.Empty<string>();
    JsonObject? _beamngFeatures;   // last "beamng.features" message, sent to phones that connect later
    JsonObject? _beamngActions;    // last "beamng.actions" message
    JsonObject? _beamngReadings;   // last "beamng.readings" message (1.4)
    JsonObject? _beamngTuning;     // last "beamng.tuning" message (1.4)
    string? _lastWatch;            // last readings.watch sent to the mod
    string? _foreground;
    long _lastOutGaugeMs = long.MinValue / 2, _lastOutGaugeFlushMs;
    bool _outgaugeWasActive;
    long _lastOutGaugeAnyMs = long.MinValue / 2;
    IPAddress? _lastOutGaugeFrom;
    int _loggedForeignOutGauge, _loggedShortOutGauge;
    string _lastCapabilities = "";
    Timer? _timer;
    int _tickCount;
    int _ticking;
    IReadOnlyList<BluetoothPad> _pads = Array.Empty<BluetoothPad>();
    string _padsKey = "";
    readonly object _padsGate = new();
    readonly CancellationTokenSource _stop = new();
    Task? _padScan;
    IReadOnlySet<IPAddress> _localAddresses = new HashSet<IPAddress>();
    long _localAddressesAtMs = long.MinValue;

    public InputRouter Router { get; }
    public Pairing Pairing { get; }
    public BeamNgLink? BeamNg { get; private set; }

    // ---------------------------------------------------------------- BeamNG mod install (1.4.1)

    /// <summary>The last check of the game's mods folder; null before the first one or without an installer.</summary>
    public ModStatus? Mod { get; private set; }
    public ModInstaller? ModInstaller => _options.ModInstaller;
    /// <summary>Raised (on the thread pool) after every mod check or install.</summary>
    public event Action? ModChanged;
    int _modChecking;

    /// <summary>
    /// Looks at the game's mods folder and, when automatic install is on, installs or updates the built-in mod while the game
    /// is closed. Runs on the thread pool; overlapping calls are skipped.
    /// </summary>
    public void CheckMod(bool install = false)
    {
        if (_options.ModInstaller is not { } inst || Interlocked.Exchange(ref _modChecking, 1) == 1) return;
        _ = Task.Run(() =>
        {
            try
            {
                var st = inst.Check();
                bool auto = Config.AutoInstallBeamNgMod && st.NeedsInstall && !st.GameRunning;
                if (install || auto)
                {
                    var r = inst.Install();
                    Log(r.Ok ? "info" : "warn", (install ? "" : "Automatic: ") + r.Message);
                    LastModResult = r;
                    st = inst.Check();
                }
                Mod = st;
                ModChanged?.Invoke();
            }
            catch (Exception e) { Log("warn", $"BeamNG mod check failed: {e.Message}"); }
            finally { Volatile.Write(ref _modChecking, 0); }
        });
    }

    /// <summary>The last install's result, for the Settings page.</summary>
    public ModInstallResult? LastModResult { get; private set; }
    public ConfigStore Store => _store;
    public CompanionConfig Config => _store.Config;

    public IReadOnlyCollection<PhoneSession> Sessions => _sessions.Values.ToList();
    /// <summary>SimDeck Pad devices connected over Bluetooth right now (refreshed every 2 s).</summary>
    public IReadOnlyList<BluetoothPad> BluetoothPads => Volatile.Read(ref _pads);
    /// <summary>Sessions and pads paired up, one entry per phone (see <see cref="PhoneLinks.Merge"/>).</summary>
    public List<LinkedPhone> Phones() => PhoneLinks.Merge(Sessions.OrderBy(s => s.DeviceName).ToList(), BluetoothPads);
    /// <summary>A Bluetooth pad connected or disconnected.</summary>
    public event Action? BluetoothChanged;
    public IReadOnlyCollection<ServiceStatus> Services => _status.Values.OrderBy(s => s.Name).ToList();
    /// <summary>The last OutGauge packet of any kind (used or not): how long ago, and where from. Null before the first.</summary>
    public (TimeSpan Ago, IPAddress From)? OutGaugeLastPacket =>
        Volatile.Read(ref _lastOutGaugeFrom) is { } from
            ? (TimeSpan.FromMilliseconds(Environment.TickCount64 - Interlocked.Read(ref _lastOutGaugeAnyMs)), from)
            : null;

    /// <summary>True when the OutGauge listener could not open its port (usually another program holds it).</summary>
    public bool OutGaugeFailed => _status.TryGetValue(OutGaugeService, out var st) && st.State == ServiceState.Failed;

    public const string OutGaugeService = "OutGauge telemetry";

    public bool OutGaugeActive => Environment.TickCount64 - Interlocked.Read(ref _lastOutGaugeMs) < OutGaugeStale.TotalMilliseconds;

    public event Action<LogEntry>? Logged;
    public event Action? SessionsChanged;
    /// <summary>A phone sent its profile list (PROTOCOL.md, Layout editing from the PC).</summary>
    public event Action<PhoneSession, JsonArray>? ProfilesListed;
    /// <summary>A phone sent one profile for editing.</summary>
    public event Action<PhoneSession, JsonObject>? ProfileReceived;
    /// <summary>A phone answered a profile.put: (session, profile id, ok, message).</summary>
    public event Action<PhoneSession, string, bool, string>? ProfileSaved;
    public event Action? StatusChanged;
    public event Action<IReadOnlyDictionary<string, JsonNode?>, string>? GameState;

    public CompanionHost(ConfigStore store, InputRouter router, HostOptions? options = null)
    {
        _store = store;
        Router = router;
        _options = options ?? new HostOptions();
        Pairing = new Pairing(store);
        // A phone leaving takes its watched readings with it (1.4).
        SessionsChanged += PushWatch;
    }

    // ---------------------------------------------------------------- lifecycle

    public void Start()
    {
        var c = Config;
        StartService("Phone link (WebSocket)", $"TCP {c.WsPort}", () =>
        {
            var ws = new WsServer(_options.Bind, c.WsPort, "/ws", HandlePhone);
            ws.Start();
            return ws;
        });
        StartService("Control events (UDP)", $"UDP {c.UdpPort}", () =>
        {
            var u = new UdpLoop(_options.Bind, c.UdpPort, OnUdpInput);
            u.Start();
            return u;
        });
        StartService("Discovery (broadcast)", $"UDP {c.DiscoveryPort}", () =>
        {
            var d = new DiscoveryResponder(c.DiscoveryPort, DiscoverReply);
            d.Start();
            return d;
        });
        if (c.EnableMdns)
            StartService("Discovery (mDNS)", $"{ProtocolInfo.MdnsService}", () =>
            {
                var txt = new[] { $"v={ProtocolInfo.Version}", $"name={c.ServerName}", $"udp={c.UdpPort}" };
                var m = new MdnsAdvertiser(c.ServerName, ProtocolInfo.MdnsService, (ushort)c.WsPort, txt);
                try
                {
                    m.Log += s => Log("warn", s);
                    m.Start();
                    return m;
                }
                catch (Exception e) when (_options.PlatformMdns != null)
                {
                    // UDP 5353 can be held exclusively by another responder; hand the job to the OS.
                    m.Dispose();
                    Log("info", $"Built-in mDNS responder unavailable ({e.Message}); using the system one");
                    return _options.PlatformMdns(c) ?? throw new InvalidOperationException(e.Message, e);
                }
            });
        StartService("BeamNG mod link", $"TCP 127.0.0.1:{c.BeamNgPort}", () =>
        {
            var link = new BeamNgLink(c.BeamNgPort);
            link.Log += s => Log("info", s);
            link.ConnectionChanged += _ =>
            {
                if (!link.Connected) OnBeamNgDisconnected(); else { _lastWatch = WatchText(Array.Empty<string>(), false); PushWatch(); }
                PushCapabilities(); StatusChanged?.Invoke();
            };
            link.StateReceived += OnBeamNgState;
            link.CommandsReceived += OnBeamNgCommands;
            link.FeaturesReceived += OnBeamNgFeatures;
            link.ActionsReceived += OnBeamNgActions;
            link.ModMessage += OnBeamNgModMessage;
            // Relayed unchanged (ok, written, message, ...); a malformed "ok" only affects the log level.
            link.BindingsResult += r =>
            {
                bool ok = r["ok"] is JsonValue v && v.TryGetValue<bool>(out var b) && b;
                Log(ok ? "info" : "warn", $"BeamNG bindings: {r["message"]}");
                Broadcast(r);
            };
            link.Start();
            BeamNg = link;
            return link;
        });
        if (c.EnableOutGauge)
            StartService(OutGaugeService, $"UDP {c.OutGaugePort}", () =>
            {
                var u = new UdpLoop(IPAddress.Any, c.OutGaugePort, OnOutGauge);
                u.Start();
                return u;
            });

        _lastCapabilities = Capabilities().ToJsonString();
        _timer = new Timer(_ => Tick(), null, 100, 100);
        _padScan = Task.Run(() => ScanBluetoothLoop(_stop.Token));
        Log("info", $"Companion ready as \"{c.ServerName}\". The pairing PIN is shown in the window (never written to the log)");
        if (_store.LoadNote is { } note) Log(_store.WriteBlocked != null || note.Contains("could not") ? "warn" : "info", note);
    }

    void StartService(string name, string detail, Func<IDisposable> start)
    {
        try
        {
            _services.Add(start());
            _status[name] = new ServiceStatus(name, ServiceState.Running, detail);
        }
        catch (Exception e)
        {
            _status[name] = new ServiceStatus(name, ServiceState.Failed, $"{detail}: {e.Message}");
            Log("error", $"{name} could not start on {detail}: {e.Message}");
        }
        StatusChanged?.Invoke();
    }

    void Tick()
    {
        // The timer fires on the thread pool: skip a tick that would overlap a slow one, and never let
        // an exception escape (an unhandled one there ends the process).
        if (Interlocked.Exchange(ref _ticking, 1) == 1) return;
        try { TickCore(); }
        catch (Exception e) { Log("error", $"Background tick failed: {e.Message}"); }
        finally { Volatile.Write(ref _ticking, 0); }
    }

    void TickCore()
    {
        long now = Environment.TickCount64;
        foreach (var s in _sessions.Values) s.Engine.Tick(now);
        DropIdleSessions();
        FlushOutGauge(now);

        bool active = OutGaugeActive;
        if (active != _outgaugeWasActive)
        {
            _outgaugeWasActive = active;
            Log("info", active ? "OutGauge telemetry is arriving" : "OutGauge telemetry stopped");
            PushCapabilities();
            StatusChanged?.Invoke();
        }

        if (_tickCount % 10 == 0) PollForeground();
        // The mod: checked every 10 s, so an update lands as soon as the game closes.
        if (_tickCount % 100 == 0) CheckMod();
        // Drivers can be installed or a vJoy device enabled while we run.
        if (++_tickCount % 50 == 0)
        {
            bool changed = false;
            try { changed |= Router.VJoy.Refresh(); } catch { }
            try { changed |= Router.XInput.Refresh(); } catch { }
            if (changed) { PushCapabilities(); StatusChanged?.Invoke(); }
        }
    }

    string? _lastScanError;

    /// <summary>
    /// The SetupAPI walk can take tens of milliseconds (more while Windows is adding a device), so it runs
    /// here rather than on the 100 ms input tick, where it would delay auto-release and idle checks.
    /// </summary>
    async Task ScanBluetoothLoop(CancellationToken stop)
    {
        using var timer = new PeriodicTimer(_options.BluetoothScanInterval);
        do
        {
            try { ScanBluetooth(); }
            catch (Exception e)
            {
                // Logged once per distinct failure, not every 2 s.
                if (e.Message != _lastScanError) Log("error", $"Bluetooth scan failed: {e.Message}");
                _lastScanError = e.Message;
                continue;
            }
            _lastScanError = null;
        }
        while (await timer.WaitForNextTickAsync(stop).ConfigureAwait(false));
    }

    /// <summary>
    /// Polls for the Bluetooth pad. Polling every 2 s keeps this testable and cheap (one SetupAPI walk);
    /// a pad that is switched off or out of range drops out because Windows removes its HID interface.
    /// </summary>
    public void ScanBluetooth()
    {
        IReadOnlyList<BluetoothPad> now, before;
        lock (_padsGate)
        {
            if (_stop.IsCancellationRequested) return; // shutting down: no late events
            now = _options.BluetoothPads.Present(); // a failure goes to the caller (the scan loop logs it)
            var key = string.Join("|", now.Select(p => p.Id).Order());
            if (key == _padsKey) return;
            before = _pads;
            _padsKey = key;
            Volatile.Write(ref _pads, now);
        }
        if (_stop.IsCancellationRequested) return;
        foreach (var p in now.Where(p => before.All(b => b.Id != p.Id)))
            Log("info", $"{p.Name} connected over Bluetooth (controls go straight to Windows; lamps and BeamNG commands need the Wi-Fi link)");
        foreach (var p in before.Where(b => now.All(x => x.Id != b.Id)))
            Log("info", $"{p.Name} disconnected from Bluetooth");
        if (!_stop.IsCancellationRequested) BluetoothChanged?.Invoke();
    }

    void DropIdleSessions()
    {
        var idle = _options.IdleTimeout;
        long cutoff = Environment.TickCount64 - (long)idle.TotalMilliseconds;
        foreach (var s in _sessions.Values.Where(x => Interlocked.Read(ref x.LastMessageTick) < cutoff).ToList())
        {
            if (!_sessions.TryRemove(s.SessionKey, out PhoneSession? _)) continue;
            s.Engine.ReleaseAll();
            Log("warn", $"{s.DeviceName} went silent for {idle.TotalSeconds:0.#} s; its held inputs were released");
            _ = s.CloseAsync("no messages (idle timeout)");
            SessionsChanged?.Invoke();
        }
    }

    int _disposed;

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) == 1) return;
        _timer?.Dispose();
        _stop.Cancel();
        bool ended = true;
        try { ended = _padScan?.Wait(1000) ?? true; } catch (AggregateException) { } // cancellation ends the loop
        if (ended) _stop.Dispose();
        else _padScan!.ContinueWith(_ => _stop.Dispose(), TaskScheduler.Default); // a scan stuck in SetupAPI
        foreach (var s in _sessions.Values) { s.Engine.ReleaseAll(); _ = s.CloseAsync("companion shutting down"); }
        Router.ReleaseAll();
        foreach (var svc in _services) { try { svc.Dispose(); } catch { } }
        _services.Clear();
    }

    public void Log(string level, string message)
    {
        // Each listener on its own: a failing log view or file must not break the caller (often a network loop).
        if (Logged is not { } handlers) return;
        var entry = new LogEntry(DateTimeOffset.Now, level, message);
        foreach (Action<LogEntry> h in handlers.GetInvocationList())
            try { h(entry); } catch { }
    }

    // ---------------------------------------------------------------- messages out

    public JsonObject Capabilities()
    {
        var devices = new JsonArray();
        foreach (var d in Router.VJoy.Available ? Router.VJoy.Devices : Array.Empty<int>()) devices.Add(d);
        return new JsonObject
        {
            ["keyboard"] = Router.Keyboard.Available,
            ["vjoy"] = Router.VJoy.Available && Router.VJoy.Devices.Count > 0,
            ["vjoyDevices"] = devices,
            ["vjoyInfo"] = VJoyInfo(),
            ["vigem"] = Router.XInput.Available,
            ["beamng"] = BeamNg?.Connected == true,
            ["outgauge"] = OutGaugeActive,
        };
    }

    JsonArray VJoyInfo()
    {
        var arr = new JsonArray();
        if (!Router.VJoy.Available) return arr;
        foreach (var d in Router.VJoy.Devices)
        {
            VJoyDeviceInfo? info = null;
            try { info = Router.VJoy.Describe(d); } catch { }
            if (info == null) continue;
            arr.Add(new JsonObject
            {
                ["device"] = info.Device, ["buttons"] = info.Buttons,
                ["axes"] = new JsonArray(info.Axes.Select(a => (JsonNode?)JsonValue.Create(a)).ToArray()),
            });
        }
        return arr;
    }

    /// <summary>
    /// Compares what a profile needs from vJoy with how the devices are configured. vJoy drops buttons above the
    /// device's button count and axes that are not ticked in "Configure vJoy" without any error, which looks like
    /// "the buttons do nothing" (the default device has only 8 buttons). Returns one message per problem.
    /// </summary>
    public List<string> CheckVJoy(IEnumerable<Binding> bindings)
    {
        var problems = new List<string>();
        var maxButton = new Dictionary<int, int>();
        var axes = new Dictionary<int, SortedSet<string>>();
        foreach (var b in bindings)
        {
            if (b.RoutedOverHid) continue;
            foreach (var a in new[] { b.Action, b.Cw, b.Ccw }.Concat(b.Positions))
            {
                if (a.Type == ActionType.VJoyButton) maxButton[a.Device] = Math.Max(maxButton.GetValueOrDefault(a.Device), a.Button);
                else if (a.Type == ActionType.VJoyAxis)
                {
                    if (!axes.TryGetValue(a.Device, out var set)) axes[a.Device] = set = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
                    set.Add(a.Axis.ToUpperInvariant());
                }
            }
        }
        var devices = maxButton.Keys.Concat(axes.Keys).Distinct().OrderBy(d => d).ToList();
        if (devices.Count == 0) return problems;
        if (!Router.VJoy.Available)
        {
            problems.Add("This profile uses vJoy buttons or axes, but vJoy is not installed on this PC. Install vJoy, or pair the phone over Bluetooth (SimDeck Pad) instead.");
            return problems;
        }
        foreach (var d in devices)
        {
            if (!Router.VJoy.Devices.Contains(d))
            {
                problems.Add($"vJoy device {d} is not enabled (or another program is using it). Enable it in \"Configure vJoy\".");
                continue;
            }
            VJoyDeviceInfo? info = null;
            try { info = Router.VJoy.Describe(d); } catch { }
            if (info == null) continue;
            if (maxButton.TryGetValue(d, out var need) && need > info.Buttons)
                problems.Add($"vJoy device {d} has {info.Buttons} buttons, but this profile uses buttons up to {need}: buttons {info.Buttons + 1}-{need} do nothing. " +
                             $"Open \"Configure vJoy\", set device {d} to {Math.Max(32, need)} buttons, apply, then reconnect the phone.");
            if (axes.TryGetValue(d, out var used))
            {
                var missing = used.Where(a => !info.Axes.Contains(a, StringComparer.OrdinalIgnoreCase)).ToList();
                if (missing.Count > 0)
                    problems.Add($"vJoy device {d} has no {string.Join(", ", missing)} axis, so those sliders and joysticks do nothing. " +
                                 "Open \"Configure vJoy\", tick all 8 axes for the device, apply, then reconnect the phone.");
            }
        }
        return problems;
    }

    readonly object _capsGate = new();

    void PushCapabilities()
    {
        // Called from the timer, the mod link and UI threads; the lock keeps the "changed?" check and
        // the broadcast together so phones never see capabilities go backwards.
        lock (_capsGate)
        {
            var caps = Capabilities();
            var text = caps.ToJsonString();
            if (text == _lastCapabilities) return;
            _lastCapabilities = text;
            var m = JsonExt.Msg("capabilities");
            m["capabilities"] = caps;
            Broadcast(m);
        }
    }

    JsonObject DiscoverReply()
    {
        var m = JsonExt.Msg("discover.reply");
        m["name"] = Config.ServerName;
        m["wsPort"] = Config.WsPort;
        m["udpPort"] = Config.UdpPort;
        return m;
    }

    // ---------------------------------------------------------------- layout editing from the PC

    public PhoneSession? Session(string key) => _sessions.TryGetValue(key, out var s) ? s : null;

    /// <summary>Asks a phone for its profile list; false when that phone is gone.</summary>
    public bool RequestProfiles(string sessionKey)
    {
        if (Session(sessionKey) is not { } s) return false;
        s.Send(JsonExt.Msg("profiles.request"));
        return true;
    }

    /// <summary>Asks a phone for one profile, to edit it.</summary>
    public bool RequestProfile(string sessionKey, string id)
    {
        if (Session(sessionKey) is not { } s) return false;
        var m = JsonExt.Msg("profile.get");
        m["id"] = id;
        s.Send(m);
        return true;
    }

    /// <summary>Sends an edited profile back to the phone, which saves it and answers profile.saved.</summary>
    public bool PutProfile(string sessionKey, JsonObject profile)
    {
        if (Session(sessionKey) is not { } s) return false;
        var m = JsonExt.Msg("profile.put");
        m["profile"] = profile.DeepClone();
        s.Send(m);
        Log("info", $"{s.DeviceName}: sent layout {(string?)profile["name"]} from the PC editor");
        return true;
    }

    void Broadcast(JsonObject msg)
    {
        foreach (var s in _sessions.Values) s.Send((JsonObject)msg.DeepClone());
    }

    // ---------------------------------------------------------------- phone sessions

    async Task HandlePhone(WebSocket ws, IPEndPoint remote, CancellationToken ct)
    {
        var addr = remote.Address.IsIPv4MappedToIPv6 ? remote.Address.MapToIPv4() : remote.Address;
        PhoneSession? session = null;
        var buffer = new byte[16 * 1024];
        try
        {
            using var helloCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            helloCts.CancelAfter(HelloTimeout);
            var first = await Receive(ws, buffer, helloCts.Token).ConfigureAwait(false);
            if (first == null) return;

            session = await Hello(ws, addr, first).ConfigureAwait(false);
            if (session == null) return;

            while (ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
            {
                var text = await Receive(ws, buffer, ct).ConfigureAwait(false);
                if (text == null) break;
                session.Touch();
                try { HandleMessage(session, text); }
                catch (Exception e)
                {
                    // One bad message (or a failing output driver) must not end the phone's session.
                    // Logged only: bad_message is reserved for malformed input from the phone.
                    Log("warn", $"Message from {session.DeviceName} failed: {e.Message}");
                }
            }
        }
        catch (Exception) { /* dropped connection */ }
        finally
        {
            // Skip when the session was already dropped (idle timeout, replaced, forgotten): it was released then.
            if (session != null && _sessions.TryRemove(session.SessionKey, out _))
            {
                session.Engine.ReleaseAll();
                Log("info", $"{session.DeviceName} disconnected; its held inputs were released");
                SessionsChanged?.Invoke();
            }
        }
    }

    /// <summary>Receives one complete text message, or null when the socket closes.</summary>
    static async Task<string?> Receive(WebSocket ws, byte[] buffer, CancellationToken ct)
    {
        using var ms = new MemoryStream();
        while (true)
        {
            var r = await ws.ReceiveAsync(buffer, ct).ConfigureAwait(false);
            if (r.MessageType == WebSocketMessageType.Close)
            {
                try { await ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None).ConfigureAwait(false); } catch { }
                return null;
            }
            ms.Write(buffer, 0, r.Count);
            if (ms.Length > MaxWsMessage) throw new InvalidDataException("message too large");
            if (r.EndOfMessage) break;
        }
        return Encoding.UTF8.GetString(ms.GetBuffer(), 0, (int)ms.Length);
    }

    static async Task SendRaw(WebSocket ws, JsonObject msg)
    {
        var bytes = Encoding.UTF8.GetBytes(msg.ToJsonString());
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        await ws.SendAsync(bytes, WebSocketMessageType.Text, true, cts.Token).ConfigureAwait(false);
    }

    async Task Reject(WebSocket ws, string code, string message)
    {
        try
        {
            await SendRaw(ws, JsonExt.Error(code, message)).ConfigureAwait(false);
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(2));
            await ws.CloseOutputAsync(WebSocketCloseStatus.PolicyViolation, code, cts.Token).ConfigureAwait(false);
        }
        catch (Exception) { }
    }

    async Task<PhoneSession?> Hello(WebSocket ws, IPAddress addr, string text)
    {
        JsonDocument doc;
        try { doc = JsonDocument.Parse(text); }
        catch (JsonException) { await Reject(ws, "bad_message", "expected hello").ConfigureAwait(false); return null; }
        using var docScope = doc;
        var m = doc.RootElement;
        if (m.Int("v", 1) > ProtocolInfo.Version)
        {
            await Reject(ws, "version_mismatch", $"companion speaks protocol v{ProtocolInfo.Version}").ConfigureAwait(false);
            return null;
        }
        if (m.Str("type") != "hello")
        {
            await Reject(ws, "not_paired", "send hello first").ConfigureAwait(false);
            return null;
        }
        var deviceId = m.Str("deviceId");
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            await Reject(ws, "bad_message", "hello needs deviceId").ConfigureAwait(false);
            return null;
        }
        var deviceName = m.Str("deviceName") ?? "Phone";
        var outcome = Pairing.Authenticate(deviceId, deviceName, m.Str("pin"), m.Str("token"), out var token);
        switch (outcome)
        {
            case PairOutcome.BadPin:
                Log("warn", $"Wrong PIN from {deviceName} ({addr})");
                await Reject(ws, "bad_pin", "That PIN does not match the one shown on the PC").ConfigureAwait(false);
                return null;
            case PairOutcome.BadToken:
                Log("warn", $"{deviceName} ({addr}) used an unknown token; it needs to pair again");
                await Reject(ws, "bad_token", "This phone is not paired with this PC any more. Pair again with the PIN.").ConfigureAwait(false);
                return null;
            case PairOutcome.RateLimited:
                await Reject(ws, "rate_limited", "Too many wrong PINs. Try again in a minute.").ConfigureAwait(false);
                return null;
            case PairOutcome.Missing:
                await Reject(ws, "not_paired", "Enter the PIN shown on the PC").ConfigureAwait(false);
                return null;
            case PairOutcome.SaveFailed:
                Log("error", $"Could not pair {deviceName}: {_store.Path} could not be written ({Pairing.SaveError}). Check the file is not read-only or locked, then try again.");
                await Reject(ws, "config_write_failed", "The PC could not save the pairing. Check the companion on the PC, then try again.").ConfigureAwait(false);
                return null;
        }

        var key = Pairing.NewSessionKey();
        while (_sessions.ContainsKey(key)) key = Pairing.NewSessionKey();
        var session = new PhoneSession(ws, key, deviceId, deviceName, addr,
            new BindingEngine(Router, (ICommandSink?)BeamNg ?? new NullCommandSink(), deviceName, deviceId));

        // The same phone reconnecting replaces its old session (and frees what it held).
        foreach (var old in _sessions.Values.Where(s => s.DeviceId == deviceId).ToList())
        {
            old.Engine.ReleaseAll();
            _sessions.TryRemove(old.SessionKey, out PhoneSession? _);
            _ = old.CloseAsync("replaced by a new connection");
        }

        var welcome = JsonExt.Msg("welcome");
        welcome["serverName"] = Config.ServerName;
        welcome["token"] = token;
        welcome["session"] = key;
        welcome["udpPort"] = Config.UdpPort;
        welcome["capabilities"] = Capabilities();
        await session.SendAsync(welcome).ConfigureAwait(false);
        // Registered only now, so no broadcast (state, capabilities) can reach the phone before its welcome.
        session.Touch();
        _sessions[key] = session;

        Log("info", $"{deviceName} connected from {addr}");
        SessionsChanged?.Invoke();
        // Ask for the phone's profiles up front, so the layout editor has them when it opens (phones before 1.3 ignore it).
        session.Send(JsonExt.Msg("profiles.request"));
        if (_beamngCommands.Count > 0) session.Send(CommandsMessage());
        if (_beamngFeatures is { } bf) session.Send((JsonObject)bf.DeepClone());
        if (_beamngActions is { } ba) session.Send((JsonObject)ba.DeepClone());
        if (_beamngReadings is { } br) session.Send((JsonObject)br.DeepClone());
        if (_beamngTuning is { } bt) session.Send((JsonObject)bt.DeepClone());
        if (_foreground is { } fg) session.Send(ForegroundMessage(fg));
        return session;
    }

    void HandleMessage(PhoneSession s, string text)
    {
        JsonDocument doc;
        try { doc = JsonDocument.Parse(text); }
        catch (JsonException) { s.Send(JsonExt.Error("bad_message", "malformed JSON")); return; }
        using var docScope = doc;
        var m = doc.RootElement;
        if (m.ValueKind != JsonValueKind.Object) { s.Send(JsonExt.Error("bad_message", "expected an object")); return; }

        switch (m.Str("type"))
        {
            case "ping":
            {
                var pong = JsonExt.Msg("pong");
                if (m.TryGetProperty("t", out var t) && t.ValueKind == JsonValueKind.Number) pong["t"] = t.GetDouble();
                s.Send(pong);
                break;
            }
            case "profile.sync": ProfileSync(s, m); break;
            case "input":
            {
                var id = m.Str("id");
                var seq = m.Long("s");
                var val = m.Num("val");
                if (id == null || seq == null || val == null) { s.Send(JsonExt.Error("bad_message", "input needs s, id and val")); break; }
                ApplyInput(s, id, seq.Value, val.Value, viaUdp: false);
                break;
            }
            case "beamng.bindings.apply": ForwardBindings(s, m); break;
            case "state.request": SendSnapshot(s); break;
            case "profiles.list":
                if (JsonNode.Parse(m.GetRawText()) is JsonObject pl && pl["list"] is JsonArray list) ProfilesListed?.Invoke(s, (JsonArray)list.DeepClone());
                break;
            case "profile.data":
                if (JsonNode.Parse(m.GetRawText()) is JsonObject pd && pd["profile"] is JsonObject prof) ProfileReceived?.Invoke(s, (JsonObject)prof.DeepClone());
                break;
            case "profile.saved":
                ProfileSaved?.Invoke(s, m.Str("id") ?? "", m.Bool("ok"), m.Str("message") ?? "");
                Log(m.Bool("ok") ? "info" : "warn", $"{s.DeviceName}: layout {(m.Bool("ok") ? "saved" : "not saved")}: {m.Str("message")}");
                break;
            case "readings.watch": SetWatch(s, m); break;
            case "tuning.request":
                BeamNg?.Send(JsonExt.Msg("tuning.request"));
                break;
            case "tuning.apply": ForwardTuning(s, m); break;
            // pong, and anything newer than us, is ignored (forward compatibility).
        }
    }

    void ProfileSync(PhoneSession s, JsonElement m)
    {
        var warnings = new List<string>();
        var bindings = new List<Binding>();
        if (m.TryGetProperty("bindings", out var arr) && arr.ValueKind == JsonValueKind.Array)
            foreach (var b in arr.EnumerateArray())
                if (BindingParser.ParseBinding(b, warnings) is { } binding) bindings.Add(binding);

        var profileId = m.Str("profileId") ?? "";
        s.Engine.SetBindings(profileId, bindings);
        s.ProfileId = profileId;
        s.Game = m.Str("game");

        var ack = JsonExt.Msg("profile.ack");
        ack["profileId"] = profileId;
        ack["count"] = bindings.Count;
        s.Send(ack);

        foreach (var problem in CheckVJoy(bindings))
        {
            Log("warn", problem);
            var n = JsonExt.Msg("notice");
            n["level"] = "warn";
            n["code"] = "vjoy_config";
            n["message"] = problem;
            s.Send(n);
        }

        int hid = bindings.Count(b => b.RoutedOverHid);
        Log("info", $"{s.DeviceName} loaded profile \"{profileId}\" ({bindings.Count} controls{(hid > 0 ? $", {hid} over Bluetooth HID" : "")})");
        foreach (var w in warnings.Take(10)) Log("warn", $"Profile {profileId}: {w}");
        if (warnings.Count > 10) Log("warn", $"Profile {profileId}: {warnings.Count - 10} more warnings");

        SendSnapshot(s);
        if (BeamNg?.Connected == true) BeamNg.RequestState();
        SessionsChanged?.Invoke();
    }

    void SendSnapshot(PhoneSession s)
    {
        if (BeamNg?.Connected == true)
        {
            s.Send(StateMessage("beamng", Snapshot(_beamngState), full: true));
            if (_beamngCommands.Count > 0) s.Send(CommandsMessage());
            if (_beamngFeatures is { } bf) s.Send((JsonObject)bf.DeepClone());
            if (_beamngReadings is { } br) s.Send((JsonObject)br.DeepClone());
            if (_beamngTuning is { } bt) s.Send((JsonObject)bt.DeepClone());
        }
        else if (OutGaugeActive)
            s.Send(StateMessage("outgauge", Snapshot(_outgaugeState), full: true));
    }

    JsonObject Snapshot(Dictionary<string, JsonNode?> state)
    {
        lock (_stateGate)
        {
            var o = new JsonObject();
            foreach (var (k, v) in state) o[k] = v?.DeepClone();
            return o;
        }
    }

    static JsonObject StateMessage(string source, JsonObject values, bool full)
    {
        var m = JsonExt.Msg("state");
        m["source"] = source;
        m["values"] = values;
        if (full) m["full"] = true;
        return m;
    }

    JsonObject CommandsMessage()
    {
        var m = JsonExt.Msg("beamng.commands");
        m["list"] = new JsonArray(_beamngCommands.Select(c => (JsonNode?)JsonValue.Create(c)).ToArray());
        return m;
    }

    void ForwardBindings(PhoneSession s, JsonElement m)
    {
        if (BeamNg?.Connected != true)
        {
            s.Send(JsonExt.Error("no_beamng", "BeamNG is not running with the SimDeck for BeamNG mod"));
            return;
        }
        if (JsonNode.Parse(m.GetRawText()) is not JsonObject fwd) return;
        fwd["type"] = "bindings.apply";
        var device = m.Str("device") ?? "vjoy1";
        string? vidpid = null;
        try { vidpid = _options.Identity.VidPid(device); } catch (Exception e) { Log("warn", $"Could not read {device} VID/PID: {e.Message}"); }
        if (vidpid != null) fwd["vidpid"] = vidpid;
        else Log("warn", $"No Windows device found for \"{device}\"; the mod will use its default");
        BeamNg.Send(fwd);
        Log("info", $"{s.DeviceName} asked BeamNG to bind {(m.TryGetProperty("bindings", out var b) && b.ValueKind == JsonValueKind.Array ? b.GetArrayLength() : 0)} actions on {device}{(vidpid != null ? $" ({vidpid})" : "")}");
    }

    // ---------------------------------------------------------------- control events

    void OnUdpInput(byte[] data, IPEndPoint from)
    {
        if (data.Length > 1024) return;
        using var doc = JsonDocument.Parse(data);
        var m = doc.RootElement;
        if (m.ValueKind != JsonValueKind.Object || m.Int("v", 1) > ProtocolInfo.Version) return;
        var key = m.Str("k");
        if (key == null || !_sessions.TryGetValue(key, out var s)) return;
        var addr = from.Address.IsIPv4MappedToIPv6 ? from.Address.MapToIPv4() : from.Address;
        if (!addr.Equals(s.RemoteAddress)) return;
        s.Touch();
        var id = m.Str("id");
        var seq = m.Long("s");
        var val = m.Num("val");
        if (id == null || seq == null || val == null) return;
        ApplyInput(s, id, seq.Value, val.Value, viaUdp: true);
    }

    void ApplyInput(PhoneSession s, string id, long seq, double val, bool viaUdp)
    {
        Interlocked.Increment(ref s.EventCount);
        var r = s.Engine.Apply(id, seq, val, Environment.TickCount64);
        if (r == ApplyResult.UnknownControl && !viaUdp)
            s.Send(JsonExt.Error("unknown_control", $"No binding for control \"{id}\". Send profile.sync first."));
    }

    // ---------------------------------------------------------------- game state

    void OnBeamNgState(JsonObject values, bool full)
    {
        lock (_stateGate)
        {
            if (full) _beamngState.Clear();
            foreach (var (k, v) in values) _beamngState[k] = v?.DeepClone();
        }
        Broadcast(StateMessage("beamng", values, full));
        GameState?.Invoke(SnapshotDict(_beamngState), "beamng");
    }

    void OnBeamNgCommands(IReadOnlyList<string> list)
    {
        _beamngCommands = list;
        Broadcast(CommandsMessage());
    }

    void ClearBeamNgState()
    {
        lock (_stateGate) _beamngState.Clear();
        _beamngCommands = Array.Empty<string>();
        if (_beamngFeatures != null)
        {
            _beamngFeatures = null;
            var empty = JsonExt.Msg("beamng.features");
            empty["vehicle"] = "";
            empty["list"] = new JsonArray();
            Broadcast(empty);
        }
        _beamngActions = null;
        _beamngReadings = null;
        if (_beamngTuning != null)
        {
            _beamngTuning = null;
            var empty = JsonExt.Msg("beamng.tuning");
            empty["vehicle"] = "";
            empty["list"] = new JsonArray();
            Broadcast(empty);
        }
        _lastWatch = null;
    }

    /// <summary>The mod's vehicle feature list, relayed as "beamng.features" (PROTOCOL.md, Vehicle features).</summary>
    void OnBeamNgFeatures(JsonObject m)
    {
        var relay = JsonExt.Msg("beamng.features");
        relay["vehicle"] = m["vehicle"]?.DeepClone();
        relay["list"] = m["list"] is JsonArray l ? l.DeepClone() : new JsonArray();
        if (m["diag"] is JsonObject d) relay["diag"] = d.DeepClone();
        // The vehicle's category (car, truck, machine, offroad, race...) picks the phone's theme (THEMES.md).
        if (m["category"] is JsonNode cat) relay["category"] = cat.DeepClone();
        _beamngFeatures = relay;
        Broadcast(relay);
        int count = (relay["list"] as JsonArray)?.Count ?? 0;
        var diag = m["diag"]?.ToJsonString() ?? "";
        Log("info", $"BeamNG vehicle {m["vehicle"]?.ToString() ?? "?"} ({m["category"]?.ToString() ?? "no category"}): {count} vehicle features {diag}");
    }

    void OnBeamNgActions(JsonObject m)
    {
        var relay = JsonExt.Msg("beamng.actions");
        relay["vehicle"] = m["vehicle"]?.DeepClone();
        relay["list"] = m["list"] is JsonArray l ? l.DeepClone() : new JsonArray();
        _beamngActions = relay;
        Broadcast(relay);
    }

    // ---------------------------------------------------------------- readings and tuning (SimDeck 1.4)

    /// <summary>The mod's readings catalogue, tuning list and tuning results, relayed with a "beamng." prefix.</summary>
    void OnBeamNgModMessage(JsonObject m)
    {
        var type = m["type"]?.GetValue<string>() ?? "";
        var relay = (JsonObject)m.DeepClone();
        relay["v"] = ProtocolInfo.Version;
        relay["type"] = "beamng." + type;
        switch (type)
        {
            case "readings":
                if (relay["list"] is not JsonArray) relay["list"] = new JsonArray();
                _beamngReadings = relay;
                break;
            case "tuning":
                if (relay["list"] is not JsonArray) relay["list"] = new JsonArray();
                _beamngTuning = relay;
                Log("info", $"BeamNG tuning for {m["vehicle"]}: {((JsonArray)relay["list"]!).Count} settings");
                break;
            case "tuning.result":
                bool ok = m["ok"] is JsonValue v && v.TryGetValue<bool>(out var b) && b;
                Log(ok ? "info" : "warn", $"BeamNG tuning: {m["message"]}");
                break;
        }
        Broadcast(relay);
    }

    const int MaxWatchKeys = 512;

    void SetWatch(PhoneSession s, JsonElement m)
    {
        var keys = new List<string>();
        if (m.TryGetProperty("keys", out var arr) && arr.ValueKind == JsonValueKind.Array)
            foreach (var k in arr.EnumerateArray())
                if (k.ValueKind == JsonValueKind.String && k.GetString() is { Length: > 0 and <= 128 } key && keys.Count < MaxWatchKeys) keys.Add(key);
        s.WatchKeys = keys;
        s.WatchAll = m.Bool("all");
        PushWatch();
    }

    /// <summary>Sends the mod the union of what every phone watches, when it changed.</summary>
    void PushWatch()
    {
        if (BeamNg?.Connected != true) return;
        var sessions = Sessions;
        var keys = sessions.SelectMany(x => x.WatchKeys).Distinct(StringComparer.Ordinal).OrderBy(x => x, StringComparer.Ordinal).Take(MaxWatchKeys).ToList();
        var text = WatchText(keys, sessions.Any(x => x.WatchAll));
        if (text == _lastWatch) return; // a fresh mod watches nothing, so an empty watch is not sent on connect
        _lastWatch = text;
        BeamNg.Send(JsonNode.Parse(text)!.AsObject());
    }

    static string WatchText(IReadOnlyList<string> keys, bool all)
    {
        var msg = JsonExt.Msg("readings.watch");
        msg["keys"] = new JsonArray(keys.Select(k => (JsonNode)JsonValue.Create(k)!).ToArray());
        msg["all"] = all;
        return msg.ToJsonString();
    }

    /// <summary>Tuning values from a phone: names and numbers only, then on to the mod, which clamps them.</summary>
    void ForwardTuning(PhoneSession s, JsonElement m)
    {
        if (!m.TryGetProperty("vars", out var vars) || vars.ValueKind != JsonValueKind.Object) { s.Send(JsonExt.Error("bad_message", "tuning.apply needs vars")); return; }
        var clean = new JsonObject();
        foreach (var p in vars.EnumerateObject())
            if (p.Value.ValueKind == JsonValueKind.Number && p.Name.Length is > 0 and <= 128 && clean.Count < 500 && double.IsFinite(p.Value.GetDouble()))
                clean[p.Name] = p.Value.GetDouble();
        if (BeamNg?.Connected != true)
        {
            var r = JsonExt.Msg("beamng.tuning.result");
            r["ok"] = false;
            r["message"] = "BeamNG is not connected";
            s.Send(r);
            return;
        }
        var msg = JsonExt.Msg("tuning.apply");
        msg["vars"] = clean;
        BeamNg.Send(msg);
        Log("info", $"{s.DeviceName}: tuning {clean.ToJsonString()}");
    }

    static JsonObject ForegroundMessage(string exe)
    {
        var m = JsonExt.Msg("foreground");
        m["exe"] = exe;
        return m;
    }

    void PollForeground()
    {
        if (_options.ForegroundApp is not { } probe) return;
        string? exe = null;
        try { exe = probe(); } catch { }
        if (string.IsNullOrEmpty(exe) || exe == _foreground) return;
        _foreground = exe;
        Broadcast(ForegroundMessage(exe));
    }

    /// <summary>
    /// The mod went away: phones drop every BeamNG value (a full, empty state), and if OutGauge is
    /// still arriving they get its whole picture, since its changes were held back while the mod was live.
    /// </summary>
    void OnBeamNgDisconnected()
    {
        ClearBeamNgState();
        Broadcast(StateMessage("beamng", new JsonObject(), full: true));
        GameState?.Invoke(new Dictionary<string, JsonNode?>(), "beamng");
        if (!OutGaugeActive) return;
        JsonObject values = new();
        lock (_stateGate)
        {
            foreach (var (k, v) in _outgaugeState) values[k] = v?.DeepClone();
            _outgaugePending.Clear();
        }
        Broadcast(StateMessage("outgauge", values, full: true));
        GameState?.Invoke(SnapshotDict(_outgaugeState), "outgauge");
    }

    IReadOnlyDictionary<string, JsonNode?> SnapshotDict(Dictionary<string, JsonNode?> d)
    {
        lock (_stateGate) return d.ToDictionary(kv => kv.Key, kv => kv.Value?.DeepClone());
    }

    internal void OnOutGauge(byte[] data, IPEndPoint from)
    {
        Interlocked.Exchange(ref _lastOutGaugeAnyMs, Environment.TickCount64);
        Volatile.Write(ref _lastOutGaugeFrom, from.Address);
        // Telemetry is local; ignore anything that is not from this PC. Each reason is logged once per run.
        if (!IPAddress.IsLoopback(from.Address) && !LocalAddresses().Contains(from.Address))
        {
            if (Interlocked.Exchange(ref _loggedForeignOutGauge, 1) == 0)
                Log("warn", $"OutGauge packet from {from.Address} ignored (not this PC)");
            return;
        }
        var values = OutGauge.Parse(data);
        if (values == null)
        {
            if (Interlocked.Exchange(ref _loggedShortOutGauge, 1) == 0)
                Log("warn", $"OutGauge packet too short ({data.Length} bytes)");
            return;
        }
        Interlocked.Exchange(ref _lastOutGaugeMs, Environment.TickCount64);
        lock (_stateGate)
        {
            foreach (var (k, v) in values)
            {
                var node = JsonValue.Create(v) as JsonNode;
                if (_outgaugeState.TryGetValue(k, out var old) && JsonNode.DeepEquals(old, node)) continue;
                _outgaugeState[k] = node;
                _outgaugePending[k] = node?.DeepClone();
            }
        }
    }

    /// <summary>This PC's addresses, refreshed every 10 s rather than per packet (OutGauge runs at up to 60 Hz).</summary>
    IReadOnlySet<IPAddress> LocalAddresses()
    {
        long now = Environment.TickCount64;
        if (now - Interlocked.Read(ref _localAddressesAtMs) > 10_000)
        {
            _localAddresses = MdnsAdvertiser.LocalAddresses().ToHashSet();
            Interlocked.Exchange(ref _localAddressesAtMs, now);
        }
        return _localAddresses;
    }

    /// <summary>Sends OutGauge changes at most 10 times a second, and not while the richer mod feed is live.</summary>
    void FlushOutGauge(long now)
    {
        if (now - _lastOutGaugeFlushMs < 100) return;
        _lastOutGaugeFlushMs = now;
        JsonObject values;
        lock (_stateGate)
        {
            if (_outgaugePending.Count == 0) return;
            values = new JsonObject();
            foreach (var (k, v) in _outgaugePending) values[k] = v;
            _outgaugePending.Clear();
        }
        if (BeamNg?.Connected == true) return;
        Broadcast(StateMessage("outgauge", values, full: false));
        GameState?.Invoke(SnapshotDict(_outgaugeState), "outgauge");
    }

    // ---------------------------------------------------------------- UI actions

    /// <summary>
    /// Disconnects and releases the phone first, then removes its pairing. If saving fails the phone is
    /// still stopped (it can reconnect with its token) and the exception tells the caller to report it.
    /// </summary>
    public void ForgetDevice(string deviceId)
    {
        foreach (var s in _sessions.Values.Where(x => x.DeviceId == deviceId).ToList())
        {
            if (!_sessions.TryRemove(s.SessionKey, out PhoneSession? _)) continue;
            s.Engine.ReleaseAll();
            _ = s.CloseAsync("forgotten on the PC");
        }
        SessionsChanged?.Invoke();
        Pairing.Forget(deviceId);
    }

    public void ReleaseEverything()
    {
        foreach (var s in _sessions.Values) s.Engine.ReleaseAll();
        Router.ReleaseAll();
        Log("info", "Released every held key and button");
    }
}
