package slash24;

import static slash24.Subnet.format;
import static slash24.Ui.at;
import static slash24.Ui.dim;
import static slash24.Ui.status;
import static slash24.Ui.wide;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

/** The single-network calculator page: details of one address, a containment check and an even split. */
final class CalculatorView implements Page {
    private static final String[] KEYS = {
        "Network", "Broadcast", "First host", "Last host", "Usable hosts", "Total addresses",
        "Netmask", "Wildcard", "Prefix", "Class", "Type", "Hex"};

    private final JPanel root = new JPanel(new BorderLayout());
    private final JTextField input = new JTextField("192.168.1.10/24");
    private final JTextField split = new JTextField("26", 3);
    private final JTextField contains = new JTextField(15);
    private final JLabel message = new JLabel(" "), splitInfo = new JLabel(" "), containsInfo = new JLabel(" ");
    private final Ui.Problem inputProblem = new Ui.Problem(input, message);
    private final Ui.Problem splitProblem = new Ui.Problem(split, splitInfo);
    private final Ui.Problem containsProblem = new Ui.Problem(contains, containsInfo);
    private final JTextField[] values = new JTextField[KEYS.length];
    private final Ui.Bits addressBits = new Ui.Bits(), maskBits = new Ui.Bits();
    private final SubnetRows rows = new SubnetRows();
    private final JTable table;
    private final History history;
    private final JButton export = new JButton("Export CSV…");
    private final JButton copyTable = new JButton("Copy table");
    private final JButton copyResults = Ui.primary(new JButton("Copy results"));

    CalculatorView(Preferences prefs) {
        history = new History(prefs, "history.calculator");
        input.setFont(Ui.MONO.deriveFont(15f));
        input.setToolTipText("Ctrl+L to jump here, Down arrow for recent networks");
        split.setFont(Ui.MONO);
        contains.setFont(Ui.MONO);
        contains.setToolTipText("An address to locate in this network");

        JPanel grid = new JPanel(new GridBagLayout());
        int y = 0;
        grid.add(Ui.withHelp(dim("IPv4 address with prefix or netmask"), ""), at(0, y++, 4));
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(history.button(input), BorderLayout.EAST);
        grid.add(inputRow, at(0, y++, 4));
        grid.add(message, at(0, y++, 4));
        for (int i = 0; i < KEYS.length; i++) values[i] = Ui.valueField();
        // the results sit together on one card, set apart from the inputs around them
        JPanel results = new JPanel(new GridBagLayout());
        int ry = 0;
        for (int r = 0; r < 6; r++, ry++) {
            results.add(dim(KEYS[r]), at(0, ry, 1));
            results.add(values[r], wide(at(1, ry, 1)));
            results.add(dim(KEYS[r + 6]), at(2, ry, 1));
            results.add(values[r + 6], wide(at(3, ry, 1)));
        }
        GridBagConstraints b = at(0, ry, 1);
        b.insets.top = 8;
        results.add(dim("Address bits"), b);
        b = at(1, ry++, 3);
        b.insets.top = 8;
        results.add(addressBits, b);
        results.add(dim("Mask bits"), at(0, ry, 1));
        results.add(maskBits, at(1, ry, 3));
        GridBagConstraints card = at(0, y++, 4);
        card.insets.right = 0;
        card.insets.top = 6;
        grid.add(Ui.card(results), card);
        GridBagConstraints c = at(0, y, 1);
        c.insets.top = 10;
        grid.add(Ui.withHelp(dim("Contains?"), ""), c);
        c = at(1, y++, 3);
        c.insets.top = 10;
        grid.add(Ui.row(contains, containsInfo), c);
        c = at(0, y++, 4);
        c.insets = new Insets(14, 0, 6, 0);
        grid.add(Ui.row(Ui.withHelp(dim("Split into  /"), ""), split, splitInfo), c);
        JScrollPane scroll = Ui.table(rows, 60, 150, 270, 130, 100);
        table = Ui.tableIn(scroll);
        grid.add(scroll, Ui.fill(at(0, y, 4)));

        copyResults.setToolTipText("Copy the details above as text (Ctrl+Shift+C)");
        copyResults.addActionListener(e -> copy());
        copyTable.setToolTipText("Copy the subnet table, ready to paste into a spreadsheet");
        copyTable.addActionListener(e -> Ui.copyTable(copyTable, rows));
        export.addActionListener(e -> Ui.exportCsv(export, rows, "subnets-" + input.getText().strip()
                .replaceAll("[^0-9A-Za-z.]+", "-") + "-split-" + rows.prefix + ".csv"));
        root.add(grid, BorderLayout.CENTER);
        root.add(Ui.footer(export, copyTable, copyResults), BorderLayout.SOUTH);

        Ui.onEdit(input, this::refresh);
        Ui.onEdit(split, this::refresh);
        Ui.onEdit(contains, this::refresh);
        refresh();
    }

    @Override public JComponent root() { return root; }

    @Override public void focusInput() {
        input.requestFocusInWindow();
        input.selectAll();
    }

