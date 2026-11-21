package slash24;

import static slash24.Subnet.format;
import static slash24.Ui.at;
import static slash24.Ui.dim;
import static slash24.Ui.status;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

/** Route tools: summarize a list of networks, or turn an address range into CIDR blocks. */
final class ToolsView implements Page {
    private final JPanel root = new JPanel(new BorderLayout());
    private final JTextArea networks = new JTextArea("192.168.0.0/24\n192.168.1.0/24\n192.168.2.0/24\n192.168.3.0/24\n");
    private final JScrollPane networksBox = Ui.roundedScroll(networks);
    private final JTextField from = new JTextField("10.0.0.5", 14), to = new JTextField("10.0.0.77", 14);
    private final JTextField route = Ui.valueField();
    private final JPanel routeRow;
    private final JLabel info = new JLabel(" ");
    private final Ui.Problem networksProblem = new Ui.Problem(networksBox, info);
    private final Ui.Problem fromProblem = new Ui.Problem(from, info), toProblem = new Ui.Problem(to, info);
    private final BlockRows rows = new BlockRows();
    private final JButton export = new JButton("Export CSV…");
    private final JButton copyTable = new JButton("Copy table");
    private final CardLayout cards = new CardLayout();
    private final JPanel inputs = new JPanel(cards);
    private final Preferences prefs;
    private boolean rangeMode;

    ToolsView(Preferences prefs) {
        this.prefs = prefs;
        rangeMode = prefs.getBoolean("tools.range", false);
        networks.setFont(Ui.MONO);
        networks.setRows(5);
        from.setFont(Ui.MONO);
        to.setFont(Ui.MONO);
        route.setFont(Ui.MONO.deriveFont(15f));

        ButtonGroup modes = new ButtonGroup();
        JToggleButton summarizeTab = new JToggleButton("Summarize networks"), rangeTab = new JToggleButton("Range to CIDR");
        modes.add(summarizeTab);
        modes.add(rangeTab);
        (rangeMode ? rangeTab : summarizeTab).setSelected(true);
        summarizeTab.addActionListener(e -> setRangeMode(false));
        rangeTab.addActionListener(e -> setRangeMode(true));

        JPanel summarize = new JPanel(new BorderLayout(0, 6));
        summarize.add(Ui.withHelp(dim("Networks to summarize: one per line, or separated by commas"), ""), BorderLayout.NORTH);
        networksBox.setMinimumSize(networksBox.getPreferredSize());
        summarize.add(networksBox, BorderLayout.CENTER);
        JPanel range = new JPanel(new BorderLayout(0, 6));
        range.add(Ui.withHelp(dim("First and last address of the range"), ""), BorderLayout.NORTH);
        range.add(Ui.row(from, dim("to"), to), BorderLayout.CENTER);
        inputs.add(summarize, "summarize");
        inputs.add(range, "range");
        routeRow = Ui.row(dim("Summary route"), route);

        JPanel grid = new JPanel(new GridBagLayout());
        int y = 0;
        grid.add(Ui.row(summarizeTab, rangeTab), at(0, y++, 1));
        GridBagConstraints c = at(0, y++, 1);
        c.insets.top = 12;
        grid.add(inputs, c);
        c = at(0, y++, 1);
        c.insets.top = 10;
        grid.add(routeRow, c);
        grid.add(info, at(0, y++, 1));
        grid.add(Ui.table(rows, 60, 170, 160, 160, 120), Ui.fill(at(0, y, 1)));

        export.addActionListener(e -> Ui.exportCsv(export, rows, rangeMode ? "range-blocks.csv" : "summary-blocks.csv"));
        copyTable.setToolTipText("Copy the blocks, ready to paste into a spreadsheet (Ctrl+Shift+C)");
        copyTable.addActionListener(e -> copy());
        root.add(grid, BorderLayout.CENTER);
        root.add(Ui.footer(export, copyTable), BorderLayout.SOUTH);

        Ui.onEdit(networks, this::refresh);
        Ui.onEdit(from, this::refresh);
        Ui.onEdit(to, this::refresh);
        setRangeMode(rangeMode);
    }

