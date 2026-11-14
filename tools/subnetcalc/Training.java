package subnetcalc;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/**
 * Build-time helper, not shipped in the app: builds and paints every page off screen (no display
 * needed) so that {@code -XX:DumpLoadedClassList} records the classes a real launch loads. Installing
 * turns that list into a class-data-sharing archive, which makes the app start faster.
 */
final class Training {
    private Training() { }

    public static void main(String[] args) throws Exception {
        Preferences prefs = Preferences.userRoot().node("subnetcalc-training-" + ProcessHandle.current().pid());
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (boolean dark : new boolean[] {true, false}) {
                    Ui.theme = Theme.of(dark);
                    Ui.installLookAndFeel();
                    VlsmView vlsm = new VlsmView(prefs);
                    for (Page page : List.of(new CalculatorView(prefs), vlsm, new ToolsView(prefs), new ConfigView(prefs, vlsm, () -> { }))) {
                        paint(page.root());
                    }
                }
            });
        } finally {
            try {
                prefs.removeNode();
                prefs.flush();
            } catch (BackingStoreException ignored) {
                // a leftover empty preferences node is harmless
            }
        }
        System.exit(0);
    }

    private static void paint(Container page) {
        page.setSize(1100, 760);
        layout(page);
        BufferedImage img = new BufferedImage(1100, 760, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        page.paint(g);
        g.dispose();
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container k) layout(k);
    }
}
