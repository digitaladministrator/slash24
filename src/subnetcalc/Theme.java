package subnetcalc;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A colour palette. The mode that matches the desktop takes its colours from the KDE colour
 * scheme in {@code kdeglobals}; the other mode, or any desktop without one, uses Breeze defaults.
 */
record Theme(boolean dark, Color window, Color view, Color alt, Color fg, Color dim, Color accent, Color negative) {

    private static final Map<String, String> KDE = readKdeGlobals();
    private static final Color ACCENT = kde("General/AccentColor",
            kde("Colors:Selection/BackgroundNormal", new Color(61, 174, 233)));
    private static final Color KDE_WINDOW = kde("Colors:Window/BackgroundNormal", null);

    /** Whether the desktop colour scheme is dark. Light is assumed when it cannot be read. */
    static final boolean SYSTEM_DARK = KDE_WINDOW != null && luma(KDE_WINDOW) < 128;

    static Theme of(boolean dark) {
        Color negative = new Color(218, 68, 83);
        Theme breeze = dark
                ? new Theme(true, new Color(32, 35, 38), new Color(20, 22, 24), new Color(29, 31, 34),
                        new Color(252, 252, 252), new Color(161, 169, 177), ACCENT, negative)
                : new Theme(false, new Color(239, 240, 241), Color.WHITE, new Color(247, 247, 247),
                        new Color(35, 38, 41), new Color(112, 125, 138), ACCENT, negative);
        if (KDE_WINDOW == null || SYSTEM_DARK != dark) return breeze;
        return new Theme(dark, KDE_WINDOW,
                kde("Colors:View/BackgroundNormal", breeze.view),
                kde("Colors:View/BackgroundAlternate", breeze.alt),
                kde("Colors:Window/ForegroundNormal", breeze.fg),
                kde("Colors:Window/ForegroundInactive", breeze.dim),
                ACCENT,
                kde("Colors:View/ForegroundNegative", negative));
    }

    Color border() { return mix(window, fg, dark ? .2f : .25f); }
    Color selection() { return mix(view, accent, .35f); }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static int luma(Color c) { return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000; }

    private static Map<String, String> readKdeGlobals() {
        Map<String, String> values = new HashMap<>();
        String config = System.getenv("XDG_CONFIG_HOME");
        if (config == null || config.isBlank()) config = System.getProperty("user.home") + "/.config";
        try {
            String section = "";
            for (String line : Files.readAllLines(Path.of(config, "kdeglobals"))) {
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
        String[] rgb = KDE.getOrDefault(key, "").split(",");
        if (rgb.length < 3) return fallback;
        try {
            return new Color(Integer.parseInt(rgb[0].strip()), Integer.parseInt(rgb[1].strip()), Integer.parseInt(rgb[2].strip()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
