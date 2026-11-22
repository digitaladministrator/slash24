package slash24;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;

/** Shared look and feel, widgets and helpers. Everything paints from the current {@link #theme}. */
public final class Ui {
    private Ui() { }

    static Theme theme;
    /** Windows has no Noto fonts, so it gets its own UI and code fonts. */
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    static final Font SANS = new Font(WINDOWS ? "Segoe UI" : "Noto Sans", Font.PLAIN, 13);
    static final Font MONO = new Font(WINDOWS ? "Consolas" : "Noto Sans Mono", Font.PLAIN, 13);
    static final float ARC = 8, CARD_ARC = 12;

    static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new Laf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hover help shows quickly and stays up long enough to read. */
    static void installTooltipTiming() {
        ToolTipManager t = ToolTipManager.sharedInstance();
        t.setInitialDelay(250);
        t.setDismissDelay(30_000);
    }

    // ---- small helpers -------------------------------------------------------------------------

    static void invalid(JComponent c, boolean invalid) {
        c.putClientProperty("invalid", invalid);
        c.repaint();
    }

    static void status(JLabel label, String text, boolean error) {
        label.setText(text);
        label.setForeground(error ? theme.negative() : theme.dim());
    }

    /** A label in the secondary text colour, re-coloured whenever the theme changes. */
    static JLabel dim(String text) {
        JLabel label = new JLabel(text);
        Consumer<JLabel> style = l -> l.setForeground(theme.dim());
        style.accept(label);
        label.addPropertyChangeListener("UI", e -> style.accept(label));
        return label;
    }

