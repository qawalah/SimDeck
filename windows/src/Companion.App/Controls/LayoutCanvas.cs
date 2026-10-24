using System.Globalization;
using System.Text.Json.Nodes;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using SimDeck.Companion.Layout;

namespace SimDeck.Companion.App.Controls;

/// <summary>
/// One page of a phone profile, drawn at the phone's grid in its equipment theme's colours, for the PC layout editor.
/// Click to select (Ctrl+click adds to the selection), drag to move, drag the corner tab to resize. Moves and resizes
/// snap to whole cells and are reported through events; the view model applies them to the <see cref="LayoutDoc"/>.
/// </summary>
public sealed class LayoutCanvas : Control
{
    public static readonly StyledProperty<LayoutDoc?> DocProperty = AvaloniaProperty.Register<LayoutCanvas, LayoutDoc?>(nameof(Doc));
    public static readonly StyledProperty<int> PageIndexProperty = AvaloniaProperty.Register<LayoutCanvas, int>(nameof(PageIndex));
    public static readonly StyledProperty<int> VersionProperty = AvaloniaProperty.Register<LayoutCanvas, int>(nameof(Version));
    public static readonly StyledProperty<IReadOnlyList<string>> SelectedProperty =
        AvaloniaProperty.Register<LayoutCanvas, IReadOnlyList<string>>(nameof(Selected), Array.Empty<string>());

    static LayoutCanvas()
    {
        AffectsRender<LayoutCanvas>(DocProperty, PageIndexProperty, VersionProperty, SelectedProperty);
        FocusableProperty.OverrideDefaultValue<LayoutCanvas>(true);
    }

    public LayoutDoc? Doc { get => GetValue(DocProperty); set => SetValue(DocProperty, value); }
    public int PageIndex { get => GetValue(PageIndexProperty); set => SetValue(PageIndexProperty, value); }
    /// <summary>Bumped by the view model after every edit, so the canvas redraws.</summary>
    public int Version { get => GetValue(VersionProperty); set => SetValue(VersionProperty, value); }
    public IReadOnlyList<string> Selected { get => GetValue(SelectedProperty); set => SetValue(SelectedProperty, value); }

    public event Action<IReadOnlyList<string>>? SelectionRequested;
    public event Action<string, int, int>? MoveRequested;
    public event Action<string, int, int>? ResizeRequested;

    const double Gap = 8;
    static readonly Typeface Face = new(new FontFamily("avares://SimDeckCompanion/Assets/Fonts#Barlow Condensed SemiBold"));
    static readonly Typeface FaceBold = new(new FontFamily("avares://SimDeckCompanion/Assets/Fonts#Barlow Condensed ExtraBold"));

    // drag state
    string? _dragId;
    bool _resizing;
    Point _dragStart;
    Vector _dragDelta;

    (double cw, double ch) Cell()
    {
        if (Doc is not { } d) return (1, 1);
        int cols = d.Columns(PageIndex), rows = d.Rows(PageIndex);
        return ((Bounds.Width - Gap * (cols - 1)) / cols, (Bounds.Height - Gap * (rows - 1)) / rows);
    }

    Rect CellRect(int x, int y, int w, int h)
    {
        var (cw, ch) = Cell();
        return new Rect(x * (cw + Gap), y * (ch + Gap), cw * w + Gap * (w - 1), ch * h + Gap * (h - 1));
    }

    /// <summary>A landscape phone screen: as wide as there is room for, a little under half as tall.</summary>
    protected override Size MeasureOverride(Size availableSize)
    {
        double w = double.IsInfinity(availableSize.Width) ? 760 : availableSize.Width;
        double h = w * 0.48;
        if (!double.IsInfinity(availableSize.Height)) h = Math.Min(h, availableSize.Height);
        return new Size(w, Math.Max(220, h));
    }

    static int I(JsonObject c, string k, int f) => LayoutDoc.Num(c, k, f);

    static Color C(string hex) => Color.Parse(hex);

