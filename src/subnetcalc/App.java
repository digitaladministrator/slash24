package subnetcalc;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;

/** Subnet Calculator: a dependency-free Swing app styled after KDE Breeze. */
public final class App {

    static Theme theme;
    static final Font UI = new Font("Noto Sans", Font.PLAIN, 13);
    static final Font MONO = new Font("Noto Sans Mono", Font.PLAIN, 13);
    private static final float ARC = 8;
    private static final String[] KEYS = {
        "Network", "Broadcast", "First host", "Last host", "Usable hosts", "Total addresses",
        "Netmask", "Wildcard", "Prefix", "Class", "Type", "Hex"};

    private final Preferences prefs;
    final JFrame frame = new JFrame("Subnet Calculator");
    private final JTextField input = new JTextField("192.168.1.10/24");
    private final JTextField split = new JTextField("26", 3);
    private final JLabel message = new JLabel(" ");
    private final JLabel splitInfo = new JLabel(" ");
    private final JTextField[] values = new JTextField[KEYS.length];
    private final Bits addressBits = new Bits(), maskBits = new Bits();
    private final SubnetModel model = new SubnetModel();

    public static void main(String[] args) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        theme = Theme.of(prefs.getBoolean("dark", Theme.SYSTEM_DARK));
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            new App(prefs).frame.setVisible(true);
        });
    }

    App(Preferences prefs) {
        this.prefs = prefs;

        JLabel title = new JLabel("Subnet Calculator");
        title.setFont(UI.deriveFont(Font.BOLD, 18f));
        Switch darkSwitch = new Switch(theme.dark(), this::setDark);
        JLabel darkLabel = dim(new JLabel("Dark mode"));
        darkLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { darkSwitch.toggle(); }
        });
        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(row(darkLabel, darkSwitch), BorderLayout.EAST);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        input.setFont(MONO.deriveFont(15f));
        split.setFont(MONO);
        JPanel grid = new JPanel(new GridBagLayout());
        int y = 0;
        grid.add(dim(new JLabel("IPv4 address with prefix or netmask")), at(0, y++, 4));
        grid.add(input, at(0, y++, 4));
        grid.add(message, at(0, y++, 4));
        for (int i = 0; i < KEYS.length; i++) values[i] = valueField();
        for (int r = 0; r < 6; r++, y++) {
            grid.add(dim(new JLabel(KEYS[r])), at(0, y, 1));
            grid.add(values[r], wide(at(1, y, 1)));
            grid.add(dim(new JLabel(KEYS[r + 6])), at(2, y, 1));
            grid.add(values[r + 6], wide(at(3, y, 1)));
        }
        grid.add(dim(new JLabel("Address bits")), at(0, y, 1));
        grid.add(addressBits, at(1, y++, 3));
        grid.add(dim(new JLabel("Mask bits")), at(0, y, 1));
        grid.add(maskBits, at(1, y++, 3));
        GridBagConstraints c = at(0, y++, 4);
        c.insets = new Insets(16, 0, 6, 0);
        grid.add(row(dim(new JLabel("Split into  /")), split, splitInfo), c);

        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, new Cells());
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension());
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {60, 150, 270, 130, 100};
        for (int i = 0; i < widths.length; i++) columns.getColumn(i).setPreferredWidth(widths[i]);
        c = at(0, y, 4);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(0, 160)); // grows into the remaining height via weighty
        grid.add(scroll, c);

        JButton copy = new JButton("Copy results");
        copy.addActionListener(e -> copy(copy));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(copy);
        footer.setBorder(new EmptyBorder(12, 0, 0, 0));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        root.add(header, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        onEdit(input, this::update);
        onEdit(split, this::update);
        update();

        frame.setContentPane(root);
        frame.setIconImages(List.of(icon(16), icon(32), icon(48), icon(64)));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(640, 600));
        frame.setSize(780, 740);
        frame.setLocationRelativeTo(null);
    }

    private void update() {
        Subnet s;
        try {
            s = Subnet.parse(input.getText());
        } catch (IllegalArgumentException e) {
            invalid(input, true);
            status(message, e.getMessage(), true);
            for (JTextField v : values) v.setText("—");
            addressBits.set(0, 0, false);
            maskBits.set(0, 0, false);
            status(splitInfo, " ", false);
            model.set(null, 0);
            return;
        }
        invalid(input, false);
        String text = input.getText().strip();
        boolean classful = text.indexOf('/') < 0 && text.chars().noneMatch(Character::isWhitespace);
        status(message, classful ? "No prefix given, so the classful default /" + s.prefix() + " is used" : " ", false);

        int p = s.prefix();
        String[] v = {
            Subnet.format(s.network()) + "/" + p, Subnet.format(s.broadcast()),
            Subnet.format(s.firstHost()), Subnet.format(s.lastHost()),
            String.format("%,d", s.usable()), String.format("%,d", s.total()),
            Subnet.format(s.mask()), Subnet.format(s.wildcard()), "/" + p,
            String.valueOf(s.addressClass()), s.type(), String.format("0x%08X", s.address())};
        for (int i = 0; i < v.length; i++) {
            values[i].setText(v[i]);
            values[i].setCaretPosition(0);
        }
        addressBits.set(s.address(), p, true);
        maskBits.set(s.mask(), p, true);

        int sp;
        try {
            sp = Subnet.parsePrefix(split.getText().strip());
            if (sp < p) throw new IllegalArgumentException("Choose /" + p + " or longer to split a /" + p);
        } catch (IllegalArgumentException e) {
            invalid(split, true);
            status(splitInfo, e.getMessage(), true);
            model.set(null, 0);
            return;
        }
        invalid(split, false);
        long n = s.subnetCount(sp);
        status(splitInfo, String.format("%,d %s × %,d usable hosts%s", n, n == 1 ? "subnet" : "subnets",
                new Subnet(0, sp).usable(), n > SubnetModel.MAX ? String.format(", showing the first %,d", SubnetModel.MAX) : ""), false);
        model.set(s, sp);
    }

    private void setDark(boolean dark) {
        prefs.putBoolean("dark", dark);
        theme = Theme.of(dark);
        installLookAndFeel();
        SwingUtilities.updateComponentTreeUI(frame);
        frame.setIconImages(List.of(icon(16), icon(32), icon(48), icon(64)));
        update();
    }

    private void copy(JButton button) {
        StringBuilder sb = new StringBuilder("Input: ").append(input.getText().strip()).append('\n');
        for (int i = 0; i < KEYS.length; i++) sb.append(KEYS[i]).append(": ").append(values[i].getText()).append('\n');
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        button.setText("Copied");
        Timer reset = new Timer(1500, e -> button.setText("Copy results"));
        reset.setRepeats(false);
        reset.start();
    }

    // ---- small helpers -------------------------------------------------------------------------

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new Laf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void invalid(JComponent c, boolean invalid) {
        c.putClientProperty("invalid", invalid);
        c.repaint();
    }

    private static void status(JLabel label, String text, boolean error) {
        label.setText(text);
        label.setForeground(error ? theme.negative() : theme.dim());
    }

    /** A label drawn in the secondary text colour, re-coloured whenever the theme changes. */
    private static JLabel dim(JLabel label) {
        Consumer<JLabel> style = l -> l.setForeground(theme.dim());
        style.accept(label);
        label.addPropertyChangeListener("UI", e -> style.accept(label));
        return label;
    }

    private static JTextField valueField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setBorder(new EmptyBorder(0, 0, 0, 0));
        f.setFont(MONO);
        return f;
    }

    private static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(8));
            p.add(parts[i]);
        }
        return p;
    }

    private static GridBagConstraints at(int x, int y, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 14);
        return c;
    }

    private static GridBagConstraints wide(GridBagConstraints c) {
        c.weightx = 1;
        return c;
    }

    private static void onEdit(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
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

    static Image icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(theme.accent());
        g.fill(new RoundRectangle2D.Float(size * .06f, size * .06f, size * .88f, size * .88f, size * .25f, size * .25f));
        g.setColor(Color.WHITE);
        g.setFont(UI.deriveFont(Font.BOLD, size * .38f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("/24", (size - fm.stringWidth("/24")) / 2f, (size - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
        return img;
    }

    private static final FocusListener REPAINT_ON_FOCUS = new FocusListener() {
        @Override public void focusGained(FocusEvent e) { e.getComponent().repaint(); }
        @Override public void focusLost(FocusEvent e) { e.getComponent().repaint(); }
    };

    // ---- look and feel -------------------------------------------------------------------------

    /** Basic look and feel with every colour, font and border taken from the current {@link Theme}. */
    public static final class Laf extends BasicLookAndFeel {
        @Override public String getName() { return "Subnet Calculator"; }
        @Override public String getID() { return getName(); }
        @Override public String getDescription() { return "Flat look matching the KDE colour scheme"; }
        @Override public boolean isNativeLookAndFeel() { return false; }
        @Override public boolean isSupportedLookAndFeel() { return true; }

        @Override protected void initClassDefaults(UIDefaults d) {
            super.initClassDefaults(d);
            String p = App.class.getName() + '$';
            d.put("ButtonUI", p + "FlatButtonUI");
            d.put("TextFieldUI", p + "FlatTextFieldUI");
            d.put("ScrollBarUI", p + "FlatScrollBarUI");
        }

        @Override protected void initComponentDefaults(UIDefaults d) {
            super.initComponentDefaults(d);
            Theme t = theme;
            ColorUIResource window = new ColorUIResource(t.window()), view = new ColorUIResource(t.view()),
                    fg = new ColorUIResource(t.fg()), dim = new ColorUIResource(t.dim()),
                    selection = new ColorUIResource(t.selection());
            FontUIResource font = new FontUIResource(UI);
            for (Object key : d.keySet().toArray()) if (key instanceof String s && s.endsWith(".font")) d.put(key, font);
            d.putDefaults(new Object[] {
                "control", window, "window", view, "text", fg, "controlText", fg, "textText", fg,
                "textHighlight", selection, "textHighlightText", fg, "info", view, "infoText", fg,
                "Panel.background", window, "Panel.foreground", fg,
                "Label.foreground", fg, "Label.disabledForeground", dim,
                "Button.background", window, "Button.foreground", fg,
                "TextField.background", view, "TextField.inactiveBackground", window, "TextField.foreground", fg,
                "TextField.inactiveForeground", dim, "TextField.caretForeground", fg,
                "TextField.selectionBackground", selection, "TextField.selectionForeground", fg,
                "TextField.border", new Round(),
                "Table.background", view, "Table.foreground", fg, "Table.gridColor", new ColorUIResource(t.border()),
                "Table.selectionBackground", selection, "Table.selectionForeground", fg,
                "Table.focusCellHighlightBorder", new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0),
                "TableHeader.background", view, "TableHeader.foreground", dim,
                "TableHeader.font", new FontUIResource(UI.deriveFont(Font.BOLD)),
                "TableHeader.cellBorder", new BorderUIResource(new CompoundBorder(
                        new MatteBorder(0, 0, 1, 0, t.border()), new EmptyBorder(6, 10, 6, 10))),
                "ScrollPane.background", view, "Viewport.background", view,
                "ScrollPane.border", new BorderUIResource(new LineBorder(t.border())),
                "ScrollBar.width", 10, "ScrollBar.background", view, "ScrollBar.track", view, "ScrollBar.thumb", dim,
                "ToolTip.background", view, "ToolTip.foreground", fg,
                "ToolTip.border", new BorderUIResource(new CompoundBorder(new LineBorder(t.border()), new EmptyBorder(4, 6, 4, 6))),
            });
        }
    }

    /** Rounded field outline: accent when focused, the negative colour when the input is invalid. */
    static final class Round extends AbstractBorder implements UIResource {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            boolean invalid = Boolean.TRUE.equals(((JComponent) c).getClientProperty("invalid"));
            g2.setColor(invalid ? theme.negative() : c.hasFocus() ? theme.accent() : theme.border());
            g2.draw(new RoundRectangle2D.Float(x + .5f, y + .5f, w - 1, h - 1, ARC, ARC));
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(6, 9, 6, 9);
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

    public static final class FlatButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new FlatButtonUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setBorder(new BorderUIResource.EmptyBorderUIResource(7, 16, 7, 16));
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.mix(theme.window(), theme.fg(), m.isPressed() ? .24f : m.isRollover() ? .16f : .1f));
            RoundRectangle2D shape = new RoundRectangle2D.Float(.5f, .5f, c.getWidth() - 1, c.getHeight() - 1, ARC, ARC);
            g2.fill(shape);
            g2.setColor(b.isFocusOwner() ? theme.accent() : theme.border());
            g2.draw(shape);
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

    // ---- custom components ---------------------------------------------------------------------

    /** Breeze-style on/off switch. */
    static final class Switch extends JComponent {
        private boolean on;
        private final Consumer<Boolean> action;

        Switch(boolean on, Consumer<Boolean> action) {
            this.on = on;
            this.action = action;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Switch between dark and light mode");
            addFocusListener(REPAINT_ON_FOCUS);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    toggle();
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) toggle();
                }
            });
        }

        void toggle() {
            on = !on;
            action.accept(on);
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(40, 22); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight(), d = h - 6;
            g2.setColor(on ? theme.accent() : Theme.mix(theme.window(), theme.fg(), .3f));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            if (isFocusOwner()) {
                g2.setColor(theme.fg());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(.75f, .75f, w - 1.5f, h - 1.5f, h, h));
            }
            g2.setColor(Color.WHITE);
            g2.fillOval(on ? w - d - 3 : 3, 3, d, d);
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
            FontMetrics fm = g2.getFontMetrics(getFont());
            int cw = fm.charWidth('0'), y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent(), x = 0;
            g2.setFont(getFont());
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

    /** Subnet rows are computed on demand from the row index, so even 2^20 rows cost no memory. */
    static final class SubnetModel extends AbstractTableModel {
        static final int MAX = 1 << 20;
        private static final String[] COLUMNS = {"#", "Network", "Usable range", "Broadcast", "Hosts"};
        private Subnet base;
        private int prefix, rows;

        void set(Subnet base, int prefix) {
            this.base = base;
            this.prefix = prefix;
            rows = base == null ? 0 : (int) Math.min(base.subnetCount(prefix), MAX);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows; }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override public Object getValueAt(int row, int column) {
            Subnet s = base.subnet(row, prefix);
            return switch (column) {
                case 0 -> String.format("%,d", row + 1);
                case 1 -> Subnet.format(s.network()) + "/" + prefix;
                case 2 -> Subnet.format(s.firstHost()) + " – " + Subnet.format(s.lastHost());
                case 3 -> Subnet.format(s.broadcast());
                default -> String.format("%,d", s.usable());
            };
        }
    }

    static final class Cells extends DefaultTableCellRenderer {
        private static final EmptyBorder PAD = new EmptyBorder(0, 10, 0, 10);

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, v, selected, false, row, column);
            setBorder(PAD);
            setFont(column >= 1 && column <= 3 ? MONO : UI);
            setHorizontalAlignment(column == 0 || column == 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!selected) {
                setBackground(row % 2 == 0 ? theme.view() : theme.alt());
                setForeground(column == 0 ? theme.dim() : theme.fg());
            }
            return this;
        }
    }
}
