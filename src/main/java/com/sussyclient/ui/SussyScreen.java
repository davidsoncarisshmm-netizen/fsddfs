package com.sussyclient.ui;

import com.sussyclient.ActivityInspector;
import com.sussyclient.Config;
import com.sussyclient.SussyClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** KR-client style dropdown: header -> modules -> settings. Scroll with the mouse wheel. */
public class SussyScreen extends Screen {
    private static boolean panelOpen = true;
    private static final Set<String> openModules = new HashSet<>();
    private static int scroll = 0;

    private static final int X = 30, Y = 30, W = 170, HEAD = 20, ROW = 17;
    private static final int HEADC = 0xFFB01E1E, ROWC = 0xE0202020, HOVER = 0xE0303030,
            ON = 0xFFB01E1E, TEXT = 0xFFFFFFFF, DIM = 0xFFAAAAAA;

    private final List<Mod> mods = new ArrayList<>();
    private Row dragging = null;

    private static final String[] REGION_NAMES = {"East", "West", "EU West", "EU Central", "Asia", "Oceania"};

    public SussyScreen() {
        super(Component.literal(SussyClient.NAME));
        Config c = Config.I;

        // ---- Sus Chunk Finder ----
        Mod sus = new Mod("Sus Chunk Finder", () -> c.enabled, v -> c.enabled = v);
        sus.rows.add(slider("Sensitivity", 1, 30, 1, () -> c.sensitivity, v -> c.sensitivity = v, ""));
        sus.rows.add(slider("Sim Distance", 2, 32, 1, () -> c.simDistance, v -> c.simDistance = v, " ch"));
        sus.rows.add(slider("Below Y", -60, 64, 1, () -> c.maxY, v -> c.maxY = v, ""));
        sus.rows.add(slider("Scan Speed", 1, 16, 1, () -> c.scanSpeed, v -> c.scanSpeed = v, "/tick"));
        sus.rows.add(slider("Box Height", 1, 384, 1, () -> c.boxHeight, v -> c.boxHeight = v, ""));
        sus.rows.add(slider("Chunk Size", 10, 100, 1, () -> c.chunkSize, v -> c.chunkSize = v, "%"));
        sus.rows.add(new Slider("Thickness", 0.05, 1.0, 0.05, () -> (double) c.outlineThickness, v -> c.outlineThickness = v.floatValue(), ""));
        sus.rows.add(slider("Fill Opacity", 0, 100, 1, () -> c.fillOpacity, v -> c.fillOpacity = v, "%"));
        sus.rows.add(slider("Red", 0, 255, 1, () -> c.red, v -> c.red = v, ""));
        sus.rows.add(slider("Green", 0, 255, 1, () -> c.green, v -> c.green = v, ""));
        sus.rows.add(slider("Blue", 0, 255, 1, () -> c.blue, v -> c.blue = v, ""));
        sus.rows.add(new Toggle("Outline", () -> c.outline, v -> c.outline = v));
        sus.rows.add(new Toggle("Fill", () -> c.fill, v -> c.fill = v));
        sus.rows.add(new Toggle("Through Walls", () -> c.throughWalls, v -> c.throughWalls = v));
        sus.rows.add(new Toggle("Chat Notify", () -> c.chatNotify, v -> c.chatNotify = v));
        mods.add(sus);

        // ---- Activity Inspector ----
        Mod ai = new Mod("Activity Inspector", () -> c.aiEnabled, v -> c.aiEnabled = v);
        ai.rows.add(slider("Sensitivity", 1, 60, 1, () -> c.aiSensitivity, v -> c.aiSensitivity = v, ""));
        ai.rows.add(slider("Scan Radius", 2, 32, 1, () -> c.aiRadius, v -> c.aiRadius = v, " ch"));
        ai.rows.add(slider("Scan Speed", 1, 16, 1, () -> c.aiScanSpeed, v -> c.aiScanSpeed = v, "/tick"));
        ai.rows.add(slider("Spawner Below Y", -60, 320, 1, () -> c.aiMaxY, v -> c.aiMaxY = v, ""));
        ai.rows.add(slider("Slab Y", -64, 320, 1, () -> c.aiSlabY, v -> c.aiSlabY = v, ""));
        ai.rows.add(new Slider("Slab Thickness", 0.1, 8.0, 0.1, () -> (double) c.aiSlabThickness, v -> c.aiSlabThickness = v.floatValue(), ""));
        ai.rows.add(slider("Slab Size", 10, 100, 1, () -> c.aiSlabSize, v -> c.aiSlabSize = v, "%"));
        ai.rows.add(slider("Opacity", 0, 100, 1, () -> c.aiOpacity, v -> c.aiOpacity = v, "%"));
        ai.rows.add(slider("Red", 0, 255, 1, () -> c.aiRed, v -> c.aiRed = v, ""));
        ai.rows.add(slider("Green", 0, 255, 1, () -> c.aiGreen, v -> c.aiGreen = v, ""));
        ai.rows.add(slider("Blue", 0, 255, 1, () -> c.aiBlue, v -> c.aiBlue = v, ""));
        ai.rows.add(new Toggle("Outline", () -> c.aiOutline, v -> c.aiOutline = v));
        ai.rows.add(new Toggle("Fill", () -> c.aiFill, v -> c.aiFill = v));
        ai.rows.add(new Toggle("Through Walls", () -> c.aiThroughWalls, v -> c.aiThroughWalls = v));
        ai.rows.add(new Toggle("Chat On Spotted", () -> c.aiChat, v -> c.aiChat = v));
        ai.rows.add(new Toggle("Show Coordinates", () -> c.aiCoords, v -> c.aiCoords = v));
        ai.rows.add(new Cycle("RTP Region", REGION_NAMES, () -> c.rtpRegion, v -> c.rtpRegion = v));
        ai.rows.add(slider("RTP Delay", 20, 600, 10, () -> c.rtpDelay, v -> c.rtpDelay = v, " t"));
        ai.rows.add(new Toggle("RTP Loop", ActivityInspector::isRtpRunning, ActivityInspector::setRtpRunning));
        ai.rows.add(new Toggle("Crown /tpahere", () -> c.crownEnabled, v -> c.crownEnabled = v));
        ai.rows.add(new Toggle("Crown Feedback", () -> c.crownFeedback, v -> c.crownFeedback = v));
        ai.rows.add(slider("Crown Timeout", 5, 300, 5, () -> c.crownTimeout, v -> c.crownTimeout = v, "s"));
        ai.rows.add(new Toggle("RTP After Timeout", () -> c.rtpAfterTimeout, v -> c.rtpAfterTimeout = v));
        mods.add(ai);
    }