    @Override public void refresh() {
        Subnet s;
        try {
            s = Subnet.parse(input.getText());
        } catch (IllegalArgumentException e) {
            inputProblem.report(e.getMessage());
            history.cancel();
            for (JTextField v : values) v.setText("—");
            addressBits.set(0, 0, false);
            maskBits.set(0, 0, false);
            splitProblem.clear();
            containsProblem.clear();
            status(splitInfo, " ", false);
            status(containsInfo, " ", false);
            showTable(null, 0);
            return;
        }
        inputProblem.clear();
        history.offer(input.getText());
        String text = Subnet.clean(input.getText());
        boolean classful = text.indexOf('/') < 0 && text.chars().noneMatch(Character::isWhitespace);
        status(message, classful ? "No prefix given, so the classful default /" + s.prefix() + " is used" : " ", false);

        int p = s.prefix();
        String[] v = {
            format(s.network()) + "/" + p, format(s.broadcast()), format(s.firstHost()), format(s.lastHost()),
            String.format("%,d", s.usable()), String.format("%,d", s.total()),
            format(s.mask()), format(s.wildcard()), "/" + p,
            String.valueOf(s.addressClass()), s.type(), String.format("0x%08X", s.address())};
        for (int i = 0; i < v.length; i++) {
            values[i].setText(v[i]);
            values[i].setCaretPosition(0);
        }
        addressBits.set(s.address(), p, true);
        maskBits.set(s.mask(), p, true);

        int sp;
        try {
            sp = Subnet.parsePrefix(Subnet.clean(split.getText()));
            if (sp < p) throw new IllegalArgumentException("Choose /" + p + " or longer to split a /" + p);
            splitProblem.clear();
            long n = s.subnetCount(sp);
            status(splitInfo, String.format("%,d %s × %,d usable hosts%s", n, n == 1 ? "subnet" : "subnets",
                    new Subnet(0, sp).usable(),
                    n > SubnetRows.MAX ? String.format(", showing and exporting the first %,d", SubnetRows.MAX) : ""), false);
            showTable(s, sp);
        } catch (IllegalArgumentException e) {
            splitProblem.report(e.getMessage());
            sp = -1;
            showTable(null, 0);
        }
        locate(s, sp);
    }

    private void showTable(Subnet base, int prefix) {
        rows.set(base, prefix);
        export.setEnabled(base != null);
        copyTable.setEnabled(base != null);
    }

    /** The "Contains?" check: is the address inside, and where, including which split subnet it lands in. */
    private void locate(Subnet s, int splitPrefix) {
        String text = Subnet.clean(contains.getText());
        if (text.isEmpty()) {
            containsProblem.clear();
            status(containsInfo, "Enter an address to see where it falls", false);
            return;
        }
        int ip;
        try {
            ip = Subnet.parseAddress(text);
        } catch (IllegalArgumentException e) {
            containsProblem.report(e.getMessage());
            return;
        }
        containsProblem.clear();
        String net = format(s.network()) + "/" + s.prefix();
        if ((ip & s.mask()) != s.network()) {
            status(containsInfo, "No, it is outside " + net, false);
            return;
        }
        long offset = (ip - s.network()) & 0xFFFFFFFFL;
        StringBuilder sb = new StringBuilder("Yes: ").append(position(s, offset)).append(" in ").append(net);
        if (splitPrefix > s.prefix()) {
            long index = offset >>> (32 - splitPrefix);
            Subnet sub = s.subnet(index, splitPrefix);
            sb.append(String.format(". Split subnet #%,d (%s/%d), %s", index + 1, format(sub.network()), splitPrefix,
                    position(sub, offset - (index << (32 - splitPrefix)))));
            if (index < rows.getRowCount()) {
                table.setRowSelectionInterval((int) index, (int) index);
                table.scrollRectToVisible(table.getCellRect((int) index, 0, true));
            }
        }
        containsInfo.setText(sb.toString());
        containsInfo.setForeground(Ui.theme.positive());
    }

    /** Where {@code offset} sits inside {@code s}, in words. */
    static String position(Subnet s, long offset) {
        if (s.prefix() >= 31) return String.format("address %,d of %,d", offset + 1, s.total());
        if (offset == 0) return "the network address";
        if (offset == s.total() - 1) return "the broadcast address";
        return String.format("host %,d of %,d", offset, s.usable());
    }

    @Override public void copy() {
        StringBuilder sb = new StringBuilder("Input: ").append(input.getText().strip()).append('\n');
        for (int i = 0; i < KEYS.length; i++) sb.append(KEYS[i]).append(": ").append(values[i].getText()).append('\n');
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        Ui.flash(copyResults, "Copied");
    }

    /** Equal-size subnets of the current network, computed from the row index on demand. */
    static final class SubnetRows extends Ui.Rows {
        static final int MAX = 1 << 20;
        private Subnet base;
        private int prefix, count;

        SubnetRows() { super("#", "Network", "Usable range", "Broadcast", "Hosts"); }

        void set(Subnet base, int prefix) {
            this.base = base;
            this.prefix = prefix;
            count = base == null ? 0 : (int) Math.min(base.subnetCount(prefix), MAX);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return count; }
        @Override boolean mono(int c) { return c >= 1 && c <= 3; }
        @Override boolean numeric(int c) { return c == 0 || c == 4; }
        @Override boolean secondary(int c) { return c == 0; }

        @Override public Object getValueAt(int row, int column) {
            Subnet s = base.subnet(row, prefix);
            return switch (column) {
                case 0 -> String.format("%,d", row + 1);
                case 1 -> format(s.network()) + "/" + prefix;
                case 2 -> format(s.firstHost()) + " – " + format(s.lastHost());
                case 3 -> format(s.broadcast());
                default -> String.format("%,d", s.usable());
            };
        }

        @Override String[] csvHeader() {
            return new String[] {"Subnet", "Network", "Prefix", "Netmask", "First host", "Last host", "Broadcast", "Usable hosts"};
        }

        @Override String[] csvRow(int row) {
            Subnet s = base.subnet(row, prefix);
            return new String[] {String.valueOf(row + 1), format(s.network()), String.valueOf(prefix), format(s.mask()),
                format(s.firstHost()), format(s.lastHost()), format(s.broadcast()), String.valueOf(s.usable())};
        }

        @Override Ui.Rows snapshot() {
            SubnetRows copy = new SubnetRows();
            copy.set(base, prefix);
            return copy;
        }
    }
}
