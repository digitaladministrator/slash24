package subnetcalc;

import static subnetcalc.Subnet.format;
import static subnetcalc.Ui.at;
import static subnetcalc.Ui.dim;
import static subnetcalc.Ui.status;
import static subnetcalc.Ui.wide;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** The single-network calculator page: details of one address plus an even split into subnets. */
final class CalculatorView {
    private static final String[] KEYS = {
        "Network", "Broadcast", "First host", "Last host", "Usable hosts", "Total addresses",
        "Netmask", "Wildcard", "Prefix", "Class", "Type", "Hex"};

    final JPanel root = new JPanel(new BorderLayout());
    private final JTextField input = new JTextField("192.168.1.10/24");
    private final JTextField split = new JTextField("26", 3);
    private final JLabel message = new JLabel(" ");
    private final JLabel splitInfo = new JLabel(" ");
    private final JTextField[] values = new JTextField[KEYS.length];
    private final Ui.Bits addressBits = new Ui.Bits(), maskBits = new Ui.Bits();
    private final SubnetRows rows = new SubnetRows();
    private final JButton export = new JButton("Export CSV…");

    CalculatorView() {
        input.setFont(Ui.MONO.deriveFont(15f));
        split.setFont(Ui.MONO);
        JPanel grid = new JPanel(new GridBagLayout());
        int y = 0;
        grid.add(dim("IPv4 address with prefix or netmask"), at(0, y++, 4));
        grid.add(input, at(0, y++, 4));
        grid.add(message, at(0, y++, 4));
        for (int i = 0; i < KEYS.length; i++) values[i] = Ui.valueField();
        for (int r = 0; r < 6; r++, y++) {
            grid.add(dim(KEYS[r]), at(0, y, 1));
            grid.add(values[r], wide(at(1, y, 1)));
            grid.add(dim(KEYS[r + 6]), at(2, y, 1));
            grid.add(values[r + 6], wide(at(3, y, 1)));
        }
        grid.add(dim("Address bits"), at(0, y, 1));
        grid.add(addressBits, at(1, y++, 3));
        grid.add(dim("Mask bits"), at(0, y, 1));
        grid.add(maskBits, at(1, y++, 3));
        GridBagConstraints c = at(0, y++, 4);
        c.insets = new Insets(16, 0, 6, 0);
        grid.add(Ui.row(dim("Split into  /"), split, splitInfo), c);
        grid.add(Ui.table(rows, 60, 150, 270, 130, 100), Ui.fill(at(0, y, 4)));

        JButton copy = new JButton("Copy results");
        copy.addActionListener(e -> copy(copy));
        export.addActionListener(e -> Ui.exportCsv(export, rows, "subnets-" + input.getText().strip()
                .replaceAll("[^0-9A-Za-z.]+", "-") + "-split-" + rows.prefix + ".csv"));
        root.add(grid, BorderLayout.CENTER);
        root.add(Ui.footer(export, copy), BorderLayout.SOUTH);

        Ui.onEdit(input, this::refresh);
        Ui.onEdit(split, this::refresh);
        refresh();
    }

    void refresh() {
        Subnet s;
        try {
            s = Subnet.parse(input.getText());
        } catch (IllegalArgumentException e) {
            Ui.invalid(input, true);
            status(message, e.getMessage(), true);
            for (JTextField v : values) v.setText("—");
            addressBits.set(0, 0, false);
            maskBits.set(0, 0, false);
            showSplit(null, 0, " ", false);
            return;
        }
        Ui.invalid(input, false);
        String text = input.getText().strip();
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
            sp = Subnet.parsePrefix(split.getText().strip());
            if (sp < p) throw new IllegalArgumentException("Choose /" + p + " or longer to split a /" + p);
        } catch (IllegalArgumentException e) {
            Ui.invalid(split, true);
            showSplit(null, 0, e.getMessage(), true);
            return;
        }
        Ui.invalid(split, false);
        long n = s.subnetCount(sp);
        showSplit(s, sp, String.format("%,d %s × %,d usable hosts%s", n, n == 1 ? "subnet" : "subnets",
                new Subnet(0, sp).usable(),
                n > SubnetRows.MAX ? String.format(", showing and exporting the first %,d", SubnetRows.MAX) : ""), false);
    }

    private void showSplit(Subnet base, int prefix, String info, boolean error) {
        status(splitInfo, info, error);
        rows.set(base, prefix);
        export.setEnabled(base != null);
    }

    private void copy(JButton button) {
        StringBuilder sb = new StringBuilder("Input: ").append(input.getText().strip()).append('\n');
        for (int i = 0; i < KEYS.length; i++) sb.append(KEYS[i]).append(": ").append(values[i].getText()).append('\n');
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        Ui.flash(button, "Copied");
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
