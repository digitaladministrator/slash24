package subnetcalc;

import static subnetcalc.Ui.at;
import static subnetcalc.Ui.dim;
import static subnetcalc.Ui.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.prefs.Preferences;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

/** Cisco IOS config generator page, driven by the plan on the VLSM planner page. */
final class ConfigView implements Page {
    private final JPanel root = new JPanel(new BorderLayout());
    private final Preferences prefs;
    private final VlsmView vlsm;
    private final IosConfig.Options options = new IosConfig.Options();
    /** Copies every control's value into {@link #options}; may throw for malformed numbers. */
    private final List<Runnable> apply = new ArrayList<>();
    private final JPanel form = new JPanel(new GridBagLayout());
    private int formRow;

    private final JLabel planInfo = new JLabel(" ");
    private final JLabel info = new JLabel(" ");
    private final JTextArea output = new JTextArea();
    private final JScrollPane outputBox = Ui.roundedScroll(output);
    private final Ui.Problem problem = new Ui.Problem(outputBox, info);
    private final JButton save = new JButton("Save…");
    private final JButton copy = new JButton("Copy config");
    private final Choice device;
    private IosConfig.Result result;

    // controls whose state decides which rows are visible
    private final Choice design, nat;
    private final Ui.Switch links, dhcp, ospf, uplink;
    private final List<JComponent> switchOnly = new ArrayList<>(), linkRows = new ArrayList<>(), dhcpRows = new ArrayList<>(),
            ospfRows = new ArrayList<>(), uplinkRows = new ArrayList<>(), poolRows = new ArrayList<>(), natRows = new ArrayList<>();
    private final JTextField routerName;