    private void setRangeMode(boolean range) {
        rangeMode = range;
        prefs.putBoolean("tools.range", range);
        cards.show(inputs, range ? "range" : "summarize");
        routeRow.setVisible(!range);
        refresh();
    }

    @Override public JComponent root() { return root; }

    @Override public void focusInput() {
        if (rangeMode) {
            from.requestFocusInWindow();
            from.selectAll();
        } else {
            networks.requestFocusInWindow();
            networks.selectAll();
        }
    }

    @Override public void copy() {
        if (rows.getRowCount() > 0) Ui.copyTable(copyTable, rows);
    }

    @Override public void refresh() {
        if (rangeMode) refreshRange();
        else refreshSummary();
    }

    private void refreshSummary() {
        fromProblem.clear();
        toProblem.clear();
        Cidr.Summary s;
        try {
            s = Cidr.summarize(Cidr.parseList(networks.getText()));
            networksProblem.clear();
        } catch (IllegalArgumentException e) {
            networksProblem.report(e.getMessage());
            route.setText("—");
            show(null);
            return;
        }
        route.setText(format(s.route().network()) + "/" + s.route().prefix());
        long extra = s.route().total() - s.covered();
        int n = s.exact().size();
        status(info, String.format("The summary route covers %,d addresses%s. Below: the exact %s covering only your list.",
                s.route().total(), extra == 0 ? ", all of them from your list" : String.format(", %,d of them not in your list", extra),
                n == 1 ? "block" : n + " blocks"), false);
        show(s.exact());
    }

    private void refreshRange() {
        networksProblem.clear();
        long a, b;
        try {
            a = Subnet.parseAddress(Subnet.clean(from.getText())) & 0xFFFFFFFFL;
            fromProblem.clear();
        } catch (IllegalArgumentException e) {
            toProblem.clear();
            fromProblem.report(e.getMessage());
            show(null);
            return;
        }
        try {
            b = Subnet.parseAddress(Subnet.clean(to.getText())) & 0xFFFFFFFFL;
            if (b < a) throw new IllegalArgumentException("The last address can't come before the first");
            toProblem.clear();
        } catch (IllegalArgumentException e) {
            toProblem.report(e.getMessage());
            show(null);
            return;
        }
        List<Subnet> blocks = Cidr.range(a, b + 1);
        status(info, String.format("%,d addresses in %d CIDR %s", b - a + 1, blocks.size(),
                blocks.size() == 1 ? "block" : "blocks"), false);
        show(blocks);
    }

    private void show(List<Subnet> blocks) {
        rows.set(blocks);
        export.setEnabled(blocks != null);
        copyTable.setEnabled(blocks != null);
    }

    static final class BlockRows extends Ui.Rows {
        private List<Subnet> blocks = List.of();

        BlockRows() { super("#", "Block", "First address", "Last address", "Addresses"); }

        void set(List<Subnet> blocks) {
            this.blocks = blocks == null ? List.of() : blocks;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return blocks.size(); }
        @Override boolean mono(int c) { return c >= 1 && c <= 3; }
        @Override boolean numeric(int c) { return c == 0 || c == 4; }
        @Override boolean secondary(int c) { return c == 0; }

        @Override public Object getValueAt(int row, int column) {
            Subnet s = blocks.get(row);
            return switch (column) {
                case 0 -> String.format("%,d", row + 1);
                case 1 -> format(s.network()) + "/" + s.prefix();
                case 2 -> format(s.network());
                case 3 -> format(s.broadcast());
                default -> String.format("%,d", s.total());
            };
        }

        @Override String[] csvHeader() {
            return new String[] {"Block", "Network", "Prefix", "Netmask", "First address", "Last address", "Addresses"};
        }

        @Override String[] csvRow(int row) {
            Subnet s = blocks.get(row);
            return new String[] {String.valueOf(row + 1), format(s.network()), String.valueOf(s.prefix()), format(s.mask()),
                format(s.network()), format(s.broadcast()), String.valueOf(s.total())};
        }

        @Override Ui.Rows snapshot() {
            BlockRows copy = new BlockRows();
            copy.blocks = blocks; // immutable lists
            return copy;
        }
    }
}
