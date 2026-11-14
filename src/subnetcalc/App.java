package subnetcalc;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Subnet Calculator: a dependency-free Swing app styled after KDE Breeze. */
public final class App {
    /** From the jar manifest (see build.sh); "dev" when run from unpackaged classes. */
    static final String VERSION = App.class.getPackage().getImplementationVersion() != null
            ? App.class.getPackage().getImplementationVersion() : "dev";
    private static final String[] PAGE_KEYS = {"calculator", "vlsm", "tools", "config"};

    final JFrame frame = new JFrame("Subnet Calculator");
    private final Preferences prefs;
    private final List<Page> pages;
    private final JToggleButton[] tabs = new JToggleButton[PAGE_KEYS.length];
    private static final String[] MODES = {"system", "light", "dark"};
    private static final String[] MODE_NAMES = {"Match system", "Light", "Dark"};
    private final JButton theme = new JButton();
    private int page;
    /** "system", "dark" or "light". */
    private String mode;

    public static void main(String[] args) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        Ui.theme = Theme.of(darkFor(initialMode(prefs)));
        SwingUtilities.invokeLater(() -> {
            Ui.installLookAndFeel();
            Ui.installTooltipTiming();
            App app = new App(prefs);
            app.frame.setVisible(true);
            app.followDesktop();
        });
    }

    App(Preferences prefs) {
        this.prefs = prefs;
        mode = initialMode(prefs);
        VlsmView vlsm = new VlsmView(prefs);
        pages = List.of(new CalculatorView(prefs), vlsm, new ToolsView(prefs), new ConfigView(prefs, vlsm, () -> tabs[1].doClick()));

        CardLayout cards = new CardLayout();
        JPanel content = new JPanel(cards);
        ButtonGroup group = new ButtonGroup();
        String[] names = {"Calculator", "VLSM planner", "Route tools", "IOS config"};
        for (int i = 0; i < pages.size(); i++) {
            int index = i;
            content.add(pages.get(i).root(), PAGE_KEYS[i]);
            tabs[i] = new JToggleButton(names[i]);
            tabs[i].setToolTipText("Ctrl+" + (i + 1));
            group.add(tabs[i]);
            tabs[i].addActionListener(e -> {
                page = index;
                cards.show(content, PAGE_KEYS[index]);
                prefs.put("page", PAGE_KEYS[index]);
            });
        }
        page = Math.max(0, List.of(PAGE_KEYS).indexOf(prefs.get("page", "calculator")));
        tabs[page].setSelected(true);
        cards.show(content, PAGE_KEYS[page]);

        JLabel title = new JLabel("Subnet Calculator");
        title.setFont(Ui.SANS.deriveFont(Font.BOLD, 18f));
        theme.setIcon(new Ui.Chevron(true));
        theme.setHorizontalTextPosition(SwingConstants.LEADING);
        theme.setIconTextGap(8);
        theme.setToolTipText("Match the Plasma colour scheme, or choose light or dark (Ctrl+D toggles)");
        theme.addActionListener(e -> themeMenu().show(theme, 0, theme.getHeight()));
        updateThemeButton();
        JPanel header = new JPanel(new BorderLayout());
        header.add(Ui.row(title, Ui.dim(VERSION), Box.createHorizontalStrut(12), tabs[0], tabs[1], tabs[2], tabs[3]), BorderLayout.WEST);
        header.add(Ui.row(theme), BorderLayout.EAST);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);

        JRootPane rootPane = frame.getRootPane();
        bind(rootPane, "ctrl L", () -> pages.get(page).focusInput());
        bind(rootPane, "ctrl shift C", () -> pages.get(page).copy());
        bind(rootPane, "ctrl D", () -> setMode(Ui.theme.dark() ? "light" : "dark"));
        for (int i = 0; i < tabs.length; i++) {
            JToggleButton tab = tabs[i];
            bind(rootPane, "ctrl " + (i + 1), tab::doClick);
        }

        frame.setContentPane(root);
        frame.setIconImages(Ui.icons());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 680));
        frame.setSize(1100, 820);
        frame.setLocationRelativeTo(null);
    }

    private void updateThemeButton() {
        theme.setText("Theme: " + MODE_NAMES[List.of(MODES).indexOf(mode)]);
    }

    /** The theme dropdown: one entry per mode, with a check mark on the current one. */
    private JPopupMenu themeMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (int i = 0; i < MODES.length; i++) {
            String m = MODES[i];
            JMenuItem item = new JMenuItem(MODE_NAMES[i], new Ui.Check(m.equals(mode)));
            item.addActionListener(e -> setMode(m));
            menu.add(item);
        }
        return menu;
    }

    static String initialMode(Preferences prefs) {
        String saved = prefs.get("theme", null);
        if (saved != null) return saved;
        String legacy = prefs.get("dark", null); // 1.0/1.1 stored only a dark flag
        return legacy == null ? "system" : Boolean.parseBoolean(legacy) ? "dark" : "light";
    }

    static boolean darkFor(String mode) {
        return mode.equals("system") ? Theme.systemDark() : mode.equals("dark");
    }

    private void setMode(String newMode) {
        mode = newMode;
        prefs.put("theme", newMode);
        prefs.remove("dark");
        updateThemeButton();
        applyTheme();
    }

    private void applyTheme() {
        Theme next = Theme.of(darkFor(mode));
        if (next.equals(Ui.theme)) return;
        Ui.theme = next;
        Ui.installLookAndFeel();
        SwingUtilities.updateComponentTreeUI(frame);
        frame.setIconImages(Ui.icons());
        pages.forEach(Page::refresh);
    }

    /** Re-reads the KDE colours shortly after kdeglobals changes (Plasma writes it several times per change). */
    private void followDesktop() {
        Timer debounce = new Timer(400, e -> {
            Theme.reload();
            applyTheme();
        });
        debounce.setRepeats(false);
        Theme.watch(() -> SwingUtilities.invokeLater(debounce::restart));
    }

    private static void bind(JRootPane rootPane, String key, Runnable action) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        rootPane.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }
}
