package subnetcalc;

import static subnetcalc.Subnet.format;
import static subnetcalc.Ui.at;
import static subnetcalc.Ui.dim;
import static subnetcalc.Ui.status;
import static subnetcalc.Ui.wide;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** The VLSM planner page: fit named host requirements into one network, largest first. */
final class VlsmView implements Page {
    private final JPanel root = new JPanel(new BorderLayout());
    private final JTextField network = new JTextField("192.168.10.0/24");
    private final JTextArea needs = new JTextArea("Sales 60\nEngineering 28\nGuest Wi-Fi 12\nManagement 5\nWAN link 2\n");
    private final JScrollPane needsBox = Ui.roundedScroll(needs);
    private final Ui.Switch pointToPoint = new Ui.Switch(false,
            "Give 1-host needs a /32 and 2-host needs a /31 (RFC 3021) instead of a /30", on -> refresh());
    private final JLabel summary = new JLabel(" ");
    private final Ui.Problem networkProblem = new Ui.Problem(network, summary);
    private final Ui.Problem needsProblem = new Ui.Problem(needsBox, summary);
    private final PlanRows rows = new PlanRows();
    private final History history;
    private final JButton export = new JButton("Export CSV…");
    private final JButton copyTable = new JButton("Copy table");
    private final List<Runnable> planListeners = new ArrayList<>();
    private Vlsm.Plan plan;

    VlsmView(Preferences prefs) {
        history = new History(prefs, "history.vlsm");
        network.setFont(Ui.MONO.deriveFont(15f));
        network.setToolTipText("Ctrl+L to jump here, Down arrow for recent networks");
        needs.setFont(Ui.MONO);
        needs.setRows(6);
        JPanel grid = new JPanel(new GridBagLayout());
        int y = 0;
        grid.add(Ui.withHelp(dim("Network to divide"), ""), at(0, y++, 2));
        JPanel networkRow = new JPanel(new BorderLayout(8, 0));
        networkRow.add(network, BorderLayout.CENTER);
        networkRow.add(history.button(network), BorderLayout.EAST);
        grid.add(networkRow, wide(at(0, y, 1)));
        GridBagConstraints c = at(1, y++, 1);
        c.insets.right = 0;
        grid.add(Ui.row(Ui.withHelp(pointToPoint.label("Use /31 and /32 links"), ""), pointToPoint), c);
        c = at(0, y++, 2);
        c.insets.top = 12;
        grid.add(Ui.withHelp(dim("Subnets needed: a name and host count on each line, e.g. \"Sales 60\""), ""), c);
        needsBox.setMinimumSize(needsBox.getPreferredSize());
        grid.add(needsBox, at(0, y++, 2));
        grid.add(summary, at(0, y++, 2));
        grid.add(Ui.table(rows, 130, 70, 165, 275, 135, 70, 60), Ui.fill(at(0, y, 2)));

        export.addActionListener(e -> Ui.exportCsv(export, rows, "vlsm-" + network.getText().strip()
                .replaceAll("[^0-9A-Za-z.]+", "-") + ".csv"));
        copyTable.setToolTipText("Copy the plan, ready to paste into a spreadsheet (Ctrl+Shift+C)");
        copyTable.addActionListener(e -> copy());
        root.add(grid, BorderLayout.CENTER);
        root.add(Ui.footer(export, copyTable), BorderLayout.SOUTH);

        Ui.onEdit(network, this::refresh);
        Ui.onEdit(needs, this::refresh);
        refresh();
    }

    @Override public JComponent root() { return root; }

    @Override public void focusInput() {
        network.requestFocusInWindow();
        network.selectAll();
    }

    @Override public void copy() {
        if (rows.getRowCount() > 0) Ui.copyTable(copyTable, rows);
    }

    @Override public void refresh() {
        Subnet net;
        try {
            net = Subnet.parse(network.getText());
            networkProblem.clear();
        } catch (IllegalArgumentException e) {
            history.cancel();
            needsProblem.clear();
            networkProblem.report(e.getMessage());
            show(null);
            return;
        }
        history.offer(network.getText());
        Vlsm.Plan plan;
        try {
            plan = Vlsm.plan(net, Vlsm.parse(needs.getText()), pointToPoint.isOn());
            needsProblem.clear();
        } catch (IllegalArgumentException e) {
            needsProblem.report(e.getMessage());
            show(null);
            return;
        }
        List<Subnet> free = plan.free();
        String freeText = free.isEmpty() ? "none" : free.stream().limit(3)
                .map(s -> format(s.network()) + "/" + s.prefix()).collect(Collectors.joining(", "))
                + (free.size() > 3 ? " and " + (free.size() - 3) + " more" : "");
        int n = plan.allocations().size();
        status(summary, String.format("%d %s use %,d of %,d addresses (%.0f%%).  Free: %s", n, n == 1 ? "subnet" : "subnets",
                plan.used(), net.total(), 100.0 * plan.used() / net.total(), freeText), false);
        show(plan);
    }

    /** The current valid plan, or null while the inputs have a problem. */
    Vlsm.Plan plan() { return plan; }

    void onPlanChange(Runnable listener) { planListeners.add(listener); }

    private void show(Vlsm.Plan plan) {
        this.plan = plan;
        planListeners.forEach(Runnable::run);
        rows.set(plan);
        export.setEnabled(plan != null);
        copyTable.setEnabled(plan != null);
    }

    static final class PlanRows extends Ui.Rows {
        private Vlsm.Plan plan;

        PlanRows() { super("Name", "Needed", "Subnet", "Usable range", "Broadcast", "Usable", "Spare"); }

        void set(Vlsm.Plan plan) {
            this.plan = plan;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return plan == null ? 0 : plan.allocations().size(); }
        @Override boolean mono(int c) { return c >= 2 && c <= 4; }
        @Override boolean numeric(int c) { return c == 1 || c >= 5; }
        @Override boolean secondary(int c) { return c == 6; }

        @Override public Object getValueAt(int row, int column) {
            Vlsm.Allocation a = plan.allocations().get(row);
            Subnet s = a.subnet();
            return switch (column) {
                case 0 -> a.need().name();
                case 1 -> String.format("%,d", a.need().hosts());
                case 2 -> format(s.network()) + "/" + s.prefix();
                case 3 -> format(s.firstHost()) + " – " + format(s.lastHost());
                case 4 -> format(s.broadcast());
                case 5 -> String.format("%,d", s.usable());
                default -> String.format("%,d", a.spare());
            };
        }

        @Override String[] csvHeader() {
            return new String[] {"Name", "Hosts needed", "Network", "Prefix", "Netmask", "First host", "Last host",
                "Broadcast", "Usable hosts", "Spare hosts"};
        }

        @Override String[] csvRow(int row) {
            Vlsm.Allocation a = plan.allocations().get(row);
            Subnet s = a.subnet();
            return new String[] {a.need().name(), String.valueOf(a.need().hosts()), format(s.network()),
                String.valueOf(s.prefix()), format(s.mask()), format(s.firstHost()), format(s.lastHost()),
                format(s.broadcast()), String.valueOf(s.usable()), String.valueOf(a.spare())};
        }

        @Override Ui.Rows snapshot() {
            PlanRows copy = new PlanRows();
            copy.plan = plan; // plans are immutable
            return copy;
        }
    }
}