    public override void Render(DrawingContext ctx)
    {
        var bounds = new Rect(Bounds.Size);
        if (Doc is not { } d || d.PageCount == 0)
        {
            ctx.FillRectangle(new SolidColorBrush(C("#0A0D14")), bounds);
            return;
        }
        int page = Math.Clamp(PageIndex, 0, d.PageCount - 1);
        var t = LayoutCatalog.Theme(d.PageTheme(page));
        ctx.FillRectangle(new SolidColorBrush(C(t.Environment)), bounds);
        int cols = d.Columns(page), rows = d.Rows(page);
        var controls = d.Controls(page);

        // Empty cells.
        var dash = new Pen(new SolidColorBrush(C(t.PanelLine), 0.35), 1, new DashStyle(new double[] { 4, 4 }, 0));
        for (int x = 0; x < cols; x++)
            for (int y = 0; y < rows; y++)
                if (!controls.Any(c => x >= I(c, "x", 0) && x < I(c, "x", 0) + I(c, "w", 1) && y >= I(c, "y", 0) && y < I(c, "y", 0) + I(c, "h", 1)))
                    ctx.DrawRectangle(null, dash, CellRect(x, y, 1, 1).Deflate(2), 4, 4);

        // Panel groups behind the controls.
        if (d.PageAt(page)["panels"] is JsonArray panels)
            foreach (var g in panels.OfType<JsonObject>())
            {
                var r = CellRect(I(g, "x", 0), I(g, "y", 0), Math.Max(1, I(g, "w", 1)), Math.Max(1, I(g, "h", 1))).Inflate(Gap * 0.45);
                ctx.DrawRectangle(new SolidColorBrush(C(t.Panel), 0.8), new Pen(new SolidColorBrush(C(t.PanelLine)), 1), r, 4, 4);
                var title = (string?)g["title"];
                if (!string.IsNullOrWhiteSpace(title))
                {
                    var ft = Text(title.ToUpperInvariant(), 10, C(t.Legend), bold: true);
                    var tr = new Rect(r.X + 10, r.Y - ft.Height / 2, ft.Width + 8, ft.Height);
                    ctx.FillRectangle(new SolidColorBrush(C(t.Environment)), tr);
                    ctx.DrawText(ft, new Point(tr.X + 4, tr.Y));
                }
            }

        if (d.IsAuto(page))
        {
            var ft = Text((string?)d.PageAt(page)["auto"] == "beamng.tuning"
                ? "Tuning page: on the phone it shows the tuning settings of the vehicle you drive."
                : "Vehicle page: fills itself on the phone with the functions of the vehicle you drive.", 15, C(t.LegendMuted));
            ctx.DrawText(ft, new Point((Bounds.Width - ft.Width) / 2, (Bounds.Height - ft.Height) / 2));
            return;
        }

        foreach (var c in controls)
        {
            var id = (string?)c["id"] ?? "";
            int x = I(c, "x", 0), y = I(c, "y", 0), w = I(c, "w", 1), h = I(c, "h", 1);
            var r = CellRect(x, y, w, h);
            if (id == _dragId)
            {
                if (_resizing) r = new Rect(r.X, r.Y, Math.Max(20, r.Width + _dragDelta.X), Math.Max(20, r.Height + _dragDelta.Y));
                else r = r.Translate(_dragDelta);
            }
            DrawControl(ctx, c, r, t, Selected.Contains(id));
        }
    }

