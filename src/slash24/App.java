package slash24;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Slash24: a dependency-free Swing app styled after KDE Breeze. */
public final class App {
    /** From the jar manifest (see build.sh); "dev" when run from unpackaged classes. */
    static final String VERSION = App.class.getPackage().getImplementationVersion() != null
            ? App.class.getPackage().getImplementationVersion() : "dev";
    private static final String[] PAGE_KEYS = {"calculator", "vlsm", "tools", "config"};
    private static final String[] PAGE_NAMES = {"Calculator", "VLSM planner", "Route tools", "IOS config"};
    private static final String[] MODES = {"system", "light", "dark"};
    private static final String[] MODE_NAMES = {"Match system", "Light", "Dark"};

    final JFrame frame = new JFrame("Slash24");
    private final Preferences prefs;
    /** Pages are built the first time they are shown, which keeps startup short. */
    private final Page[] pages = new Page[PAGE_KEYS.length];
    private final JToggleButton[] tabs = new JToggleButton[PAGE_KEYS.length];
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JButton theme = new JButton();
    private int page;
    /** "system", "dark" or "light". */
    private String mode;

    public static void main(String[] args) {
        if (List.of(args).contains("--version")) {
            System.out.println("Slash24 " + VERSION);
            return;
        }
        Preferences prefs = preferences();
        Ui.theme = Theme.of(darkFor(initialMode(prefs)));
        SwingUtilities.invokeLater(() -> {
            Ui.installLookAndFeel();
            Ui.installTooltipTiming();
            Ui.installInputTracking();
            App app = new App(prefs);
            app.frame.setVisible(true);
            app.followDesktop();
        });
    }

    App(Preferences prefs) {
        this.prefs = prefs;
        mode = initialMode(prefs);

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < PAGE_KEYS.length; i++) {
            int index = i;
            tabs[i] = new JToggleButton(PAGE_NAMES[i]);
            tabs[i].setToolTipText("Ctrl+" + (i + 1));
            group.add(tabs[i]);
            tabs[i].addActionListener(e -> {
                showPage(index);
                prefs.put("page", PAGE_KEYS[index]);
            });
        }
        int start = Math.max(0, List.of(PAGE_KEYS).indexOf(prefs.get("page", "calculator")));
        tabs[start].setSelected(true);
        showPage(start);

        JLabel title = new JLabel("Slash24");
        title.setFont(Ui.SANS.deriveFont(Font.BOLD, 18f));
        theme.setIcon(new Ui.Chevron(true));
        theme.setHorizontalTextPosition(SwingConstants.LEADING);
        theme.setIconTextGap(8);
        theme.setToolTipText("Match the Plasma colour scheme, or choose light or dark (Ctrl+D toggles)");
        theme.addActionListener(e -> themeMenu().show(theme, 0, theme.getHeight()));
        updateThemeButton();
        Ui.plain(theme);
        JPanel header = new JPanel(new HeaderLayout());
        header.add(Ui.row(title, Ui.dim(VERSION)));
        header.add(new Ui.Segmented(tabs));
        header.add(theme);
        header.setBorder(new EmptyBorder(0, 0, 18, 0));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 22, 18, 22));
        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);

        JRootPane rootPane = frame.getRootPane();
        bind(rootPane, "ctrl L", () -> page(page).focusInput());
        bind(rootPane, "ctrl shift C", () -> page(page).copy());
        bind(rootPane, "ctrl D", () -> setMode(Ui.theme.dark() ? "light" : "dark"));
        for (int i = 0; i < tabs.length; i++) {
            JToggleButton tab = tabs[i];
            bind(rootPane, "ctrl " + (i + 1), tab::doClick);
        }

        // start in the page's main input, so typing works at once (Swing would otherwise pick a tab)
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { page(page).focusInput(); }
        });
        frame.setContentPane(root);
        frame.setIconImages(Ui.icons());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 680));
        frame.setSize(1100, 820);
        frame.setLocationRelativeTo(null);
    }

    /**
     * Title on the left, the page switcher in the true centre of the window (not the centre of what's
     * left over), the theme button on the right; all vertically centred.
     */
    private static final class HeaderLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
        @Override public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }

        @Override public Dimension preferredLayoutSize(Container p) {
            int w = 0, h = 0;
            for (Component c : p.getComponents()) {
                w += c.getPreferredSize().width;
                h = Math.max(h, c.getPreferredSize().height);
            }
            Insets in = p.getInsets();
            return new Dimension(w + 48 + in.left + in.right, h + in.top + in.bottom);
        }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int w = p.getWidth() - in.left - in.right, h = p.getHeight() - in.top - in.bottom;
            Component left = p.getComponent(0), center = p.getComponent(1), right = p.getComponent(2);
            Dimension l = left.getPreferredSize(), c = center.getPreferredSize(), r = right.getPreferredSize();
            left.setBounds(in.left, in.top + (h - l.height) / 2, l.width, l.height);
            right.setBounds(in.left + w - r.width, in.top + (h - r.height) / 2, r.width, r.height);
            // centred, unless that would overlap the title or the theme button
            int cx = Math.clamp(in.left + (w - c.width) / 2, in.left + l.width + 24, Math.max(in.left + l.width + 24, in.left + w - r.width - 24 - c.width));
            center.setBounds(cx, in.top + (h - c.height) / 2, c.width, c.height);
        }
    }

    private void showPage(int index) {
        page = index;
        page(index);
        cards.show(content, PAGE_KEYS[index]);
    }

    /** The page at {@code index}, built and added to the window on first use. */
    private Page page(int index) {
        if (pages[index] == null) {
            pages[index] = switch (index) {
                case 0 -> new CalculatorView(prefs);
                case 1 -> new VlsmView(prefs);
                case 2 -> new ToolsView(prefs);
                default -> new ConfigView(prefs, (VlsmView) page(1), () -> tabs[1].doClick()); // uses the VLSM plan
            };
            content.add(pages[index].root(), PAGE_KEYS[index]);
        }
        return pages[index];
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

    /** The app's settings. Settings saved before the rename to Slash24 (1.4.0) are moved over once. */
    static Preferences preferences() {
        Preferences root = Preferences.userRoot(), prefs = root.node("slash24");
        try {
            if (prefs.keys().length == 0 && root.nodeExists("subnetcalc")) {
                Preferences old = root.node("subnetcalc");
                for (String key : old.keys()) prefs.put(key, old.get(key, ""));
                prefs.flush();
                old.removeNode();
                root.flush();
            }
        } catch (BackingStoreException e) {
            // unreadable settings: start with the defaults
        }
        return prefs;
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
        for (Page p : pages) if (p != null) p.refresh();
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
