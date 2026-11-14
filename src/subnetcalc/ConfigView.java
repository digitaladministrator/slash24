package subnetcalc;

import static subnetcalc.Ui.at;
import static subnetcalc.Ui.dim;
import static subnetcalc.Ui.status;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.SwingConstants;
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
    private final List<Section> sections = new ArrayList<>();
    private Section current;
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

    private static final String[] PORT_TYPES = {"FastEthernet", "GigabitEthernet"};

    // controls whose state decides which rows are visible
    private final Choice routing, switchKind, nat;
    private final Ui.Switch links, dhcp, ospf, uplink;
    private final List<JComponent> switchOnly = new ArrayList<>(), routerRoutingRows = new ArrayList<>(),
            uplinkPortRows = new ArrayList<>(), stickRows = new ArrayList<>(), layer2Rows = new ArrayList<>(),
            linkRows = new ArrayList<>(), dhcpRows = new ArrayList<>(), ospfRows = new ArrayList<>(),
            uplinkRows = new ArrayList<>(), poolRows = new ArrayList<>(), natRows = new ArrayList<>();
    private final Row lanPortRow, uplinkPortRow;
    private final AccessForm routerAccess, switchAccess;
    private final JTextField routerName;

    ConfigView(Preferences prefs, VlsmView vlsm, Runnable editPlan) {
        this.prefs = prefs;
        this.vlsm = vlsm;
        IosConfig.Options d = new IosConfig.Options();

        int oldDesign = prefs.getInt("ios.design", 0); // 1.3.0 had a single "LAN design" choice
        JLabel hint = dim("<html>Empty optional fields are left out of the config. \"auto\" picks a value for you.</html>");
        GridBagConstraints h = at(0, formRow++, 2);
        h.insets.bottom = 10;
        form.add(hint, h);

        section("Router", null);
        routerName = text("routerName", d.routerName, 12, v -> options.routerName = v);
        row("Hostname", routerName, null);
        row("Port type", choice("interfaceType", PORT_TYPES, 1, i -> options.routerPortType = PORT_TYPES[i]), null);
        row("Numbering", choice("numbering", new String[] {"0/0", "0/0/0"}, 0, i -> options.routerThreeLevel = i == 1), null);
        routing = choice("routing", new String[] {"Router on a stick", "Port per subnet"}, oldDesign == 1 ? 1 : 0,
                i -> options.routing = IosConfig.Routing.values()[i]);
        wideRow("Inter-VLAN routing", routing, routerRoutingRows);
        lanPortRow = row("Trunk port", tip(text("lanPort", d.lanPort, 18, v -> options.lanPort = v), "An interface name, or auto / empty for the first free port"), null);

        section("Switch", null);
        switchKind = choice("switchKind", new String[] {"None", "Layer 2", "Layer 3"}, oldDesign == 2 ? 2 : 1,
                i -> options.switchKind = IosConfig.SwitchKind.values()[i]);
        row("Switch", switchKind, null);
        row("Hostname", text("switchName", d.switchName, 12, v -> options.switchName = v), switchOnly);
        row("Port type", choice("switchPortType", PORT_TYPES, 0, i -> options.switchPortType = PORT_TYPES[i]), switchOnly);
        row("Numbering", choice("switchNumbering", new String[] {"0/1", "1/0/1"}, 0, i -> options.switchStacked = i == 1), switchOnly);
        uplinkPortRow = row("Trunk port", tip(text("uplinkPort", d.uplinkPort, 18, v -> options.uplinkPort = v), "An interface name, or auto / empty for the usual uplink port"), uplinkPortRows);
        row("First VLAN ID", number("vlanStart", d.vlanStart, "First VLAN ID", null, v -> options.vlanStart = v), switchOnly);
        row("VLAN ID step", number("vlanStep", d.vlanStep, "VLAN ID step", null, v -> options.vlanStep = v), switchOnly);
        row("Native VLAN", number("nativeVlan", 0, "Native VLAN", 0, v -> options.nativeVlan = v), stickRows);
        row("Access ports per VLAN", number("accessPorts", d.accessPorts, "Access ports per VLAN", 0,
                v -> options.accessPorts = v), switchOnly);
        row("Management VLAN", tip(text("mgmtVlan", d.mgmtVlan, 8, v -> options.mgmtVlan = v), "A VLAN ID, auto, or empty for no management interface"), layer2Rows);
        row("Management address", tip(text("mgmtAddress", d.mgmtAddress, 16, v -> options.mgmtAddress = v), "An address, auto, or empty for no management interface"), layer2Rows);

        section("Addressing", null);
        row("Gateway", choice("gateway", new String[] {"First usable", "Last usable"}, 0, i -> options.gatewayLast = i == 1), null);
        links = toggle("links", d.smallSubnetsAreLinks, "Subnets with 1 or 2 hosts become point-to-point links instead of LANs",
                v -> options.smallSubnetsAreLinks = v);
        row("1–2 host subnets are links", Ui.row(links), null);
        row("Link interface", text("linkInterface", d.linkInterface, 18, v -> options.linkInterface = v), linkRows);

        section("DHCP", null);
        dhcp = toggle("dhcp", d.dhcp, "Create a DHCP pool for every LAN, with the gateway excluded", v -> options.dhcp = v);
        row("DHCP pools", Ui.row(dhcp), null);
        row("DNS server", text("dns", d.dnsServer, 14, v -> options.dnsServer = v), dhcpRows);
        row("Domain name", text("domain", d.domainName, 14, v -> options.domainName = v), dhcpRows);
        row("Keep for static devices", number("reserved", d.reserved, "Addresses to keep for static devices", 0,
                v -> options.reserved = v), dhcpRows);

        section("Routing", null);
        ospf = toggle("ospf", d.ospf, "Advertise every subnet with OSPF", v -> options.ospf = v);
        row("OSPF", Ui.row(ospf), null);
        row("Process ID", number("ospfProcess", d.ospfProcess, "OSPF process ID", null, v -> options.ospfProcess = v), ospfRows);
        row("Area", number("ospfArea", d.ospfArea, "OSPF area", null, v -> options.ospfArea = v), ospfRows);
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
        row("Next hop", tip(text("nextHop", d.nextHop, 14, v -> options.nextHop = v), "Empty for no default route"), uplinkRows);
        nat = choice("nat", new String[] {"Off", "PAT", "Pool"}, 1, i -> options.nat = IosConfig.Nat.values()[i]);
        row("NAT", nat, uplinkRows);
        row("Pool start", text("poolStart", d.poolStart, 14, v -> options.poolStart = v), poolRows);
        row("Pool end", text("poolEnd", d.poolEnd, 14, v -> options.poolEnd = v), poolRows);
        row("Pool prefix", number("poolPrefix", d.poolPrefix, "NAT pool prefix", null, v -> options.poolPrefix = v), poolRows);
        Ui.Switch overload = toggle("poolOverload", d.poolOverload, "Share pool addresses between hosts (PAT on the pool)",
                v -> options.poolOverload = v);
        row("Overload the pool", Ui.row(overload), poolRows);
        wideRow("Static NAT: \"inside outside\" or \"tcp inside port outside port\"", area("staticNat", d.staticNat, 2,
                v -> options.staticNat = v), natRows);

        routerAccess = new AccessForm("Router security and access", "router", () -> options.routerAccess);
        switchAccess = new AccessForm("Switch security and access", "switch", () -> options.switchAccess);

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
        int kind = switchKind.selected();
        boolean hasSwitch = kind != 0, layer3 = kind == 2, stick = !layer3 && routing.selected() == 0;
        sections.forEach(Section::apply);
        showIf(routerRoutingRows, !layer3);
        lanPortRow.relabel("Router", layer3 ? "Port to switch" : stick ? "Trunk port" : "First LAN port");
        showIf(switchOnly, hasSwitch);
        showIf(uplinkPortRows, hasSwitch && (layer3 || stick));
        uplinkPortRow.relabel("Switch", layer3 ? "Routed port to router" : "Trunk port");
        showIf(stickRows, hasSwitch && stick);
        showIf(layer2Rows, kind == 1);
        routerAccess.update(true);
        switchAccess.update(hasSwitch);
        showIf(linkRows, links.isOn());
        showIf(dhcpRows, dhcp.isOn());
        showIf(ospfRows, ospf.isOn());
        showIf(uplinkRows, uplink.isOn());
        showIf(poolRows, uplink.isOn() && nat.selected() == 2);
        showIf(natRows, uplink.isOn() && nat.selected() != 0);
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

    /** A collapsible group of rows; its open/closed state is remembered. */
    private final class Section {
        private final List<JComponent> parts = new ArrayList<>();
        private final String key;
        final String title;
        private final Ui.Chevron chevron = new Ui.Chevron(true);
        boolean expanded;

        Section(String title) {
            this.title = title;
            key = "ios.section." + title;
            expanded = prefs.getBoolean(key, true);
            chevron.down = expanded;
        }

        void add(JComponent c) {
            parts.add(c);
            c.putClientProperty(Section.class, this);
        }

        void toggle() {
            expanded = !expanded;
            chevron.down = expanded;
            prefs.putBoolean(key, expanded);
            refresh();
        }

        void apply() { parts.forEach(c -> c.setVisible(expanded)); }
    }

    /** Shows {@code parts} when {@code condition} holds and their section is expanded. */
    private static void showIf(List<JComponent> parts, boolean condition) {
        for (JComponent c : parts) {
            Section s = (Section) c.getClientProperty(Section.class);
            c.setVisible(condition && (s == null || s.expanded));
        }
    }

    private void section(String title, List<JComponent> group) {
        Section section = new Section(title);
        JLabel l = new JLabel(title, section.chevron, SwingConstants.LEFT);
        l.setIconTextGap(6);
        l.setFont(Ui.SANS.deriveFont(Font.BOLD, 14f));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText("Show or hide these settings");
        l.setFocusable(true);
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                l.requestFocusInWindow();
                section.toggle();
            }
        });
        l.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) section.toggle();
            }
        });
        GridBagConstraints c = at(0, formRow++, 2);
        c.insets.top = formRow == 1 ? 0 : 14;
        c.insets.bottom = 4;
        form.add(l, c);
        if (group != null) group.add(l);
        sections.add(section);
        current = section;
    }

    /** A form row: its label, the label with its help icon ({@code box}), and the control. */
    record Row(JLabel label, JComponent box, JComponent control) {
        void addTo(List<JComponent> group) {
            group.add(box);
            group.add(control);
        }

        /** Changes the label and switches the help to match it. */
        void relabel(String section, String text) {
            if (text.equals(label.getText())) return;
            label.setText(text);
            Ui.HelpIcon help = Ui.helpIn(box);
            if (help != null) help.setEntry(Help.find(section, text));
        }
    }

    private Row row(String label, JComponent control, List<JComponent> group) {
        JLabel l = dim(label);
        JComponent box = Ui.withHelp(l, current.title);
        form.add(box, at(0, formRow, 1));
        GridBagConstraints c = Ui.wide(at(1, formRow++, 1));
        c.insets.right = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(control, c);
        current.add(box);
        current.add(control);
        Row row = new Row(l, box, control);
        if (group != null) row.addTo(group);
        return row;
    }

    /** A label on its own line with the control below, for controls too wide for two columns. */
    private void wideRow(String label, JComponent control, List<JComponent> group) {
        JComponent box = Ui.withHelp(dim(label), current.title);
        form.add(box, at(0, formRow++, 2));
        GridBagConstraints c = at(0, formRow++, 2);
        c.insets.right = 0;
        form.add(control, c);
        current.add(box);
        current.add(control);
        if (group != null) {
            group.add(box);
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

    private static <T extends JComponent> T tip(T c, String text) {
        c.setToolTipText(text);
        return c;
    }

    /** A whole-number field; when {@code blankValue} is not null an empty field means that value. */
    private JTextField number(String key, int fallback, String what, Integer blankValue, IntConsumer set) {
        JTextField f = text(key, blankValue != null && fallback == blankValue ? "" : String.valueOf(fallback), 6, v -> { });
        apply.add(() -> {
            String t = f.getText().strip();
            if (t.isEmpty() && blankValue != null) {
                set.accept(blankValue);
                return;
            }
            if (!t.matches("\\d{1,6}")) throw new IllegalArgumentException(what + " must be a whole number");
            set.accept(Integer.parseInt(t));
        });
        return f;
    }

    /** A password field. Its value is deliberately not saved: Java preferences are stored as plain text. */
    private JTextField secret(Consumer<String> set) {
        JTextField f = new JTextField(14);
        f.setFont(Ui.MONO);
        f.setToolTipText("Not saved between runs");
        apply.add(() -> set.accept(f.getText()));
        Ui.onEdit(f, this::refresh);
        return f;
    }

    /** Passwords, Telnet/SSH and line settings for one device. */
    private final class AccessForm {
        private final Choice remote;
        private final List<JComponent> all = new ArrayList<>(), vty = new ArrayList<>(), ssh = new ArrayList<>(),
                lines = new ArrayList<>();

        AccessForm(String title, String key, java.util.function.Supplier<IosConfig.Access> target) {
            IosConfig.Access d = new IosConfig.Access();
            section(title, all);
            row("Enable secret", secret(v -> target.get().enableSecret = v), all);
            row("Console password", secret(v -> target.get().consolePassword = v), all);
            remote = choice(key + ".remote", new String[] {"Off", "Telnet", "SSH", "Both"}, 0,
                    i -> target.get().remote = IosConfig.Remote.values()[i]);
            row("Remote access", remote, all);
            row("VTY password", secret(v -> target.get().vtyPassword = v), vty);
            row("SSH username", text(key + ".username", d.username, 14, v -> target.get().username = v), ssh);
            row("SSH password", secret(v -> target.get().userPassword = v), ssh);
            row("SSH domain", text(key + ".sshDomain", d.sshDomain, 14, v -> target.get().sshDomain = v), ssh);
            row("RSA key size", choice(key + ".keyBits", new String[] {"1024", "2048"}, 1,
                    i -> target.get().keyBits = i == 0 ? 1024 : 2048), ssh);
            row("VTY lines", choice(key + ".allVty", new String[] {"0–4", "0–15"}, 0, i -> target.get().allVty = i == 1), lines);
            row("Idle timeout (minutes)", number(key + ".timeout", d.timeout, "Idle timeout", -1, v -> target.get().timeout = v), all);
            row("Encrypt passwords", Ui.row(toggle(key + ".encrypt", d.encrypt, "service password-encryption",
                    v -> target.get().encrypt = v)), all);
            row("Banner (MOTD)", text(key + ".banner", d.banner, 18, v -> target.get().banner = v), all);
        }

        void update(boolean shown) {
            int r = remote.selected();
            showIf(all, shown);
            showIf(vty, shown && r == 1); // with SSH available, Telnet also signs in as the SSH user
            showIf(ssh, shown && r >= 2);
            showIf(lines, shown && r != 0);
        }
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
