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
        ios();

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

    private static void ios() {
        Vlsm.Plan office = Vlsm.plan(Subnet.parse("192.168.10.0/24"),
                Vlsm.parse("Sales 60\nEngineering 28\nGuest Wi-Fi 12\nManagement 5\nWAN link 2"), false);

        IosConfig.Result r = IosConfig.generate(office, new IosConfig.Options());
        eq("ios defaults generate a switch", true, r.switchConfig() != null);
        has("ios subinterface", r.router(), "interface GigabitEthernet0/0.10\n description SALES\n encapsulation dot1Q 10\n"
                + " ip address 192.168.10.1 255.255.255.192\n");
        has("ios link on next port", r.router(), "interface GigabitEthernet0/1\n description WAN link\n ip address 192.168.10.121 255.255.255.252\n");
        has("ios dhcp exclusion", r.router(), "ip dhcp excluded-address 192.168.10.65\n");
        has("ios dhcp pool", r.router(), "ip dhcp pool GUEST_WI_FI\n network 192.168.10.96 255.255.255.240\n default-router 192.168.10.97\n");
        has("ios wrapped", r.router(), "enable\nconfigure terminal\n");
        has("ios saved", r.router(), "end\nwrite memory\n");
        missing("ios no NAT without uplink", r.router(), "ip nat");
        has("ios trunk", r.switchConfig(), "interface GigabitEthernet0/1\n description Trunk to R1\n switchport mode trunk\n");
        has("ios access range", r.switchConfig(), "interface range FastEthernet0/3 - 4\n description ENGINEERING\n switchport mode access\n"
                + " switchport access vlan 20\n");

        IosConfig.Options o = new IosConfig.Options();
        o.ospf = true;
        o.routerId = "1.1.1.1";
        o.uplink = true;
        o.reserved = 5;
        o.staticNat = "192.168.10.10 203.0.113.5\ntcp 192.168.10.20 443 203.0.113.2 443";
        o.staticRoutes = "10.99.0.0/16 192.168.10.122";
        r = IosConfig.generate(office, o);
        has("ios ospf wildcard", r.router(), " network 192.168.10.96 0.0.0.15 area 0\n");
        has("ios passive", r.router(), " passive-interface GigabitEthernet0/0.40\n");
        has("ios default originate", r.router(), " default-information originate\nip route 0.0.0.0 0.0.0.0 203.0.113.1\n");
        has("ios router id", r.router(), " router-id 1.1.1.1\n");
        has("ios static route", r.router(), "ip route 10.99.0.0 255.255.0.0 192.168.10.122\n");
        has("ios pat", r.router(), "access-list 1 permit 192.168.10.0 0.0.0.255\nip nat inside source list 1 interface GigabitEthernet0/2 overload\n");
        has("ios static nat", r.router(), "ip nat inside source static 192.168.10.10 203.0.113.5\n");
        has("ios port forward", r.router(), "ip nat inside source static tcp 192.168.10.20 443 203.0.113.2 443\n");
        has("ios reserved range", r.router(), "ip dhcp excluded-address 192.168.10.66 192.168.10.70\n");
        has("ios full pool skipped", r.router(), "! No DHCP pool for MANAGEMENT");
        missing("ios uplink not in ospf", r.router(), "network 203.0.113.0");

        o = new IosConfig.Options();
        o.uplink = true;
        o.nat = IosConfig.Nat.POOL;
        o.poolOverload = false;
        o.routerPortType = "FastEthernet";
        o.routerThreeLevel = true;
        r = IosConfig.generate(office, o);
        has("ios pool", r.router(), "ip nat pool PUBLIC 203.0.113.10 203.0.113.20 netmask 255.255.255.0\n"
                + "ip nat inside source list 1 pool PUBLIC\n");
        has("ios fa 0/0/0", r.router(), "interface FastEthernet0/0/0\n");

        o = new IosConfig.Options();
        o.switchKind = IosConfig.SwitchKind.LAYER3;
        r = IosConfig.generate(office, o);
        has("ios l3 ip routing", r.switchConfig(), "ip routing\n");
        has("ios l3 svi", r.switchConfig(), "interface Vlan10\n description SALES\n ip address 192.168.10.1 255.255.255.192\n");
        has("ios l3 routed uplink", r.switchConfig(), " no switchport\n ip address 192.168.10.122 255.255.255.252\n");
        has("ios l3 switch default", r.switchConfig(), "ip route 0.0.0.0 0.0.0.0 192.168.10.121\n");
        has("ios l3 router back route", r.router(), "ip route 192.168.10.0 255.255.255.192 192.168.10.122\n");
        has("ios l3 dhcp on switch", r.switchConfig(), "ip dhcp pool SALES\n");
        missing("ios l3 no dhcp on router", r.router(), "ip dhcp");

        o = new IosConfig.Options();
        o.routing = IosConfig.Routing.PORT_PER_SUBNET;
        o.switchKind = IosConfig.SwitchKind.NONE;
        o.gatewayLast = true;
        o.linkInterface = "Serial0/1/0";
        r = IosConfig.generate(Vlsm.plan(Subnet.parse("10.0.0.0/24"), Vlsm.parse("A 50\nB 20\nTo R2 2\nTo R3 2"), false), o);
        eq("ios per-port has no switch", null, r.switchConfig());
        has("ios last usable gateway", r.router(), "interface GigabitEthernet0/1\n description B\n ip address 10.0.0.94 255.255.255.224\n");
        has("ios serial numbering", r.router(), "interface Serial0/1/1\n description To R3\n ip address 10.0.0.101 255.255.255.252\n");

        // 1.3.1: per-device ports, inter-VLAN port choice, management SVI, security and remote access
        o = new IosConfig.Options();
        o.lanPort = "GigabitEthernet0/1";
        o.switchPortType = "GigabitEthernet";
        o.switchStacked = true;
        r = IosConfig.generate(office, o);
        has("ios chosen trunk port", r.router(), "interface GigabitEthernet0/1.10\n");
        has("ios auto skips chosen port", r.router(), "interface GigabitEthernet0/0\n description WAN link\n");
        has("ios switch ports per device", r.switchConfig(), "interface range GigabitEthernet1/0/1 - 2\n");
        has("ios stacked uplink", r.switchConfig(), "interface GigabitEthernet1/1/1\n description Trunk to R1\n");
        has("ios management svi", r.switchConfig(), "interface Vlan40\n description Management (MANAGEMENT)\n"
                + " ip address 192.168.10.114 255.255.255.248\n no shutdown\nip default-gateway 192.168.10.113\n");
        has("ios management excluded from dhcp", r.router(), "ip dhcp excluded-address 192.168.10.114\n");

        o = new IosConfig.Options();
        o.routing = IosConfig.Routing.PORT_PER_SUBNET;
        o.lanPort = "GigabitEthernet0/0/2";
        r = IosConfig.generate(office, o);
        has("ios per-port first lan port", r.router(), "interface GigabitEthernet0/0/2\n description SALES\n");
        has("ios per-port counts up", r.router(), "interface GigabitEthernet0/0/3\n description ENGINEERING\n");
        has("ios per-port l2 switch access only", r.switchConfig(), "! Connect each router LAN port to an access port in its VLAN\n");
        missing("ios per-port no trunk", r.switchConfig(), "switchport mode trunk");

        o = new IosConfig.Options();
        o.routerAccess.enableSecret = "class";
        o.routerAccess.consolePassword = "cisco";
        o.routerAccess.username = "admin";
        o.routerAccess.userPassword = "S3cret!";
        o.routerAccess.remote = IosConfig.Remote.SSH;
        o.routerAccess.timeout = 5;
        o.routerAccess.banner = "Authorized access only";
        o.switchAccess.remote = IosConfig.Remote.TELNET;
        o.switchAccess.vtyPassword = "telnetpw";
        o.switchAccess.allVty = true;
        r = IosConfig.generate(office, o);
        has("ios security top", r.router(), "service password-encryption\nenable secret class\n"
                + "username admin privilege 15 secret S3cret!\nbanner motd #Authorized access only#\n"
                + "ip domain-name lab.local\ncrypto key generate rsa general-keys modulus 2048\nip ssh version 2\n");
        has("ios console", r.router(), "line con 0\n password cisco\n login\n logging synchronous\n exec-timeout 5 0\n");
        has("ios ssh vty", r.router(), "line vty 0 4\n login local\n transport input ssh\n exec-timeout 5 0\n");
        has("ios telnet vty", r.switchConfig(), "line vty 0 15\n password telnetpw\n login\n transport input telnet\n");
        missing("ios switch no ssh keys", r.switchConfig(), "crypto key");

        String[][] access = {{"ssh-no-user"}, {"telnet-no-password"}, {"password-with-space"}, {"user-without-password"}, {"banner-hash"}};
        for (String[] b : access) {
            o = new IosConfig.Options();
            IosConfig.Access a = o.routerAccess;
            switch (b[0]) {
                case "ssh-no-user" -> a.remote = IosConfig.Remote.SSH;
                case "telnet-no-password" -> a.remote = IosConfig.Remote.TELNET;
                case "password-with-space" -> a.enableSecret = "two words";
                case "user-without-password" -> a.username = "admin";
                default -> a.banner = "no # allowed";
            }
            checks++;
            try {
                IosConfig.generate(office, o);
                fail("ios should reject " + b[0]);
            } catch (IllegalArgumentException expected) {
                // rejected with a message for the user
            }
        }
        o = new IosConfig.Options();
        o.switchKind = IosConfig.SwitchKind.NONE;
        checks++;
        try {
            IosConfig.generate(office, o);
            fail("ios should reject router on a stick without a switch");
        } catch (IllegalArgumentException expected) {
            // a trunk needs a switch
        }
        o = new IosConfig.Options();
        o.lanPort = "GigabitEthernet0/1";
        o.linkInterface = "GigabitEthernet0/1";
        checks++;
        try {
            IosConfig.generate(office, o);
            fail("ios should reject a port used twice");
        } catch (IllegalArgumentException expected) {
            // two roles on one port
        }

        eq("ios name", "GUEST_WI_FI", IosConfig.name("Guest Wi-Fi!", "X"));
        String[][] bad = {
            {"routerName", "R 1"}, {"uplinkAddress", "203.0.113.2"}, {"staticNat", "tcp 1.2.3.4 99999 5.6.7.8 80"},
            {"vlanStart", "1002"}, {"staticRoutes", "10.0.0.0/8 nowhere"}, {"design", "LAYER3_NO_LINK"}};
        for (String[] b : bad) {
            o = new IosConfig.Options();
            o.uplink = true;
            Vlsm.Plan plan = office;
            switch (b[0]) {
                case "routerName" -> o.routerName = b[1];
                case "uplinkAddress" -> o.uplinkAddress = b[1];
                case "staticNat" -> o.staticNat = b[1];
                case "vlanStart" -> o.vlanStart = Integer.parseInt(b[1]);
                case "staticRoutes" -> o.staticRoutes = b[1];
                default -> {
                    o.switchKind = IosConfig.SwitchKind.LAYER3;
                    plan = Vlsm.plan(Subnet.parse("10.0.0.0/24"), Vlsm.parse("A 50"), false);
                }
            }
            checks++;
            try {
                IosConfig.generate(plan, o);
                fail("ios should reject " + b[0] + " = " + b[1]);
            } catch (IllegalArgumentException expected) {
                // rejected with a message for the user
            }
        }
    }

    private static void has(String name, String text, String part) {
        checks++;
        if (!text.contains(part)) fail(name + ": missing\n" + part + "\nin\n" + text);
    }

    private static void missing(String name, String text, String part) {
        checks++;
        if (text.contains(part)) fail(name + ": should not contain " + part);
    }

    private static String block(Subnet s) { return format(s.network()) + "/" + s.prefix(); }

    private static String blocks(java.util.List<Subnet> list) {
        return list.stream().map(s -> format(s.network()) + "/" + s.prefix()).toList().toString();
    }

    private static void eq(String name, Object want, Object got) {
        checks++;
        if (!java.util.Objects.equals(want, got)) fail(name + ": expected " + want + " but got " + got);
    }

    private static void fail(String message) {
        failures++;
        System.err.println("FAIL " + message);
    }
}