    static FormattedText Text(string s, double size, Color color, bool bold = false, double maxWidth = 0)
    {
        if (maxWidth > 0)
        {
            // Never break inside a word: shrink until the longest word fits, like the phone's legends.
            var longest = s.Split(' ', StringSplitOptions.RemoveEmptyEntries).DefaultIfEmpty("").MaxBy(w => w.Length)!;
            while (size > 8 && new FormattedText(longest, CultureInfo.InvariantCulture, FlowDirection.LeftToRight, bold ? FaceBold : Face, size, Brushes.White).Width > maxWidth)
                size -= 0.5;
        }
        var ft = new FormattedText(s, CultureInfo.InvariantCulture, FlowDirection.LeftToRight, bold ? FaceBold : Face, size, new SolidColorBrush(color));
        if (maxWidth > 0) { ft.MaxTextWidth = maxWidth; ft.MaxLineCount = 2; ft.Trimming = TextTrimming.CharacterEllipsis; ft.TextAlignment = TextAlignment.Center; }
        return ft;
    }

    void DrawControl(DrawingContext ctx, JsonObject c, Rect r, ThemeTokens t, bool selected)
    {
        var type = (string?)c["type"] ?? "momentary";
        var skin = LayoutCatalog.ResolveSkin(type, (string?)c["skin"], t.Id, (string?)c["range"],
            c["springReturn"] is JsonValue sv && sv.TryGetValue<bool>(out var spring) && spring, (string?)c["label"], (string?)c["icon"]);
        var accent = C(t.Lamps.TryGetValue((string?)c["accent"] ?? "neutral", out var a) ? a : t.Lamps["neutral"]);
        var edge = new Pen(new SolidColorBrush(C(t.CapEdge)), 1);
        var capBrush = new LinearGradientBrush
        {
            StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
            GradientStops = { new GradientStop(C(t.CapHigh), 0), new GradientStop(C(t.Cap), 1) },
        };
        var body = r.Deflate(3);
        var label = ((string?)c["label"] ?? "").ToUpperInvariant();
        double minSide = Math.Min(body.Width, body.Height);

        switch (type)
        {
            case "slider":
            {
                bool vertical = !(c["vertical"] is JsonValue vv && vv.TryGetValue<bool>(out var vert) && !vert);
                ctx.DrawRectangle(new SolidColorBrush(C(t.Panel)), edge, body, t.Corner, t.Corner);
                var slot = vertical ? new Rect(body.Center.X - 4, body.Y + 22, 8, body.Height - 34) : new Rect(body.X + 12, body.Center.Y - 4, body.Width - 24, 8);
                ctx.DrawRectangle(Brushes.Black, null, slot, 4, 4);
                var handle = vertical ? new Rect(body.X + 8, slot.Bottom - 16, body.Width - 16, 14) : new Rect(slot.X, body.Center.Y - 12, 14, 24);
                if (skin is "hydraulic-lever" or "quadrant-knob")
                {
                    var hc = vertical ? new Point(body.Center.X, slot.Center.Y) : new Point(slot.Center.X, body.Center.Y);
                    ctx.DrawEllipse(capBrush, new Pen(new SolidColorBrush(accent), 2), hc, 11, 11);
                }
                else ctx.DrawRectangle(capBrush, new Pen(new SolidColorBrush(accent), 2), handle, 3, 3);
                if (c["stations"] is JsonArray st)
                    foreach (var s in st.OfType<JsonObject>())
                    {
                        double at = s["at"] is JsonValue av && av.TryGetValue<double>(out var dv) ? dv : 0;
                        var y = slot.Bottom - at * slot.Height;
                        if (vertical) ctx.DrawLine(new Pen(new SolidColorBrush(C(t.Legend)), 1.5), new Point(slot.X - 6, y), new Point(slot.Right + 6, y));
                    }
                ctx.DrawText(Text(label, 12, C(t.LegendMuted), maxWidth: body.Width - 4), new Point(body.X + 2, body.Y + 3));
                break;
            }
            case "joystick":
            {
                ctx.DrawRectangle(new SolidColorBrush(C(t.Panel)), edge, body, t.Corner, t.Corner);
                var cc = new Point(body.Center.X, body.Center.Y + 8);
                double rr = Math.Min(body.Width, body.Height - 24) / 2 - 6;
                if (skin == "pilot-grip") ctx.DrawRectangle(Brushes.Black, new Pen(new SolidColorBrush(C(t.PanelLine)), 1.5), new Rect(cc.X - rr, cc.Y - rr, rr * 2, rr * 2), rr * 0.25, rr * 0.25);
                else ctx.DrawEllipse(Brushes.Black, new Pen(new SolidColorBrush(C(t.PanelLine)), 1.5), cc, rr, rr);
                ctx.DrawLine(new Pen(new SolidColorBrush(C(t.PanelLine)), 1), new Point(cc.X - rr * 0.7, cc.Y), new Point(cc.X + rr * 0.7, cc.Y));
                ctx.DrawLine(new Pen(new SolidColorBrush(C(t.PanelLine)), 1), new Point(cc.X, cc.Y - rr * 0.7), new Point(cc.X, cc.Y + rr * 0.7));
                ctx.DrawEllipse(capBrush, new Pen(new SolidColorBrush(accent), 2), cc, rr * 0.28, rr * 0.28);
                ctx.DrawText(Text(label, 12, C(t.Legend), maxWidth: body.Width - 8), new Point(body.X + 4, body.Y + 3));
                if (c["ends"] is JsonArray ends && ends.Count >= 4)
                {
                    var ec = C(t.LegendMuted);
                    void E(int i, Point p) { var s = (string?)ends[i]; if (!string.IsNullOrEmpty(s)) { var ft = Text(s, 10, ec); ctx.DrawText(ft, new Point(p.X - ft.Width / 2, p.Y - ft.Height / 2)); } }
                    E(0, new Point(cc.X - rr * 0.65, cc.Y + 10)); E(1, new Point(cc.X + rr * 0.65, cc.Y + 10));
                    E(2, new Point(cc.X, cc.Y + rr * 0.8)); E(3, new Point(cc.X, cc.Y - rr * 0.85));
                }
                break;
            }
            case "gauge":
            {
                if (LayoutCatalog.IsRound(skin))
                {
                    ctx.DrawEllipse(Brushes.Black, new Pen(new SolidColorBrush(C("#6B6F75")), 3), body.Center, minSide / 2 - 2, minSide / 2 - 2);
                    var ft = Text(label, 11, C("#BFC3C8"), maxWidth: minSide * 0.8);
                    ctx.DrawText(ft, new Point(body.Center.X - ft.Width / 2, body.Center.Y - minSide * 0.3));
                    ctx.DrawLine(new Pen(new SolidColorBrush(C("#FF7A1A")), 2.5), body.Center, new Point(body.Center.X + minSide * 0.25, body.Center.Y - minSide * 0.18));
                }
                else
                {
                    ctx.DrawRectangle(new SolidColorBrush(skin is "segment" or "shift-lights" ? Colors.Black : C(t.Panel)), edge, body, t.Corner, t.Corner);
                    var lt = Text(label, 11, C(t.LegendMuted), maxWidth: body.Width - 4);
                    ctx.DrawText(lt, new Point(body.X + 2, body.Y + 3));
                    var big = Text(skin == "shift-lights" ? "● ● ● ● ●" : "88", Math.Min(28, body.Height * 0.45), accent, bold: true);
                    ctx.DrawText(big, new Point(body.Center.X - big.Width / 2, body.Center.Y - big.Height / 2 + 6));
                }
                break;
            }
            case "indicator":
            {
                ctx.DrawRectangle(new SolidColorBrush(C(t.Panel)), edge, body, t.Corner, t.Corner);
                ctx.DrawEllipse(new SolidColorBrush(accent, 0.85), null, new Point(body.Center.X, body.Y + body.Height * 0.4), minSide * 0.17, minSide * 0.17);
                var ft = Text(label, 11, C(t.LegendMuted), maxWidth: body.Width - 4);
                ctx.DrawText(ft, new Point(body.X + 2, body.Bottom - ft.Height - 3));
                break;
            }
            default:
            {
                if (skin == "gear-lever")
                {
                    ctx.DrawRectangle(new SolidColorBrush(C(t.Panel)), edge, body, t.Corner, t.Corner);
                    var slot = new Rect(body.Center.X - 4, body.Y + 36, 8, Math.Max(20, body.Height - 54));
                    ctx.DrawRectangle(Brushes.Black, null, slot, 4, 4);
                    ctx.DrawEllipse(new SolidColorBrush(C("#EDEDED")), new Pen(new SolidColorBrush(C("#9A9A9A")), 1), new Point(slot.Center.X, slot.Y + 12), 13, 13);
                    var ft = Text(label, 11, C(t.Legend), maxWidth: body.Width);
                    ctx.DrawText(ft, new Point(body.X, body.Y + 3));
                    if (c["legends"] is JsonArray lg && lg.Count >= 2)
                    {
                        var up = Text((string?)lg[0] ?? "", 10, C(t.LegendMuted)); var dn = Text((string?)lg[1] ?? "", 10, C(t.LegendMuted));
                        ctx.DrawText(up, new Point(body.Center.X - up.Width / 2, slot.Y - up.Height - 1));
                        ctx.DrawText(dn, new Point(body.Center.X - dn.Width / 2, slot.Bottom + 2));
                    }
                }
                else if (LayoutCatalog.IsRound(skin))
                {
                    double rad = Math.Min(minSide, body.Height - 18) / 2 - 2;
                    var cc = new Point(body.Center.X, body.Y + rad + 4);
                    ctx.DrawEllipse(new SolidColorBrush(C("#8E9399")), null, cc, rad, rad);
                    ctx.DrawEllipse(Brushes.Black, null, cc, rad * 0.8, rad * 0.8);
                    ctx.DrawEllipse(null, new Pen(new SolidColorBrush(accent), Math.Max(2, rad * 0.09)), cc, rad * 0.72, rad * 0.72);
                    ctx.DrawEllipse(capBrush, null, cc, rad * 0.58, rad * 0.58);
                    var ft = Text(label, 11, C(t.Legend), maxWidth: body.Width);
                    ctx.DrawText(ft, new Point(body.X, Math.Min(body.Bottom - ft.Height, cc.Y + rad + 2)));
                }
                else
                {
                    ctx.DrawRectangle(capBrush, edge, body, t.Corner, t.Corner);
                    if (type == "rocker" || skin.StartsWith("rocker"))
                    {
                        // Chevrons at the two ends: the rocker's two directions.
                        var mark = new SolidColorBrush(C(t.LegendMuted), 0.8);
                        bool tall = body.Height >= body.Width;
                        double tri = 5;
                        Geometry Tri(Point p, double dx, double dy) => new PolylineGeometry(new[]
                            { new Point(p.X + dx * tri, p.Y + dy * tri), new Point(p.X - dy * tri - dx * tri * 0.2, p.Y - dx * tri - dy * tri * 0.2), new Point(p.X + dy * tri - dx * tri * 0.2, p.Y + dx * tri - dy * tri * 0.2) }, true);
                        if (tall) { ctx.DrawGeometry(mark, null, Tri(new Point(body.Center.X, body.Y + 10), 0, -1)); ctx.DrawGeometry(mark, null, Tri(new Point(body.Center.X, body.Bottom - 14), 0, 1)); }
                        else { ctx.DrawGeometry(mark, null, Tri(new Point(body.X + 10, body.Center.Y), -1, 0)); ctx.DrawGeometry(mark, null, Tri(new Point(body.Right - 10, body.Center.Y), 1, 0)); }
                    }
                    if (skin is "lit-push" or "lit-push-split" or "rubber-key" or "rocker-lamp" or "lit-row" or "annunciator-row")
                        ctx.DrawRectangle(new SolidColorBrush(accent, 0.8), null, new Rect(body.Right - body.Width * 0.35, body.Y + 6, body.Width * 0.25, 5), 2, 2);
                    // Accent bar: the colour this control lights in.
                    ctx.DrawRectangle(new SolidColorBrush(accent), null, new Rect(body.X + 6, body.Bottom - 6, body.Width * 0.3, 3), 1.5, 1.5);
                    var ft = Text(label, Math.Clamp(minSide / 5, 11, 15), C(t.Legend), maxWidth: body.Width - 8);
                    ctx.DrawText(ft, new Point(body.X + 4, body.Center.Y - ft.Height / 2));
                }
                break;
            }
        }
        // Style name, small, so the PC shows what the phone will draw.
        var sk = Text(skin, 9, C(t.LegendMuted));
        if (body.Width > 70 && body.Height > 44 && type is not ("joystick" or "slider")) ctx.DrawText(sk, new Point(body.Right - sk.Width - 4, body.Bottom - sk.Height - 2));
        if (selected)
        {
            var sel = new SolidColorBrush(C("#FF2E7E"));
            ctx.DrawRectangle(null, new Pen(sel, 2.5), r.Inflate(1), 5, 5);
            ctx.FillRectangle(sel, new Rect(r.Right - 12, r.Bottom - 12, 12, 12));
        }
    }

