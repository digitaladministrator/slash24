package slash24;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

/**
 * A colour palette. The mode that matches the desktop takes its colours from the KDE colour
 * scheme in {@code kdeglobals}; the other mode, or any desktop without one, uses Breeze defaults.
 */
record Theme(boolean dark, Color window, Color view, Color alt, Color fg, Color dim, Color accent,
        Color negative, Color positive) {

    private static Map<String, String> kde = Map.of();
    private static Color kdeAccent, kdeWindow;

    static {
        reload();
    }

    /** Re-reads {@code kdeglobals}. Call on the event thread. */
    static void reload() {
        kde = readKdeGlobals();
        kdeAccent = kde("General/AccentColor", kde("Colors:Selection/BackgroundNormal", new Color(61, 174, 233)));
        kdeWindow = kde("Colors:Window/BackgroundNormal", null);
    }

    /** Whether the desktop colour scheme is dark. Light is assumed when it cannot be read. */
    static boolean systemDark() {
        if (kdeWindow != null) return luma(kdeWindow) < 128;
        if (windowsDark == null) windowsDark = readWindowsDarkMode();
        return windowsDark;
    }

    private static Boolean windowsDark;

    /** Windows keeps its app dark-mode setting in the registry; anything else without KDE is light. */
    private static boolean readWindowsDarkMode() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out.contains("0x0"); // AppsUseLightTheme = 0 means dark
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static Theme of(boolean dark) {
        Color negative = new Color(218, 68, 83);
        Theme breeze = dark
                ? new Theme(true, new Color(32, 35, 38), new Color(20, 22, 24), new Color(29, 31, 34),
                        new Color(252, 252, 252), new Color(161, 169, 177), kdeAccent, negative, new Color(39, 174, 96))
                : new Theme(false, new Color(239, 240, 241), Color.WHITE, new Color(247, 247, 247),
                        new Color(35, 38, 41), new Color(112, 125, 138), kdeAccent, negative, new Color(30, 130, 72));
        if (kdeWindow == null || systemDark() != dark) return breeze;
        return new Theme(dark, kdeWindow,
                kde("Colors:View/BackgroundNormal", breeze.view),
                kde("Colors:View/BackgroundAlternate", breeze.alt),
                kde("Colors:Window/ForegroundNormal", breeze.fg),
                kde("Colors:Window/ForegroundInactive", breeze.dim),
                kdeAccent,
                kde("Colors:View/ForegroundNegative", negative),
                dark ? kde("Colors:View/ForegroundPositive", breeze.positive) : breeze.positive);
    }

    Color border() { return mix(window, fg, dark ? .2f : .25f); }
    Color selection() { return mix(view, accent, .35f); }
    /** Quiet outline for fields, cards and separators: present, but never louder than the content. */
    Color hairline() { return mix(window, fg, dark ? .14f : .15f); }
    /** Fill for secondary buttons and the track of segmented controls. */
    Color control() { return mix(window, fg, dark ? .09f : .07f); }
    /** The raised piece marking the selected segment. */
    Color pill() { return dark ? mix(window, fg, .25f) : view; }

    /** Background for the one primary button on a page: the accent, darkened until white text reads clearly on it. */
    Color primary() {
        Color c = accent;
        for (int i = 0; i < 20 && contrast(c, Color.WHITE) < 4.5; i++) c = mix(c, Color.BLACK, .05f);
        return c;
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + .05) / (Math.min(la, lb) + .05);
    }

    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    /**
     * Plasma's animation speed setting (System Settings > Animation speed): 1 is normal, larger is
     * slower, 0 is instant. Animations scale with it, so turning animations off turns them off here too.
     */
    static double animationScale() {
        try {
            return Math.max(0, Double.parseDouble(kde.getOrDefault("KDE/AnimationDurationFactor", "1")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /**
     * Calls {@code onChange} (on a background thread) whenever {@code kdeglobals} is rewritten,
     * e.g. when the Plasma colour scheme or accent colour changes. Uses inotify, so it costs
     * nothing while idle.
     */
    static void watch(Runnable onChange) {
        Path dir = configDir();
        if (!Files.isDirectory(dir)) return;
        Thread.ofPlatform().daemon().name("kdeglobals-watcher").start(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                dir.register(ws, ENTRY_CREATE, ENTRY_MODIFY);
                while (true) {
                    WatchKey key = ws.take();
                    boolean changed = false;
                    for (WatchEvent<?> e : key.pollEvents()) {
                        if (e.context() instanceof Path p && p.toString().equals("kdeglobals")) changed = true;
                    }
                    if (changed) onChange.run();
                    if (!key.reset()) return;
                }
            } catch (IOException e) {
                // Live updates are a convenience; without a watcher the theme is simply read at startup.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static Path configDir() {
        String config = System.getenv("XDG_CONFIG_HOME");
        return config == null || config.isBlank() ? Path.of(System.getProperty("user.home"), ".config") : Path.of(config);
    }

    private static int luma(Color c) { return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000; }

    private static Map<String, String> readKdeGlobals() {
        Map<String, String> values = new HashMap<>();
        try {
            String section = "";
            for (String line : Files.readAllLines(configDir().resolve("kdeglobals"))) {
                line = line.strip();
                int eq = line.indexOf('=');
                if (line.startsWith("[") && line.endsWith("]")) section = line.substring(1, line.length() - 1);
                else if (eq > 0) values.put(section + '/' + line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        } catch (IOException e) {
            // Not a KDE desktop (or unreadable config): the built-in Breeze palettes are used.
        }
        return values;
    }

    private static Color kde(String key, Color fallback) {
        String[] rgb = kde.getOrDefault(key, "").split(",");
        if (rgb.length < 3) return fallback;
        try {
            return new Color(Integer.parseInt(rgb[0].strip()), Integer.parseInt(rgb[1].strip()), Integer.parseInt(rgb[2].strip()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