    private Slider slider(String name, double min, double max, double step, Supplier<Integer> get, Consumer<Integer> set, String suffix) {
        return new Slider(name, min, max, step, () -> (double) get.get(), v -> set.accept(v.intValue()), suffix);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int contentHeight() {
        int h = 0;
        for (Mod m : mods) {
            h += ROW;
            if (openModules.contains(m.name)) h += m.rows.size() * ROW;
        }
        return h;
    }

    private int maxScroll() {
        int visible = this.height - (Y + HEAD) - 30;
        return Math.max(0, contentHeight() - visible);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        if (panelOpen) {
            scroll = Math.max(-maxScroll(), Math.min(0, scroll));
            g.enableScissor(X, Y + HEAD, X + W, this.height);
            int y = Y + HEAD + scroll;
            for (Mod m : mods) {
                boolean hov = inside(mx, my, X, y, W, ROW) && my >= Y + HEAD;
                g.fill(X, y, X + W, y + ROW, hov ? HOVER : ROWC);
                if (m.on.get()) g.fill(X, y, X + 2, y + ROW, ON);
                g.drawString(font, m.name, X + 6, y + 5, m.on.get() ? TEXT : DIM, true);
                g.drawString(font, openModules.contains(m.name) ? "-" : "+", X + W - 10, y + 5, DIM, true);
                y += ROW;
                if (openModules.contains(m.name)) {
                    for (Row r : m.rows) {
                        r.draw(g, y, mx, my);
                        y += ROW;
                    }
                }
            }
            g.fill(X, y, X + W, y + 1, HEADC);
            g.drawString(font, "L-click: toggle  R-click: settings", X, y + 5, DIM, true);
            g.drawString(font, "Scroll: move list", X, y + 16, DIM, true);
            g.disableScissor();
        }
        // header drawn last so scrolled rows never cover it
        g.fill(X, Y, X + W, Y + HEAD, HEADC);
        g.drawString(font, SussyClient.NAME, X + 6, Y + 6, TEXT, true);
        g.drawString(font, panelOpen ? "v" : ">", X + W - 12, Y + 6, TEXT, true);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (panelOpen && mx >= X && mx < X + W) {
            scroll += (int) (scrollY * ROW * 2);
            scroll = Math.max(-maxScroll(), Math.min(0, scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();

        if (inside(mx, my, X, Y, W, HEAD)) { panelOpen = !panelOpen; return true; }
        if (!panelOpen || my < Y + HEAD) return super.mouseClicked(event, doubleClick);

        int y = Y + HEAD + scroll;
        for (Mod m : mods) {
            if (inside(mx, my, X, y, W, ROW)) {
                if (button == 0) { m.set.accept(!m.on.get()); Config.save(); }
                else if (button == 1) {
                    if (!openModules.remove(m.name)) openModules.add(m.name);
                }
                return true;
            }
            y += ROW;
            if (openModules.contains(m.name)) {
                for (Row r : m.rows) {
                    if (inside(mx, my, X, y, W, ROW)) {
                        r.click(mx);
                        if (r instanceof Slider) dragging = r;
                        Config.save();
                        return true;
                    }
                    y += ROW;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) { dragging.click(event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) { dragging = null; Config.save(); }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; } // same bind closes it
        return super.keyPressed(event);
    }

    @Override
    public void removed() { Config.save(); }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- model ----------
    private static class Mod {
        final String name; final Supplier<Boolean> on; final Consumer<Boolean> set;
        final List<Row> rows = new ArrayList<>();
        Mod(String name, Supplier<Boolean> on, Consumer<Boolean> set) { this.name = name; this.on = on; this.set = set; }
    }

    private abstract class Row {
        abstract void draw(GuiGraphics g, int y, int mx, int my);
        abstract void click(double mx);
    }

    private class Slider extends Row {
        final String name, suffix; final double min, max, step;
        final Supplier<Double> get; final Consumer<Double> set;
        Slider(String name, double min, double max, double step, Supplier<Double> get, Consumer<Double> set, String suffix) {
            this.name = name; this.min = min; this.max = max; this.step = step; this.get = get; this.set = set; this.suffix = suffix;
        }
        void draw(GuiGraphics g, int y, int mx, int my) {
            g.fill(X, y, X + W, y + ROW, inside(mx, my, X, y, W, ROW) ? HOVER : ROWC);
            double f = (get.get() - min) / (max - min);
            g.fill(X, y, X + (int) (W * Math.max(0, Math.min(1, f))), y + ROW, 0x80B01E1E);
            double val = get.get();
            String shown = step >= 1 ? String.valueOf((int) val) : String.format("%.2f", val);
            g.drawString(font, name + ": " + shown + suffix, X + 6, y + 5, TEXT, true);
        }
        void click(double mx) {
            double f = Math.max(0, Math.min(1, (mx - X) / W));
            double v = min + f * (max - min);
            v = Math.round(v / step) * step;
            set.accept(Math.max(min, Math.min(max, v)));
        }
    }

    private class Toggle extends Row {
        final String name; final Supplier<Boolean> get; final Consumer<Boolean> set;
        Toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set) { this.name = name; this.get = get; this.set = set; }
        void draw(GuiGraphics g, int y, int mx, int my) {
            g.fill(X, y, X + W, y + ROW, inside(mx, my, X, y, W, ROW) ? HOVER : ROWC);
            g.drawString(font, name, X + 6, y + 5, TEXT, true);
            g.drawString(font, get.get() ? "ON" : "OFF", X + W - 26, y + 5, get.get() ? 0xFF55FF55 : 0xFFFF5555, true);
        }
        void click(double mx) { set.accept(!get.get()); }
    }

    private class Cycle extends Row {
        final String name; final String[] options; final Supplier<Integer> get; final Consumer<Integer> set;
        Cycle(String name, String[] options, Supplier<Integer> get, Consumer<Integer> set) {
            this.name = name; this.options = options; this.get = get; this.set = set;
        }
        void draw(GuiGraphics g, int y, int mx, int my) {
            g.fill(X, y, X + W, y + ROW, inside(mx, my, X, y, W, ROW) ? HOVER : ROWC);
            int i = Math.max(0, Math.min(options.length - 1, get.get()));
            g.drawString(font, name + ": " + options[i], X + 6, y + 5, TEXT, true);
        }
        void click(double mx) { set.accept((get.get() + 1) % options.length); }
    }
}