    // ---------------------------------------------------------------- input

    JsonObject? HitTest(Point p, out bool corner)
    {
        corner = false;
        if (Doc is not { } d) return null;
        foreach (var c in d.Controls(PageIndex).Reverse())
        {
            var r = CellRect(I(c, "x", 0), I(c, "y", 0), I(c, "w", 1), I(c, "h", 1));
            if (!r.Contains(p)) continue;
            corner = p.X > r.Right - 18 && p.Y > r.Bottom - 18;
            return c;
        }
        return null;
    }

    protected override void OnPointerPressed(PointerPressedEventArgs e)
    {
        base.OnPointerPressed(e);
        Focus();
        var p = e.GetPosition(this);
        var hit = HitTest(p, out var corner);
        if (hit == null) { SelectionRequested?.Invoke(Array.Empty<string>()); return; }
        var id = (string?)hit["id"] ?? "";
        if (e.KeyModifiers.HasFlag(KeyModifiers.Control))
        {
            var list = Selected.Contains(id) ? Selected.Where(s => s != id).ToList() : Selected.Append(id).ToList();
            SelectionRequested?.Invoke(list);
            return;
        }
        if (!Selected.Contains(id)) SelectionRequested?.Invoke(new[] { id });
        _dragId = id;
        _resizing = corner && Selected.Contains(id);
        _dragStart = p;
        _dragDelta = default;
        e.Pointer.Capture(this);
    }