    ConfigView(Preferences prefs, VlsmView vlsm, Runnable editPlan) {
        this.prefs = prefs;
        this.vlsm = vlsm;
        IosConfig.Options d = new IosConfig.Options();

        section("Devices", null);
        routerName = text("routerName", d.routerName, 12, v -> options.routerName = v);
        row("Router hostname", routerName, null);
        row("Switch hostname", text("switchName", d.switchName, 12, v -> options.switchName = v), switchOnly);

        section("Interfaces", null);
        row("Port type", choice("interfaceType", new String[] {"FastEthernet", "GigabitEthernet"}, 1,
                i -> options.interfaceType = i == 0 ? "FastEthernet" : "GigabitEthernet"), null);
        row("Numbering", choice("numbering", new String[] {"0/0", "0/0/0"}, 0, i -> options.threeLevelNumbering = i == 1), null);
        design = choice("design", new String[] {"Router on a stick", "Port per subnet", "Layer 3 switch"}, 0,
                i -> options.design = IosConfig.Design.values()[i]);
        wideRow("LAN design", design, null);

        section("Addressing", null);
        row("Gateway", choice("gateway", new String[] {"First usable", "Last usable"}, 0, i -> options.gatewayLast = i == 1), null);
        links = toggle("links", d.smallSubnetsAreLinks, "Subnets with 1 or 2 hosts become point-to-point links instead of LANs",
                v -> options.smallSubnetsAreLinks = v);
        row("1–2 host subnets are links", Ui.row(links), null);
        row("Link interface", text("linkInterface", d.linkInterface, 18, v -> options.linkInterface = v), linkRows);

        section("VLANs and switch", switchOnly);
        row("First VLAN ID", number("vlanStart", d.vlanStart, "First VLAN ID", false, v -> options.vlanStart = v), switchOnly);
        row("VLAN ID step", number("vlanStep", d.vlanStep, "VLAN ID step", false, v -> options.vlanStep = v), switchOnly);
        row("Native VLAN", number("nativeVlan", 0, "Native VLAN", true, v -> options.nativeVlan = v), switchOnly);
        row("Access ports per VLAN", number("accessPorts", d.accessPorts, "Access ports per VLAN", false, v -> options.accessPorts = v), switchOnly);
        row("Access port prefix", text("accessPortPrefix", d.accessPortPrefix, 18, v -> options.accessPortPrefix = v), switchOnly);
        row("Switch uplink port", text("uplinkPort", d.uplinkPort, 18, v -> options.uplinkPort = v), switchOnly);

        section("DHCP", null);
        dhcp = toggle("dhcp", d.dhcp, "Create a DHCP pool for every LAN, with the gateway excluded", v -> options.dhcp = v);
        row("DHCP pools", Ui.row(dhcp), null);
        row("DNS server", text("dns", d.dnsServer, 14, v -> options.dnsServer = v), dhcpRows);
        row("Domain name", text("domain", d.domainName, 14, v -> options.domainName = v), dhcpRows);
        row("Keep for static devices", number("reserved", d.reserved, "Addresses to keep for static devices", true,
                v -> options.reserved = v), dhcpRows);

        section("Routing", null);
        ospf = toggle("ospf", d.ospf, "Advertise every subnet with OSPF", v -> options.ospf = v);
        row("OSPF", Ui.row(ospf), null);
        row("Process ID", number("ospfProcess", d.ospfProcess, "OSPF process ID", false, v -> options.ospfProcess = v), ospfRows);
        row("Area", number("ospfArea", d.ospfArea, "OSPF area", false, v -> options.ospfArea = v), ospfRows);
        row("Router ID", text("routerId", d.routerId, 14, v -> options.routerId = v), ospfRows);
        Ui.Switch passive = toggle("passive", d.passive, "Don't send OSPF hellos out of LAN interfaces", v -> options.passive = v);
        row("Passive LAN interfaces", Ui.row(passive), ospfRows);
        wideRow("Static routes: network/prefix next-hop, one per line", area("staticRoutes", d.staticRoutes, 2,
                v -> options.staticRoutes = v), null);

        section("Internet uplink", null);
        uplink = toggle("uplink", d.uplink, "Add an Internet-facing interface with a default route", v -> options.uplink = v);
        row("Uplink", Ui.row(uplink), null);
        row("Interface", text("uplinkInterface", d.uplinkInterface, 18, v -> options.uplinkInterface = v), uplinkRows);
        row("Address", text("uplinkAddress", d.uplinkAddress, 18, v -> options.uplinkAddress = v), uplinkRows);
        row("Next hop", text("nextHop", d.nextHop, 14, v -> options.nextHop = v), uplinkRows);
        nat = choice("nat", new String[] {"Off", "PAT", "Pool"}, 1, i -> options.nat = IosConfig.Nat.values()[i]);
        row("NAT", nat, uplinkRows);
        row("Pool start", text("poolStart", d.poolStart, 14, v -> options.poolStart = v), poolRows);
        row("Pool end", text("poolEnd", d.poolEnd, 14, v -> options.poolEnd = v), poolRows);
        row("Pool prefix", number("poolPrefix", d.poolPrefix, "NAT pool prefix", false, v -> options.poolPrefix = v), poolRows);
        Ui.Switch overload = toggle("poolOverload", d.poolOverload, "Share pool addresses between hosts (PAT on the pool)",
                v -> options.poolOverload = v);
        row("Overload the pool", Ui.row(overload), poolRows);
        wideRow("Static NAT: \"inside outside\" or \"tcp inside port outside port\"", area("staticNat", d.staticNat, 2,
                v -> options.staticNat = v), natRows);

        section("Output", null);
        row("enable … write memory", Ui.row(toggle("wrap", d.wrap, "Start with enable / configure terminal and end with write memory",
                v -> options.wrap = v)), null);
        row("Lab extras", Ui.row(toggle("extras", d.extras, "no ip domain-lookup and logging synchronous", v -> options.extras = v)), null);
        row("Comments", Ui.row(toggle("comments", d.comments, "Label each section with ! comments", v -> options.comments = v)), null);

        GridBagConstraints end = at(0, formRow, 2);
        end.weighty = 1;
        form.add(Box.createVerticalGlue(), end);
        form.setBorder(new EmptyBorder(0, 0, 0, 14));
        JScrollPane formScroll = new JScrollPane(form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override public void updateUI() {
                super.updateUI();
                setBorder(new EmptyBorder(0, 0, 0, 0));
                getVerticalScrollBar().setUnitIncrement(16);
            }
        };
        formScroll.setPreferredSize(new Dimension(470, 0));

        output.setEditable(false);
        output.setFont(Ui.MONO);
        device = new Choice(new String[] {"Router", "Switch"}, 0, i -> show());
        JButton edit = new JButton("Edit plan");
        edit.setToolTipText("Open the VLSM planner (Ctrl+2)");
        edit.addActionListener(e -> editPlan.run());
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(planInfo, BorderLayout.CENTER);
        top.add(edit, BorderLayout.EAST);
        JPanel right = new JPanel(new GridBagLayout());
        right.add(top, Ui.wide(at(0, 0, 1)));
        GridBagConstraints c = at(0, 1, 1);
        c.insets.top = 10;
        right.add(Ui.row(device, info), c);
        right.add(outputBox, Ui.fill(at(0, 2, 1)));
        right.setBorder(new EmptyBorder(0, 14, 0, 0));

        copy.setToolTipText("Copy this device's config (Ctrl+Shift+C)");
        copy.addActionListener(e -> copy());
        save.addActionListener(e -> {
            String text = output.getText(), name = device.selected() == 1 ? options.switchName : options.routerName;
            Ui.saveAs(save, name + ".txt", "txt", "Text files", path -> Files.writeString(path, text));
        });
        JPanel body = new JPanel(new BorderLayout());
        body.add(formScroll, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        root.add(Ui.footer(save, copy), BorderLayout.SOUTH);

        vlsm.onPlanChange(this::refresh);
        refresh();
    }

    @Override public JComponent root() { return root; }

    @Override public void focusInput() {
        routerName.requestFocusInWindow();
        routerName.selectAll();
    }

    @Override public void copy() {
        if (!copy.isEnabled()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(output.getText()), null);
        Ui.flash(copy, "Copied");
    }

    @Override public void refresh() {
        boolean perPort = design.selected() == 1;
        switchOnly.forEach(c -> c.setVisible(!perPort));
        linkRows.forEach(c -> c.setVisible(links.isOn()));
        dhcpRows.forEach(c -> c.setVisible(dhcp.isOn()));
        ospfRows.forEach(c -> c.setVisible(ospf.isOn()));
        uplinkRows.forEach(c -> c.setVisible(uplink.isOn()));
        poolRows.forEach(c -> c.setVisible(uplink.isOn() && nat.selected() == 2));
        natRows.forEach(c -> c.setVisible(uplink.isOn() && nat.selected() != 0));
        form.revalidate();
        form.repaint();

        Vlsm.Plan plan = vlsm.plan();
        if (plan == null) {
            status(planInfo, "The VLSM planner has a problem to fix first", true);
            result = null;
            show();
            return;
        }
        int n = plan.allocations().size();
        status(planInfo, String.format("Plan: %d %s in %s/%d, from the VLSM planner", n, n == 1 ? "subnet" : "subnets",
                Subnet.format(plan.network().network()), plan.network().prefix()), false);
        try {
            apply.forEach(Runnable::run);
            result = IosConfig.generate(plan, options);
            problem.clear();
        } catch (IllegalArgumentException e) {
            problem.report(e.getMessage());
            setEnabled(false);
            return;
        }
        status(info, result.switchConfig() == null ? "No switch config is needed for this design" : " ", false);
        show();
    }

    /** Shows the selected device's config; keeps the scroll position when the text is unchanged. */
    private void show() {
        boolean hasSwitch = result != null && result.switchConfig() != null;
        device.setEnabled(1, hasSwitch);
        if (!hasSwitch && device.selected() == 1) device.select(0);
        String text = result == null ? "" : device.selected() == 1 ? result.switchConfig() : result.router();
        if (!text.equals(output.getText())) {
            output.setText(text);
            output.setCaretPosition(0);
        }
        setEnabled(result != null);
    }

    private void setEnabled(boolean enabled) {
        copy.setEnabled(enabled);
        save.setEnabled(enabled);
    }

    // ---- form building -------------------------------------------------------------------------

    private void section(String title, List<JComponent> group) {
        JLabel l = new JLabel(title);
        l.setFont(Ui.SANS.deriveFont(Font.BOLD, 14f));
        GridBagConstraints c = at(0, formRow++, 2);
        c.insets.top = formRow == 1 ? 0 : 16;
        c.insets.bottom = 4;
        form.add(l, c);
        if (group != null) group.add(l);
    }

    private void row(String label, JComponent control, List<JComponent> group) {
        JLabel l = dim(label);
        form.add(l, at(0, formRow, 1));
        GridBagConstraints c = Ui.wide(at(1, formRow++, 1));
        c.insets.right = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(control, c);
        if (group != null) {
            group.add(l);
            group.add(control);
        }
    }

    /** A label on its own line with the control below, for controls too wide for two columns. */
    private void wideRow(String label, JComponent control, List<JComponent> group) {
        JLabel l = dim(label);
        form.add(l, at(0, formRow++, 2));
        GridBagConstraints c = at(0, formRow++, 2);
        c.insets.right = 0;
        form.add(control, c);
        if (group != null) {
            group.add(l);
            group.add(control);
        }
    }

    private JTextField text(String key, String fallback, int columns, Consumer<String> set) {
        JTextField f = new JTextField(prefs.get("ios." + key, fallback), columns);
        f.setFont(Ui.MONO);
        apply.add(() -> set.accept(f.getText().strip()));
        Ui.onEdit(f, () -> {
            prefs.put("ios." + key, f.getText());
            refresh();
        });
        return f;
    }

    private JTextField number(String key, int fallback, String what, boolean blankIsZero, IntConsumer set) {
        JTextField f = text(key, blankIsZero && fallback == 0 ? "" : String.valueOf(fallback), 6, v -> { });
        apply.add(() -> {
            String t = f.getText().strip();
            if (t.isEmpty() && blankIsZero) {
                set.accept(0);
                return;
            }
            if (!t.matches("\\d{1,6}")) throw new IllegalArgumentException(what + " must be a whole number");
            set.accept(Integer.parseInt(t));
        });
        return f;
    }

    private JScrollPane area(String key, String fallback, int rows, Consumer<String> set) {
        JTextArea a = new JTextArea(prefs.get("ios." + key, fallback), rows, 30);
        a.setFont(Ui.MONO);
        apply.add(() -> set.accept(a.getText()));
        Ui.onEdit(a, () -> {
            prefs.put("ios." + key, a.getText());
            refresh();
        });
        JScrollPane box = Ui.roundedScroll(a);
        box.setMinimumSize(box.getPreferredSize());
        return box;
    }

    private Ui.Switch toggle(String key, boolean fallback, String tip, Consumer<Boolean> set) {
        Ui.Switch s = new Ui.Switch(prefs.getBoolean("ios." + key, fallback), tip, on -> {
            prefs.putBoolean("ios." + key, on);
            refresh();
        });
        apply.add(() -> set.accept(s.isOn()));
        return s;
    }

    private Choice choice(String key, String[] labels, int fallback, IntConsumer set) {
        int saved = prefs.getInt("ios." + key, fallback);
        Choice c = new Choice(labels, saved >= 0 && saved < labels.length ? saved : fallback, i -> {
            prefs.putInt("ios." + key, i);
            refresh();
        });
        apply.add(() -> set.accept(c.selected()));
        return c;
    }

    /** A row of compact toggle buttons where exactly one is selected. */
    static final class Choice extends JPanel {
        private final JToggleButton[] buttons;
        private int selected;

        Choice(String[] labels, int selected, IntConsumer onChange) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            this.selected = selected;
            buttons = new JToggleButton[labels.length];
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < labels.length; i++) {
                int index = i;
                JToggleButton b = new JToggleButton(labels[i]);
                b.putClientProperty("compact", true);
                b.updateUI();
                b.setSelected(i == selected);
                b.addActionListener(e -> {
                    this.selected = index;
                    onChange.accept(index);
                });
                group.add(b);
                if (i > 0) add(Box.createHorizontalStrut(6));
                add(b);
                buttons[i] = b;
            }
        }

        int selected() { return selected; }

        void select(int index) {
            selected = index;
            buttons[index].setSelected(true);
        }

        void setEnabled(int index, boolean enabled) { buttons[index].setEnabled(enabled); }
    }
}
