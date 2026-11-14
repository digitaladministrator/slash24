package subnetcalc;

import static subnetcalc.Subnet.format;

/** Dependency-free checks of the subnet arithmetic and parsing; run by build.sh. */
public final class SubnetTest {
    private static int checks, failures;

    public static void main(String[] args) {
        Subnet s = Subnet.parse("192.168.1.10/24");
        eq("network", "192.168.1.0", format(s.network()));
        eq("broadcast", "192.168.1.255", format(s.broadcast()));
        eq("first host", "192.168.1.1", format(s.firstHost()));
        eq("last host", "192.168.1.254", format(s.lastHost()));
        eq("usable", 254L, s.usable());
        eq("total", 256L, s.total());
        eq("netmask", "255.255.255.0", format(s.mask()));
        eq("wildcard", "0.0.0.255", format(s.wildcard()));
        eq("class", 'C', s.addressClass());
        eq("type", "Private (RFC 1918)", s.type());
        eq("hex", "0xC0A8010A", String.format("0x%08X", s.address()));

        s = Subnet.parse("10.20.30.40 255.255.240.0");
        eq("dotted mask prefix", 20, s.prefix());
        eq("dotted mask network", "10.20.16.0", format(s.network()));
        eq("dotted mask broadcast", "10.20.31.255", format(s.broadcast()));
        eq("slash dotted mask", 20, Subnet.parse("10.20.30.40/255.255.240.0").prefix());
        eq("space prefix", 27, Subnet.parse("  10.0.0.1   27 ").prefix());

        s = Subnet.parse("203.0.113.8/31");
        eq("/31 usable", 2L, s.usable());
        eq("/31 first", "203.0.113.8", format(s.firstHost()));
        eq("/31 last", "203.0.113.9", format(s.lastHost()));
        eq("/32 usable", 1L, Subnet.parse("8.8.8.8/32").usable());

        s = Subnet.parse("0.0.0.0/0");
        eq("/0 total", 4_294_967_296L, s.total());
        eq("/0 usable", 4_294_967_294L, s.usable());
        eq("/0 broadcast", "255.255.255.255", format(s.broadcast()));
        eq("/0 last /32", "255.255.255.255", format(s.subnet(s.subnetCount(32) - 1, 32).network()));

        eq("classful A", 8, Subnet.parse("10.1.2.3").prefix());
        eq("classful B", 16, Subnet.parse("172.16.5.4").prefix());
        eq("classful C", 24, Subnet.parse("192.0.2.1").prefix());

        s = Subnet.parse("10.9.8.7/8");
        eq("split count", 256L, s.subnetCount(16));
        eq("split 3", "10.3.0.0", format(s.subnet(3, 16).network()));
        eq("split last", "10.255.255.255", format(s.subnet(255, 16).broadcast()));

        eq("public", "Public", Subnet.parse("8.8.8.8/24").type());
        eq("cgnat", "Shared / CGNAT (RFC 6598)", Subnet.parse("100.64.0.1/10").type());
        eq("loopback", "Loopback", Subnet.parse("127.0.0.1/8").type());
        eq("multicast", 'D', Subnet.parse("224.0.0.5/32").addressClass());
        eq("prefix with slash", 26, Subnet.parsePrefix("/26"));

        for (String bad : new String[] {"", "256.1.1.1/24", "1.2.3/24", "1.2.3.4/33", "1.2.3.4 255.0.255.0",
                "1.2.3.4.5", "a.b.c.d", "1..2.3", "1.2.3.4/", "1.2.3.4/-1", "1.2.3.4/024"}) {
            checks++;
            try {
                Subnet.parse(bad);
                fail("should reject \"" + bad + "\"");
            } catch (IllegalArgumentException expected) {
                // rejected as it should be
            }
        }

        vlsm();
        csv();
        cidr();
        keyBindings();

        if (failures > 0) {
            System.err.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("All " + checks + " subnet checks passed");
    }

    private static void vlsm() {
        String office = "Sales 60\nEngineering 28\n\nGuest Wi-Fi: 12\nManagement 5\nWAN link 2\n";
        Vlsm.Plan plan = Vlsm.plan(Subnet.parse("192.168.10.0/24"), Vlsm.parse(office), false);
        eq("vlsm count", 5, plan.allocations().size());
        String[] want = {"192.168.10.0/26", "192.168.10.64/27", "192.168.10.96/28", "192.168.10.112/29", "192.168.10.120/30"};
        for (int i = 0; i < want.length; i++) {
            Subnet s = plan.allocations().get(i).subnet();
            eq("vlsm block " + i, want[i], format(s.network()) + "/" + s.prefix());
        }
        eq("vlsm name trimmed", "Guest Wi-Fi", plan.allocations().get(2).need().name());
        eq("vlsm spare", 2L, plan.allocations().get(0).spare());
        eq("vlsm used", 124L, plan.used());
        eq("vlsm free", "[192.168.10.124/30, 192.168.10.128/25]", blocks(plan.free()));

        plan = Vlsm.plan(Subnet.parse("192.168.10.0/24"), Vlsm.parse(office), true);
        eq("vlsm /31 link", 31, plan.allocations().get(4).subnet().prefix());
        eq("vlsm /31 free", "[192.168.10.122/31, 192.168.10.124/30, 192.168.10.128/25]", blocks(plan.free()));

        eq("vlsm unnamed", "Subnet 2", Vlsm.parse("10, 20; 30").get(1).name());
        eq("vlsm sorts largest first", "B", Vlsm.plan(Subnet.parse("10.0.0.0/24"), Vlsm.parse("A 10\nB 100"), false)
                .allocations().get(0).need().name());
        eq("vlsm prefix 1 host", 30, Vlsm.prefixFor(1, false));
        eq("vlsm prefix 1 host p2p", 32, Vlsm.prefixFor(1, true));
        eq("vlsm prefix 254", 24, Vlsm.prefixFor(254, false));
        eq("vlsm prefix 255", 23, Vlsm.prefixFor(255, false));
        eq("vlsm whole space", 0, Vlsm.prefixFor(Vlsm.MAX_HOSTS, false));
        eq("vlsm full fit", "[]", blocks(Vlsm.plan(Subnet.parse("10.0.0.0/24"), Vlsm.parse("126\n126"), false).free()));

        for (String bad : new String[] {"", "Sales", "Sales 0", "x 99999999999"}) {
            checks++;
            try {
                Vlsm.parse(bad);
                fail("vlsm should reject \"" + bad + "\"");
            } catch (IllegalArgumentException expected) {
                // rejected as it should be
            }
        }
        checks++;
        try {
            Vlsm.plan(Subnet.parse("10.0.0.0/24"), Vlsm.parse("200\n100"), false);
            fail("vlsm should report a plan that does not fit");
        } catch (IllegalArgumentException expected) {
            // 256 + 128 addresses cannot fit in 256
        }
    }

    private static void csv() {
        eq("csv plain", "a,b,c\r\n", Csv.line("a", "b", "c"));
        eq("csv quoting", "\"Lab, east\",\"say \"\"hi\"\"\",2\r\n", Csv.line("Lab, east", "say \"hi\"", "2"));
    }

    private static void cidr() {
        Cidr.Summary s = Cidr.summarize(Cidr.parseList("192.168.0.0/24\n192.168.1.0/24, 192.168.2.0/24; 192.168.3.0/24"));
        eq("summary route", "192.168.0.0/22", block(s.route()));
        eq("summary exact", "[192.168.0.0/22]", blocks(s.exact()));
        eq("summary covered", 1024L, s.covered());

        s = Cidr.summarize(Cidr.parseList("10.0.0.0/24\n10.0.3.0/24"));
        eq("gap route", "10.0.0.0/22", block(s.route()));
        eq("gap exact", "[10.0.0.0/24, 10.0.3.0/24]", blocks(s.exact()));
        eq("gap covered", 512L, s.covered());

        s = Cidr.summarize(Cidr.parseList("10.0.5.0/24\n10.0.0.0/16\n10.0.9.9/32"));
        eq("overlap route", "10.0.0.0/16", block(s.route()));
        eq("overlap exact", "[10.0.0.0/16]", blocks(s.exact()));

        s = Cidr.summarize(Cidr.parseList("8.8.8.8"));
        eq("bare address is /32", "8.8.8.8/32", block(s.route()));
        s = Cidr.summarize(Cidr.parseList("10.1.2.3/24, 10.1.3.200/24"));
        eq("host bits ignored", "[10.1.2.0/23]", blocks(s.exact()));
        eq("far apart", "0.0.0.0/0", block(Cidr.summarize(Cidr.parseList("1.0.0.0/8, 200.0.0.0/8")).route()));

        eq("range", "[10.0.0.5/32, 10.0.0.6/31, 10.0.0.8/29, 10.0.0.16/28, 10.0.0.32/27, 10.0.0.64/29, 10.0.0.72/30, 10.0.0.76/31]",
                blocks(Cidr.range(0x0A000005L, 0x0A00004EL)));
        eq("range whole space", "[0.0.0.0/0]", blocks(Cidr.range(0, 1L << 32)));
        eq("range single", "[255.255.255.255/32]", blocks(Cidr.range(0xFFFFFFFFL, 1L << 32)));

        checks++;
        try {
            Cidr.parseList("10.0.0.0/24\n10.0.0.300/24");
            fail("summary should reject a bad entry");
        } catch (IllegalArgumentException e) {
            eq("summary error names entry", true, e.getMessage().startsWith("Entry 2:"));
        }

        Subnet net = Subnet.parse("192.168.1.0/24");
        eq("position host", "host 10 of 254", CalculatorView.position(net, 10));
        eq("position network", "the network address", CalculatorView.position(net, 0));
        eq("position broadcast", "the broadcast address", CalculatorView.position(net, 255));
        eq("position /31", "address 2 of 2", CalculatorView.position(Subnet.parse("10.0.0.0/31"), 1));
    }

    /** Regression for 1.2.1: the custom look and feel once shipped with no editing keys at all. */
    private static void keyBindings() {
        Ui.theme = Theme.of(false);
        Ui.installLookAndFeel();
        String[][] expected = {
            {"BACK_SPACE", "delete-previous"}, {"DELETE", "delete-next"}, {"LEFT", "caret-backward"},
            {"HOME", "caret-begin-line"}, {"ctrl C", "copy-to-clipboard"}, {"ctrl V", "paste-from-clipboard"},
            {"ctrl X", "cut-to-clipboard"}, {"ctrl A", "select-all"}};
        for (javax.swing.text.JTextComponent c : new javax.swing.text.JTextComponent[] {
                new javax.swing.JTextField(), new javax.swing.JTextArea()}) {
            javax.swing.InputMap keys = c.getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
            for (String[] e : expected) {
                eq(c.getClass().getSimpleName() + " " + e[0], e[1], String.valueOf(keys.get(javax.swing.KeyStroke.getKeyStroke(e[0]))));
            }
        }
        eq("table arrow keys", "selectNextRow", String.valueOf(new javax.swing.JTable()
                .getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(javax.swing.KeyStroke.getKeyStroke("DOWN"))));
    }

    private static String block(Subnet s) { return format(s.network()) + "/" + s.prefix(); }

    private static String blocks(java.util.List<Subnet> list) {
        return list.stream().map(s -> format(s.network()) + "/" + s.prefix()).toList().toString();
    }

    private static void eq(String name, Object want, Object got) {
        checks++;
        if (!want.equals(got)) fail(name + ": expected " + want + " but got " + got);
    }

    private static void fail(String message) {
        failures++;
        System.err.println("FAIL " + message);
    }
}