    protected override void OnPointerMoved(PointerEventArgs e)
    {
        base.OnPointerMoved(e);
        var p = e.GetPosition(this);
        if (_dragId == null)
        {
            var hit = HitTest(p, out var corner);
            Cursor = hit != null && corner && Selected.Contains((string?)hit["id"] ?? "") ? new Cursor(StandardCursorType.BottomRightCorner)
                : hit != null ? new Cursor(StandardCursorType.SizeAll) : Cursor.Default;
            return;
        }
        _dragDelta = p - _dragStart;
        InvalidateVisual();
    }

    protected override void OnPointerReleased(PointerReleasedEventArgs e)
    {
        base.OnPointerReleased(e);
        if (_dragId is not { } id || Doc is not { } d) return;
        e.Pointer.Capture(null);
        var (cw, ch) = Cell();
        int dx = (int)Math.Round(_dragDelta.X / (cw + Gap)), dy = (int)Math.Round(_dragDelta.Y / (ch + Gap));
        if (d.Find(PageIndex, id) is { } c && (dx != 0 || dy != 0))
        {
            if (_resizing) ResizeRequested?.Invoke(id, I(c, "w", 1) + dx, I(c, "h", 1) + dy);
            else MoveRequested?.Invoke(id, I(c, "x", 0) + dx, I(c, "y", 0) + dy);
        }
        _dragId = null;
        _dragDelta = default;
        InvalidateVisual();
    }
}