    static JTextField valueField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setBorder(new EmptyBorder(0, 0, 0, 0));
        f.setFont(MONO);
        return f;
    }

    static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(8));
            p.add(parts[i]);
        }
        return p;
    }

    static JPanel footer(Component... buttons) {
        JPanel p = row(buttons);
        ((FlowLayout) p.getLayout()).setAlignment(FlowLayout.RIGHT);
        p.setBorder(new EmptyBorder(12, 0, 0, 0));
        return p;
    }

    static GridBagConstraints at(int x, int y, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 14);
        return c;
    }

    static GridBagConstraints wide(GridBagConstraints c) {
        c.weightx = 1;
        return c;
    }

    /** Constraints for a component that takes all remaining height. */
    static GridBagConstraints fill(GridBagConstraints c) {
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.insets.right = 0;
        return c;
    }

    /**
     * Saves a preference unless it is longer than Java preferences allow, in which case the last saved
     * value is kept. Returns whether it was saved.
     */
    static boolean remember(Preferences prefs, String key, String value) {
        if (value.length() > Preferences.MAX_VALUE_LENGTH) return false;
        prefs.put(key, value);
        return true;
    }

    static void onEdit(JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    /** Temporarily replaces a button's label, e.g. "Copied", then restores it. */
    static void flash(AbstractButton b, String text) {
        if (b.getClientProperty("label") == null) b.putClientProperty("label", b.getText());
        b.setText(text);
        Timer reset = new Timer(1500, e -> b.setText((String) b.getClientProperty("label")));
        reset.setRepeats(false);
        reset.start();
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") instanceof Map<?, ?> hints) {
            g2.addRenderingHints(hints);
        }
        return g2;
    }

    static List<Image> icons() {
        Color accent = theme.accent();
        return List.of(icon(16, accent), icon(32, accent), icon(48, accent), icon(64, accent));
    }

    /** The app icon: "/24" on a rounded square. Also used to generate the packaged icon files. */
    static BufferedImage icon(int size, Color accent) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Float(size * .06f, size * .06f, size * .88f, size * .88f, size * .25f, size * .25f));
        g.setColor(Color.WHITE);
        g.setFont(SANS.deriveFont(Font.BOLD, size * .38f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("/24", (size - fm.stringWidth("/24")) / 2f, (size - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
        return img;
    }

    private static final FocusListener REPAINT_ON_FOCUS = new FocusListener() {
        @Override public void focusGained(FocusEvent e) { e.getComponent().repaint(); }
        @Override public void focusLost(FocusEvent e) { e.getComponent().repaint(); }
    };

    /**
     * Shows an input error only once typing pauses, so half-typed input such as {@code 192.168.}
     * isn't flagged straight away. An error that is already visible updates immediately.
     */
    static final class Problem {
        private final JComponent field;
        private final JLabel label;
        private final Timer timer;
        private String message;
        private boolean shown;

        Problem(JComponent field, JLabel label) {
            this.field = field;
            this.label = label;
            timer = new Timer(800, e -> show());
            timer.setRepeats(false);
        }

        void report(String message) {
            this.message = message;
            if (shown) {
                show();
            } else {
                status(label, " ", false);
                timer.restart();
            }
        }

        void clear() {
            timer.stop();
            if (shown) invalid(field, false);
            shown = false;
        }

        private void show() {
            shown = true;
            invalid(field, true);
            status(label, message, true);
        }
    }

    // ---- tables, clipboard and CSV export ------------------------------------------------------

    static final int CLIPBOARD_ROWS = 100_000;

    /** Copies a table as tab-separated text, which pastes straight into spreadsheet cells. */
    static void copyTable(AbstractButton button, Rows rows) {
        int n = Math.min(rows.getRowCount(), CLIPBOARD_ROWS);
        StringBuilder sb = new StringBuilder();
        tsv(sb, rows.csvHeader());
        for (int r = 0; r < n; r++) tsv(sb, rows.csvRow(r));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        flash(button, n < rows.getRowCount() ? String.format("Copied first %,d rows", n) : "Copied");
    }

    private static void tsv(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(Csv.defuse(cells[i]).replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
        }
        sb.append('\n');
    }

    static JTable tableIn(JScrollPane scroll) { return (JTable) scroll.getViewport().getView(); }

    /** Table model whose rows are computed on demand and can also be exported as CSV. */
    abstract static class Rows extends AbstractTableModel {
        private final String[] columns;

        Rows(String... columns) { this.columns = columns; }

        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        boolean mono(int column) { return false; }
        boolean numeric(int column) { return false; }
        boolean secondary(int column) { return false; }

        abstract String[] csvHeader();
        abstract String[] csvRow(int row);
        /** An unchanging copy that a background export can read while the user keeps editing. */
        abstract Rows snapshot();
    }

    static JScrollPane table(Rows model, int... widths) {
        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, new Cells());
        table.getTableHeader().setDefaultRenderer(new HeaderCells());
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension());
        table.setFillsViewportHeight(true);
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < widths.length; i++) columns.getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = new JScrollPane(table) {
            @Override public void updateUI() {
                super.updateUI();
                setOpaque(false);
                setBorder(new CardBorder());
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(theme.view());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CARD_ARC, CARD_ARC));
                g2.dispose();
            }
        };
        scroll.setPreferredSize(new Dimension(0, 160)); // grows into the remaining height
        return scroll;
    }

    /** Something that writes a file; runs on a background thread. */
    interface FileWriter {
        void write(Path path) throws IOException;
    }

    static void exportCsv(AbstractButton button, Rows model, String suggestedName) {
        Rows data = model.snapshot();
        saveAs(button, suggestedName, "csv", "CSV files", path -> Csv.write(path, data));
    }

    /**
     * Asks where to save (KDE's own dialog when kdialog is installed, otherwise the toolkit's) and
     * writes the file on a background thread so large exports never freeze the window.
     */
    static void saveAs(AbstractButton button, String suggestedName, String extension, String description, FileWriter writer) {
        if (Boolean.TRUE.equals(button.getClientProperty("busy"))) return;
        button.putClientProperty("busy", true);
        Window owner = SwingUtilities.getWindowAncestor(button);
        Thread.ofVirtual().start(() -> {
            String error = null;
            boolean saved = false;
            try {
                Path path = askSavePath(owner, suggestedName, extension, description);
                if (path != null) {
                    writer.write(path);
                    saved = true;
                }
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean ok = saved;
            String problem = error;
            SwingUtilities.invokeLater(() -> {
                button.putClientProperty("busy", false);
                if (ok) flash(button, "Saved");
                if (problem != null) {
                    JOptionPane.showMessageDialog(owner, "Could not save the file:\n" + problem, "Save",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private static Path askSavePath(Window owner, String name, String extension, String description)
            throws IOException, InterruptedException {
        String home = System.getProperty("user.home");
        if (onPath("kdialog")) {
            Process p = new ProcessBuilder("kdialog", "--title", "Save " + description, "--getsavefilename",
                    home + "/" + name, "*." + extension + "|" + description + " (*." + extension + ")")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return p.waitFor() == 0 && !out.isEmpty() ? withExtension(Path.of(out), extension) : null;
        }
        String[] chosen = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                FileDialog d = new FileDialog((Frame) owner, "Save " + description, FileDialog.SAVE);
                d.setDirectory(home);
                d.setFile(name);
                d.setVisible(true);
                if (d.getFile() != null) chosen[0] = d.getDirectory() + d.getFile();
            });
        } catch (InvocationTargetException e) {
            throw new IOException(e.getCause());
        }
        return chosen[0] == null ? null : withExtension(Path.of(chosen[0]), extension);
    }

    private static Path withExtension(Path p, String extension) {
        String n = p.getFileName().toString();
        return n.toLowerCase(Locale.ROOT).endsWith("." + extension) ? p : p.resolveSibling(n + "." + extension);
    }

    private static boolean onPath(String command) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(":")) if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, command))) return true;
        return false;
    }

    static final class Cells extends DefaultTableCellRenderer {
        private static final EmptyBorder PAD = new EmptyBorder(0, 10, 0, 10);

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, v, selected, false, row, column);
            Rows m = (Rows) t.getModel();
            int c = t.convertColumnIndexToModel(column);
            setBorder(PAD);
            setFont(m.mono(c) ? MONO : SANS);
            setHorizontalAlignment(m.numeric(c) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!selected) {
                setBackground(row % 2 == 0 ? theme.view() : theme.alt());
                setForeground(m.secondary(c) ? theme.dim() : theme.fg());
            }
            return this;
        }
    }

    /** Header cells aligned like the column below them: small, secondary, over a hairline. */
    static final class HeaderCells extends DefaultTableCellRenderer {
        private static final Font HEADER = SANS.deriveFont(Font.BOLD, 12f);
        private Theme borderTheme;
        private CompoundBorder border;

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, v, false, false, row, column);
            if (borderTheme != theme) { // the border's colour follows the theme
                borderTheme = theme;
                border = new CompoundBorder(new MatteBorder(0, 0, 1, 0, theme.hairline()), new EmptyBorder(7, 10, 7, 10));
            }
            setFont(HEADER);
            setForeground(theme.dim());
            setBackground(theme.view());
            setHorizontalAlignment(((Rows) t.getModel()).numeric(t.convertColumnIndexToModel(column))
                    ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(border);
            return this;
        }
    }

    /** A scroll pane drawn as a rounded input box, for multi-line text entry. */
    static JScrollPane roundedScroll(JComponent view) {
        view.setOpaque(false);
        JScrollPane scroll = new JScrollPane(view) {
            @Override public void updateUI() {
                super.updateUI();
                setOpaque(false);
                setBorder(new Round());
                if (getViewport() != null) getViewport().setOpaque(false);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(theme.view());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
                g2.dispose();
            }
        };
        view.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) { scroll.repaint(); }
            @Override public void focusLost(FocusEvent e) { scroll.repaint(); }
        });
        return scroll;
    }

    // ---- look and feel -------------------------------------------------------------------------

    /** Basic look and feel with every colour, font and border taken from the current {@link Theme}. */
    public static final class Laf extends BasicLookAndFeel {
        @Override public String getName() { return "Slash24"; }
        @Override public String getID() { return getName(); }
        @Override public String getDescription() { return "Flat look matching the KDE colour scheme"; }
        @Override public boolean isNativeLookAndFeel() { return false; }
        @Override public boolean isSupportedLookAndFeel() { return true; }

        @Override protected void initClassDefaults(UIDefaults d) {
            super.initClassDefaults(d);
            String p = Ui.class.getName() + '$';
            d.put("ButtonUI", p + "FlatButtonUI");
            d.put("ToggleButtonUI", p + "FlatButtonUI");
            d.put("TextFieldUI", p + "FlatTextFieldUI");
            d.put("ScrollBarUI", p + "FlatScrollBarUI");
        }

        /**
         * BasicLookAndFeel defines no keyboard bindings (Backspace, Delete, arrows, Ctrl+C/V…); every
         * concrete look and feel supplies its own. Borrow the standard cross-platform set from Metal.
         * The values are immutable lazy input maps, so they are read once and shared.
         */
        private static final Map<Object, Object> KEY_BINDINGS = keyBindings();

        private static Map<Object, Object> keyBindings() {
            Map<Object, Object> bindings = new HashMap<>();
            UIDefaults metal = new MetalLookAndFeel().getDefaults();
            for (Object key : metal.keySet().toArray()) {
                if (key instanceof String s && (s.contains("InputMap") || s.endsWith("Bindings"))) bindings.put(key, metal.get(key));
            }
            return Map.copyOf(bindings);
        }

        @Override protected void initComponentDefaults(UIDefaults d) {
            super.initComponentDefaults(d);
            d.putAll(KEY_BINDINGS);
            Theme t = theme;
            ColorUIResource window = new ColorUIResource(t.window()), view = new ColorUIResource(t.view()),
                    fg = new ColorUIResource(t.fg()), dim = new ColorUIResource(t.dim()),
                    selection = new ColorUIResource(t.selection());
            FontUIResource font = new FontUIResource(SANS);
            for (Object key : d.keySet().toArray()) if (key instanceof String s && s.endsWith(".font")) d.put(key, font);
            d.putDefaults(new Object[] {
                "control", window, "window", view, "text", fg, "controlText", fg, "textText", fg,
                "textHighlight", selection, "textHighlightText", fg, "info", view, "infoText", fg,
                "Panel.background", window, "Panel.foreground", fg,
                "Label.foreground", fg, "Label.disabledForeground", dim,
                "Button.background", window, "Button.foreground", fg, "Button.disabledText", dim,
                "ToggleButton.background", window, "ToggleButton.foreground", fg,
                "OptionPane.background", window, "OptionPane.messageForeground", fg,
                "TextField.background", view, "TextField.inactiveBackground", window, "TextField.foreground", fg,
                "TextField.inactiveForeground", dim, "TextField.caretForeground", fg,
                "TextField.selectionBackground", selection, "TextField.selectionForeground", fg,
                "TextField.border", new Round(),
                "TextArea.background", view, "TextArea.foreground", fg, "TextArea.caretForeground", fg,
                "TextArea.selectionBackground", selection, "TextArea.selectionForeground", fg,
                "TextArea.margin", new InsetsUIResource(0, 0, 0, 0),
                "Table.background", view, "Table.foreground", fg, "Table.gridColor", new ColorUIResource(t.border()),
                "Table.selectionBackground", selection, "Table.selectionForeground", fg,
                "Table.focusCellHighlightBorder", new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0),
                "TableHeader.background", view, "TableHeader.foreground", dim,
                "ScrollPane.background", view, "Viewport.background", view,
                "ScrollPane.border", new BorderUIResource(new LineBorder(t.border())),
                "ScrollBar.width", 10, "ScrollBar.background", view, "ScrollBar.track", view, "ScrollBar.thumb", dim,
                "PopupMenu.background", view, "PopupMenu.foreground", fg,
                "PopupMenu.border", new BorderUIResource(new CompoundBorder(new LineBorder(t.border()), new EmptyBorder(4, 0, 4, 0))),
                "MenuItem.background", view, "MenuItem.foreground", fg, "MenuItem.disabledForeground", dim,
                "MenuItem.selectionBackground", selection, "MenuItem.selectionForeground", fg,
                "MenuItem.border", new BorderUIResource.EmptyBorderUIResource(5, 4, 5, 12),
                "Separator.foreground", new ColorUIResource(t.border()), "Separator.background", view,
                "PopupMenuSeparator.foreground", new ColorUIResource(t.border()), "PopupMenuSeparator.background", view,
                "ToolTip.background", view, "ToolTip.foreground", fg,
                "ToolTip.border", new BorderUIResource(new CompoundBorder(new LineBorder(t.border()), new EmptyBorder(4, 6, 4, 6))),
            });
        }
    }

    /** A 2 px accent ring just inside the edge, the one focus indicator used everywhere. */
    static void focusRing(Graphics2D g2, int w, int h, float arc) {
        g2.setColor(theme.accent());
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc));
    }

    /**
     * Rounded input outline: a quiet hairline at rest, the accent focus ring while typing, and the
     * negative colour when the input is invalid.
     */
    static final class Round extends AbstractBorder implements UIResource {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            g2.translate(x, y);
            boolean invalid = Boolean.TRUE.equals(((JComponent) c).getClientProperty("invalid"));
            boolean focused = c.hasFocus()
                    || c instanceof JScrollPane s && s.getViewport().getView() != null && s.getViewport().getView().hasFocus();
            if (invalid) {
                g2.setColor(theme.negative());
                g2.setStroke(new BasicStroke(focused ? 2f : 1.5f));
                g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, ARC, ARC));
            } else if (focused) {
                focusRing(g2, w, h, ARC);
            } else {
                g2.setColor(theme.hairline());
                g2.draw(new RoundRectangle2D.Float(.5f, .5f, w - 1, h - 1, ARC, ARC));
            }
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(6, 10, 6, 10);
            return insets;
        }
    }

    /**
     * Outline of a card-style scroll pane; its insets keep square content inside the rounded corners.
     * Deliberately not a UIResource: JTable replaces a UIResource border on its scroll pane with the
     * look and feel's bevelled "Table.scrollPaneBorder" when it is added to a window.
     */
    static final class CardBorder extends AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            g2.setColor(theme.hairline());
            g2.draw(new RoundRectangle2D.Float(x + .5f, y + .5f, w - 1, h - 1, CARD_ARC, CARD_ARC));
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(3, 3, 3, 3);
            return insets;
        }
    }

    public static final class FlatTextFieldUI extends BasicTextFieldUI {
        public static ComponentUI createUI(JComponent c) { return new FlatTextFieldUI(); }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
            c.addFocusListener(REPAINT_ON_FOCUS);
        }

        @Override public void uninstallUI(JComponent c) {
            c.removeFocusListener(REPAINT_ON_FOCUS);
            super.uninstallUI(c);
        }

        @Override protected void paintSafely(Graphics g) {
            JTextComponent c = getComponent();
            if (c.isEditable()) {
                Graphics2D g2 = smooth(g);
                g2.setColor(c.getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), ARC, ARC));
                g2.dispose();
            }
            super.paintSafely(g);
        }
    }

    /**
     * Flat rounded buttons in four styles, set with the "style" client property: the default quiet
     * fill, "primary" (accent fill, one per page), "plain" (fill only on hover) and "segment" (part of
     * a {@link Segmented} control, which draws the selection). Feedback shows on press, not release.
     */
    public static final class FlatButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new FlatButtonUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setRequestFocusEnabled(false); // clicks don't take focus, so the focus ring means keyboard focus
            Object style = b.getClientProperty("style");
            b.setBorder("segment".equals(style) ? new BorderUIResource.EmptyBorderUIResource(4, 12, 4, 12)
                    : Boolean.TRUE.equals(b.getClientProperty("compact")) ? new BorderUIResource.EmptyBorderUIResource(4, 10, 4, 10)
                    : new BorderUIResource.EmptyBorderUIResource(7, 16, 7, 16));
            if ("primary".equals(style)) b.setFont(SANS.deriveFont(Font.BOLD));
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Object style = b.getClientProperty("style");
            boolean down = m.isPressed() && m.isArmed(), hover = m.isRollover() && b.isEnabled();
            Graphics2D g2 = smooth(g);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), ARC, ARC);
            Color fill = null;
            if ("primary".equals(style)) {
                Color p = theme.primary();
                fill = !b.isEnabled() ? Theme.mix(theme.window(), p, .35f)
                        : down ? Theme.mix(p, Color.BLACK, .15f) : hover ? Theme.mix(p, Color.WHITE, .1f) : p;
            } else if ("segment".equals(style)) {
                if (!m.isSelected() && b.isEnabled() && (hover || down)) fill = Theme.mix(theme.control(), theme.fg(), down ? .1f : .05f);
            } else if ("plain".equals(style)) {
                if (b.isEnabled() && (hover || down)) fill = Theme.mix(theme.window(), theme.fg(), down ? .14f : .08f);
            } else {
                fill = !b.isEnabled() ? Theme.mix(theme.window(), theme.control(), .5f)
                        : Theme.mix(theme.control(), theme.fg(), down ? .1f : hover ? .05f : 0f);
            }
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(shape);
            }
            if (b.isFocusOwner()) focusRing(g2, c.getWidth(), c.getHeight(), ARC);
            g2.dispose();
            super.paint(g, c);
        }

        @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle v, Rectangle t, Rectangle i) { }
        @Override protected void paintButtonPressed(Graphics g, AbstractButton b) { }
    }

    public static final class FlatScrollBarUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new FlatScrollBarUI(); }

        @Override protected JButton createDecreaseButton(int orientation) { return noButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return noButton(); }

        private static JButton noButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension());
            b.setMinimumSize(new Dimension());
            b.setMaximumSize(new Dimension());
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(theme.view());
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(isThumbRollover() || isDragging ? theme.dim() : Theme.mix(theme.view(), theme.dim(), .55f));
            g2.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
            g2.dispose();
        }
    }

    // ---- motion --------------------------------------------------------------------------------

    /**
     * A critically damped spring (no overshoot) driving one value toward a target. Retargeting keeps
     * the current position and velocity, so a second click mid-motion redirects smoothly instead of
     * jumping or restarting. {@code response} is roughly the time to settle, in seconds, before Plasma's
     * animation speed setting is applied; with animations off the value jumps straight to its target.
     */
    static final class Spring {
        private final double response;
        private final Runnable onFrame;
        private final Timer timer;
        private double value, velocity, target;
        private long last;

        Spring(double value, double response, Runnable onFrame) {
            this.value = target = value;
            this.response = response;
            this.onFrame = onFrame;
            timer = new Timer(1000 / 120, e -> step());
            timer.setCoalesce(true);
        }

        double value() { return value; }
        boolean moving() { return timer.isRunning(); }

        void jump(double to) {
            timer.stop();
            value = target = to;
            velocity = 0;
        }

        void to(double to) {
            target = to;
            if (Theme.animationScale() == 0) {
                jump(to);
                onFrame.run();
            } else if (!timer.isRunning()) {
                last = System.nanoTime();
                timer.start();
            }
        }

        private void step() {
            long now = System.nanoTime();
            double dt = Math.min((now - last) / 1e9, .05);
            last = now;
            double omega = 2 * Math.PI / (response * Theme.animationScale());
            for (int i = 0; i < 4; i++) { // a few small steps keep the integration stable on slow frames
                double h = dt / 4;
                velocity += (-omega * omega * (value - target) - 2 * omega * velocity) * h;
                value += velocity * h;
            }
            if (Math.abs(value - target) < .3 && Math.abs(velocity) < 3) jump(target);
            onFrame.run();
        }
    }

    // ---- custom components ---------------------------------------------------------------------

    /** Marks the one main action of a page: filled with the accent, white text. */
    static <B extends AbstractButton> B primary(B b) {
        b.putClientProperty("style", "primary");
        b.setForeground(Color.WHITE);
        b.updateUI();
        return b;
    }

    /** A button with no fill until hovered, for actions that shouldn't compete with the content. */
    static <B extends AbstractButton> B plain(B b) {
        b.putClientProperty("style", "plain");
        b.updateUI();
        return b;
    }

    /** A rounded surface that groups related content, like a grouped list on macOS. */
    static JPanel card(JComponent content) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                RoundRectangle2D shape = new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1, getHeight() - 1, CARD_ARC, CARD_ARC);
                g2.setColor(theme.view());
                g2.fill(shape);
                g2.setColor(theme.hairline());
                g2.draw(shape);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        content.setOpaque(false);
        p.setBorder(new EmptyBorder(10, 14, 10, 14));
        p.add(content);
        return p;
    }

    /**
     * Mutually exclusive choices sharing one track, with a raised pill under the selected one that
     * glides to a new choice (macOS segmented control). The buttons keep their own action listeners
     * and the caller's button group.
     */
    static class Segmented extends JPanel {
        private final List<AbstractButton> segments = new java.util.ArrayList<>();
        private final Spring x = new Spring(0, .3, this::repaint), w = new Spring(0, .3, this::repaint);
        private boolean placed;

        Segmented(AbstractButton... buttons) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(2, 2, 2, 2));
            for (AbstractButton b : buttons) addSegment(b);
        }

        final void addSegment(AbstractButton b) {
            b.putClientProperty("style", "segment");
            b.updateUI();
            b.addItemListener(e -> glide());
            segments.add(b);
            add(b);
        }

        private AbstractButton chosen() {
            for (AbstractButton b : segments) if (b.isSelected()) return b;
            return null;
        }

        private void glide() {
            AbstractButton s = chosen();
            if (s != null && placed && s.getWidth() > 0) {
                x.to(s.getX());
                w.to(s.getWidth());
            }
            repaint();
        }

        @Override public void doLayout() {
            super.doLayout();
            AbstractButton s = chosen();
            if (s != null && s.getWidth() > 0 && !x.moving()) { // settle in place after any layout change
                x.jump(s.getX());
                w.jump(s.getWidth());
                placed = true;
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(theme.control());
            // only as wide as the segments, even when a layout stretches the control across a row
            AbstractButton last = segments.isEmpty() ? null : segments.getLast();
            float trackWidth = last == null ? getWidth() : Math.min(getWidth(), last.getX() + last.getWidth() + getInsets().right);
            g2.fill(new RoundRectangle2D.Float(0, 0, trackWidth, getHeight(), ARC + 4, ARC + 4));
            if (chosen() != null && placed) {
                Insets in = getInsets();
                float px = (float) x.value(), pw = (float) w.value(), ph = getHeight() - in.top - in.bottom;
                if (!theme.dark()) { // a soft drop shadow lifts the pill off the track
                    g2.setColor(new Color(0, 0, 0, 22));
                    g2.fill(new RoundRectangle2D.Float(px, in.top + 1, pw, ph, ARC, ARC));
                }
                g2.setColor(theme.pill());
                g2.fill(new RoundRectangle2D.Float(px, in.top, pw, ph, ARC, ARC));
            }
            g2.dispose();
        }
    }

    /** On/off switch whose knob glides to its new side (0 = off, 1 = on). */
    static final class Switch extends JComponent {
        private boolean on;
        private final Consumer<Boolean> action;
        private final Spring knob;

        Switch(boolean on, String tooltip, Consumer<Boolean> action) {
            this.on = on;
            this.action = action;
            knob = new Spring(on ? 1 : 0, .25, this::repaint);
            setFocusable(true);
            setRequestFocusEnabled(false); // like buttons: the focus ring only appears with the keyboard
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tooltip);
            addFocusListener(REPAINT_ON_FOCUS);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { toggle(); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) toggle();
                }
            });
        }

        boolean isOn() { return on; }

        void toggle() {
            if (!isEnabled()) return;
            on = !on;
            knob.to(on ? 1 : 0); // the knob moves first, so feedback is immediate even if the action is slow
            action.accept(on);
            repaint();
        }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setFocusable(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        /** A clickable label that toggles this switch. */
        JLabel label(String text) {
            JLabel l = dim(text);
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggle(); }
            });
            return l;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(38, 22); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.SrcOver.derive(.45f));
            int w = getWidth(), h = getHeight(), d = h - 6;
            float t = (float) Math.clamp(knob.value(), 0, 1);
            g2.setColor(Theme.mix(Theme.mix(theme.window(), theme.fg(), .22f), theme.accent(), t)); // track colour follows the knob
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            float kx = 3 + t * (w - d - 6);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fill(new Ellipse2D.Float(kx, 3.8f, d, d));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(kx, 3, d, d));
            if (isFocusOwner()) focusRing(g2, w, h, h);
            g2.dispose();
        }
    }

    /** 32 bits in dotted groups of eight, with the network part in the accent colour. */
    static final class Bits extends JComponent {
        private int value, prefix;
        private boolean valid;

        Bits() { setFont(MONO); }

        void set(int value, int prefix, boolean valid) {
            this.value = value;
            this.prefix = prefix;
            this.valid = valid;
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.charWidth('0') * 35, fm.getHeight() + 4);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int cw = fm.charWidth('0'), y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent(), x = 0;
            if (!valid) {
                g2.setColor(theme.fg());
                g2.drawString("—", 0, y);
            } else {
                char[] digit = new char[1];
                for (int i = 0; i < 32; i++) {
                    if (i > 0 && i % 8 == 0) {
                        g2.setColor(theme.dim());
                        g2.drawString(".", x, y);
                        x += cw;
                    }
                    g2.setColor(i < prefix ? theme.accent() : theme.fg());
                    digit[0] = (value >>> (31 - i) & 1) == 1 ? '1' : '0';
                    g2.drawChars(digit, 0, 1, x, y);
                    x += cw;
                }
            }
            g2.dispose();
        }
    }

    /** Small arrow that points down (open, or a dropdown) or right (closed). */
    static final class Chevron implements Icon {
        boolean down;

        Chevron(boolean down) { this.down = down; }

        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = smooth(g);
            g2.setColor(theme.dim());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            if (down) {
                p.moveTo(x + 2, y + 4);
                p.lineTo(x + 6, y + 8);
                p.lineTo(x + 10, y + 4);
            } else {
                p.moveTo(x + 4, y + 2);
                p.lineTo(x + 8, y + 6);
                p.lineTo(x + 4, y + 10);
            }
            g2.draw(p);
            g2.dispose();
        }
    }

    /** A check mark for the selected menu entry; an equal-sized blank keeps the others aligned. */
    static final class Check implements Icon {
        private final boolean on;

        Check(boolean on) { this.on = on; }

        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            if (!on) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(theme.accent());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            p.moveTo(x + 2, y + 7);
            p.lineTo(x + 6, y + 11);
            p.lineTo(x + 12, y + 3);
            g2.draw(p);
            g2.dispose();
        }
    }

    /**
     * A small circled "?" that shows a setting's description and an example when hovered.
     * Tooltips stay up long enough to read (see {@link #installTooltipTiming()}).
     */
    static final class HelpIcon extends JComponent {
        private boolean hover;

        HelpIcon(Help.Entry entry) {
            setEntry(entry);
            setFocusable(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setEntry(Help.Entry entry) {
            setToolTipText(entry == null ? null : tooltip(entry));
            setVisible(entry != null);
        }

        private static String tooltip(Help.Entry e) {
            return "<html><div style='width:260px'>" + escape(e.text())
                    + "<br><br><b>Example:</b><br><code>" + escape(e.example()).replace("\n", "<br>") + "</code></div></html>";
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        @Override public Dimension getPreferredSize() { return new Dimension(15, 15); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(hover ? theme.accent() : Theme.mix(theme.window(), theme.dim(), .6f)); // present, but quieter than the label
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Float(1, 1, 13, 13));
            g2.setFont(SANS.deriveFont(Font.BOLD, 10f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("?", (15 - fm.stringWidth("?")) / 2f, (15 - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    /** {@code label} followed by its help icon, when {@link Help} has an entry for it. */
    static JComponent withHelp(JLabel label, String section) {
        Help.Entry entry = Help.find(section, label.getText());
        if (entry == null) return label;
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(label);
        p.add(Box.createHorizontalStrut(5));
        p.add(new HelpIcon(entry));
        return p;
    }

    /** The help icon inside a {@link #withHelp} box, if any. */
    static HelpIcon helpIn(JComponent box) {
        for (Component c : box.getComponents()) if (c instanceof HelpIcon h) return h;
        return null;
    }
}
