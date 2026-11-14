package subnetcalc;

import static subnetcalc.Subnet.format;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a VLSM plan into Cisco IOS router and switch configurations. */
final class IosConfig {
    private IosConfig() { }

    enum Design { ROUTER_ON_A_STICK, PORT_PER_SUBNET, LAYER3_SWITCH }

    enum Nat { OFF, PAT, POOL }

    /** Every generator setting, with lab-friendly defaults. "auto" picks the next free router port. */
    static final class Options {
        String routerName = "R1", switchName = "S1";
        String interfaceType = "GigabitEthernet";
        boolean threeLevelNumbering;
        Design design = Design.ROUTER_ON_A_STICK;
        boolean gatewayLast;
        boolean smallSubnetsAreLinks = true;
        String linkInterface = "auto";
        int vlanStart = 10, vlanStep = 10, nativeVlan;
        int accessPorts = 2;
        String accessPortPrefix = "FastEthernet0/", uplinkPort = "GigabitEthernet0/1";
        boolean dhcp = true;
        String dnsServer = "8.8.8.8", domainName = "";
        int reserved;
        boolean ospf;
        int ospfProcess = 1, ospfArea;
        String routerId = "";
        boolean passive = true;
        String staticRoutes = "";
        boolean uplink;
        String uplinkInterface = "auto", uplinkAddress = "203.0.113.2/30", nextHop = "203.0.113.1";
        Nat nat = Nat.PAT;
        String poolStart = "203.0.113.10", poolEnd = "203.0.113.20";
        int poolPrefix = 24;
        boolean poolOverload = true;
        String staticNat = "";
        boolean wrap = true, extras = true, comments = true;
    }

    /** {@code switchConfig} is null when the design needs no switch configuration. */
    record Result(String router, String switchConfig) { }

    private static final Pattern HOSTNAME = Pattern.compile("[A-Za-z][A-Za-z0-9-]{0,62}");
    private static final Pattern INTERFACE = Pattern.compile("[A-Za-z][A-Za-z-]*\\d+(/\\d+)*");
    private static final Pattern PORT_PREFIX = Pattern.compile("[A-Za-z][A-Za-z-]*(\\d+/)+");
    private static final Pattern DOMAIN = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(.*?)(\\d+)$");

    static Result generate(Vlsm.Plan plan, Options o) {
        return new Generator(plan, o).run();
    }

    /** One run of the generator; holds the parsed settings and the interface allocation. */
    private static final class Generator {
        private final Vlsm.Plan plan;
        private final Options o;
        private final List<Vlsm.Allocation> lans = new ArrayList<>(), links = new ArrayList<>();
        private final List<String> lanNames = new ArrayList<>();
        private final List<Integer> vlans = new ArrayList<>();
        private Vlsm.Allocation transit;
        private String uplinkPortName;
        private int nextPort;

        Generator(Vlsm.Plan plan, Options o) {
            this.plan = plan;
            this.o = o;
        }

        Result run() {
            check(HOSTNAME.matcher(o.routerName).matches(), "Router hostname must start with a letter and use only letters, digits and hyphens");
            if (o.design != Design.PORT_PER_SUBNET) {
                check(HOSTNAME.matcher(o.switchName).matches(), "Switch hostname must start with a letter and use only letters, digits and hyphens");
            }
            for (Vlsm.Allocation a : plan.allocations()) {
                (o.smallSubnetsAreLinks && a.need().hosts() <= 2 ? links : lans).add(a);
            }
            if (o.design == Design.LAYER3_SWITCH) {
                check(!links.isEmpty(), "The layer 3 switch design needs a 2-host link in the plan (for example \"Core link 2\") "
                        + "to connect the switch to the router, with 1–2 host subnets treated as links");
                transit = links.removeFirst();
            }
            if (o.design != Design.PORT_PER_SUBNET) {
                check(!lans.isEmpty(), "The plan has no LAN subnets to put in VLANs");
                check(o.nativeVlan >= 0 && o.nativeVlan <= 4094, "Native VLAN must be between 1 and 4094, or empty");
                check(o.accessPorts >= 0 && o.accessPorts <= 48, "Access ports per VLAN must be between 0 and 48");
                check(INTERFACE.matcher(o.uplinkPort).matches(), "Switch uplink port must be an interface name like GigabitEthernet0/1");
                if (o.accessPorts > 0) {
                    check(PORT_PREFIX.matcher(o.accessPortPrefix).matches(), "Access port prefix must look like FastEthernet0/");
                }
                Set<String> names = new HashSet<>();
                for (int i = 0; i < lans.size(); i++) {
                    int vlan = o.vlanStart + i * o.vlanStep;
                    check(o.vlanStep > 0, "VLAN step must be at least 1");
                    check(vlan >= 2 && vlan <= 4094 && (vlan < 1002 || vlan > 1005),
                            "VLAN " + vlan + " is not usable: choose IDs from 2 to 1001 or 1006 to 4094");
                    check(vlan != o.nativeVlan, "VLAN " + vlan + " is also the native VLAN");
                    vlans.add(vlan);
                    lanNames.add(unique(name(lans.get(i).need().name(), "VLAN" + vlan), names));
                }
            } else {
                Set<String> names = new HashSet<>();
                for (Vlsm.Allocation a : lans) lanNames.add(unique(name(a.need().name(), "LAN"), names));
            }
            check(!o.dhcp || o.reserved >= 0, "Addresses to keep for static devices can't be negative");
            check(!o.dhcp || o.dnsServer.isBlank() || isAddress(o.dnsServer), "DNS server must be an IPv4 address, or empty");
            check(!o.dhcp || o.domainName.isBlank() || DOMAIN.matcher(o.domainName).matches(), "Domain name can only use letters, digits, dots and hyphens");
            if (o.ospf) {
                check(o.ospfProcess >= 1 && o.ospfProcess <= 65535, "OSPF process ID must be between 1 and 65535");
                check(o.ospfArea >= 0, "OSPF area can't be negative");
                check(o.routerId.isBlank() || isAddress(o.routerId), "OSPF router ID must look like an IPv4 address, or be empty");
            }

            String sw = switch (o.design) {
                case ROUTER_ON_A_STICK -> switchConfig(false);
                case LAYER3_SWITCH -> switchConfig(true);
                case PORT_PER_SUBNET -> null;
            };
            return new Result(routerConfig(), sw);
        }

        // ---- router ----------------------------------------------------------------------------

        private String routerConfig() {
            Out out = new Out(o);
            out.begin();
            out.line("hostname " + o.routerName);
            if (o.extras) out.line("no ip domain-lookup");
            boolean natOn = o.uplink && o.nat != Nat.OFF; // NAT lives on the uplink, so it is off without one
            List<String> passiveInterfaces = new ArrayList<>();
            List<Subnet> routed = new ArrayList<>();

            if (o.dhcp && o.design != Design.LAYER3_SWITCH) dhcp(out);

            out.section("Interfaces");
            switch (o.design) {
                case ROUTER_ON_A_STICK -> {
                    String trunk = ethernet(nextPort++);
                    out.line("interface " + trunk);
                    out.line(" description Trunk to " + o.switchName);
                    out.line(" no shutdown");
                    if (o.nativeVlan > 0) {
                        out.line("interface " + trunk + "." + o.nativeVlan);
                        out.line(" description Native VLAN");
                        out.line(" encapsulation dot1Q " + o.nativeVlan + " native");
                    }
                    for (int i = 0; i < lans.size(); i++) {
                        Subnet s = lans.get(i).subnet();
                        String sub = trunk + "." + vlans.get(i);
                        out.line("interface " + sub);
                        out.line(" description " + lanNames.get(i));
                        out.line(" encapsulation dot1Q " + vlans.get(i));
                        out.line(" ip address " + format(gateway(s)) + " " + format(s.mask()));
                        if (natOn) out.line(" ip nat inside");
                        passiveInterfaces.add(sub);
                        routed.add(s);
                    }
                }
                case PORT_PER_SUBNET -> {
                    for (int i = 0; i < lans.size(); i++) {
                        Subnet s = lans.get(i).subnet();
                        String port = ethernet(nextPort++);
                        out.line("interface " + port);
                        out.line(" description " + lanNames.get(i));
                        out.line(" ip address " + format(gateway(s)) + " " + format(s.mask()));
                        if (natOn) out.line(" ip nat inside");
                        out.line(" no shutdown");
                        passiveInterfaces.add(port);
                        routed.add(s);
                    }
                }
                case LAYER3_SWITCH -> {
                    Subnet s = transit.subnet();
                    out.line("interface " + ethernet(nextPort++));
                    out.line(" description Link to " + o.switchName + " (" + clean(transit.need().name()) + ")");
                    out.line(" ip address " + format(s.firstHost()) + " " + format(s.mask()));
                    if (natOn) out.line(" ip nat inside");
                    out.line(" no shutdown");
                    routed.add(s);
                }
            }
            for (int i = 0; i < links.size(); i++) {
                Vlsm.Allocation a = links.get(i);
                String port = isAuto(o.linkInterface) ? ethernet(nextPort++) : numbered(o.linkInterface, i, "Point-to-point link interface");
                out.line("interface " + port);
                out.line(" description " + clean(a.need().name()));
                out.line(" ip address " + format(a.subnet().firstHost()) + " " + format(a.subnet().mask()));
                if (natOn) out.line(" ip nat inside");
                out.line(" no shutdown");
                routed.add(a.subnet());
            }
            String defaultRoute = null;
            if (o.uplink) {
                String port = isAuto(o.uplinkInterface) ? ethernet(nextPort++) : numbered(o.uplinkInterface, 0, "Uplink interface");
                boolean viaDhcp = o.uplinkAddress.strip().equalsIgnoreCase("dhcp");
                out.line("interface " + port);
                out.line(" description Internet uplink");
                if (viaDhcp) {
                    out.line(" ip address dhcp");
                } else {
                    Subnet up = parseWithPrefix(o.uplinkAddress, "Uplink address");
                    check(up.prefix() <= 31, "Uplink address needs a prefix of /31 or shorter");
                    out.line(" ip address " + format(up.address()) + " " + format(up.mask()));
                }
                if (natOn) out.line(" ip nat outside");
                out.line(" no shutdown");
                if (!o.nextHop.isBlank()) {
                    check(isAddress(o.nextHop), "Next hop must be an IPv4 address");
                    defaultRoute = "ip route 0.0.0.0 0.0.0.0 " + o.nextHop.strip();
                } else {
                    check(viaDhcp, "Enter the next-hop address for the default route (or use dhcp on the uplink)");
                    defaultRoute = "";
                }
                uplinkPortName = port;
            }

            out.section("Routing");
            if (o.ospf) {
                out.line("router ospf " + o.ospfProcess);
                if (!o.routerId.isBlank()) out.line(" router-id " + o.routerId.strip());
                for (Subnet s : routed) out.line(" network " + format(s.network()) + " " + format(s.wildcard()) + " area " + o.ospfArea);
                if (o.passive) for (String p : passiveInterfaces) out.line(" passive-interface " + p);
                if (defaultRoute != null) out.line(" default-information originate");
            } else if (o.design == Design.LAYER3_SWITCH) {
                for (Vlsm.Allocation a : lans) {
                    out.line("ip route " + format(a.subnet().network()) + " " + format(a.subnet().mask()) + " "
                            + format(transit.subnet().lastHost()));
                }
            }
            if (defaultRoute != null && !defaultRoute.isEmpty()) out.line(defaultRoute);
            else if (defaultRoute != null) out.comment("The default route is learned from DHCP on the uplink");
            staticRoutes(out);

            if (natOn) nat(out);
            out.end();
            return out.toString();
        }

        private void nat(Out out) {
            out.section("NAT");
            Subnet inside = plan.network();
            out.line("access-list 1 permit " + format(inside.network()) + " " + format(inside.wildcard()));
            if (o.nat == Nat.PAT) {
                out.line("ip nat inside source list 1 interface " + uplinkPortName + " overload");
            } else {
                check(isAddress(o.poolStart) && isAddress(o.poolEnd), "NAT pool start and end must be IPv4 addresses");
                check(o.poolPrefix >= 8 && o.poolPrefix <= 32, "NAT pool prefix must be between 8 and 32");
                int a = Subnet.parseAddress(o.poolStart.strip()), b = Subnet.parseAddress(o.poolEnd.strip());
                check(Integer.compareUnsigned(a, b) <= 0, "The NAT pool must not end before it starts");
                check((a & Subnet.mask(o.poolPrefix)) == (b & Subnet.mask(o.poolPrefix)), "The NAT pool start and end must be in the same /" + o.poolPrefix);
                out.line("ip nat pool PUBLIC " + format(a) + " " + format(b) + " netmask " + format(Subnet.mask(o.poolPrefix)));
                out.line("ip nat inside source list 1 pool PUBLIC" + (o.poolOverload ? " overload" : ""));
            }
            int line = 0;
            for (String raw : o.staticNat.split("\\R")) {
                line++;
                String s = raw.strip();
                if (s.isEmpty()) continue;
                String[] t = s.split("\\s+");
                String where = "Static NAT line " + line;
                if (t.length == 2) {
                    check(isAddress(t[0]) && isAddress(t[1]), where + " needs an inside and an outside address");
                    out.line("ip nat inside source static " + t[0] + " " + t[1]);
                } else if (t.length == 5) {
                    String proto = t[0].toLowerCase(Locale.ROOT);
                    check(proto.equals("tcp") || proto.equals("udp"), where + " must start with tcp or udp for a port forward");
                    check(isAddress(t[1]) && isAddress(t[3]), where + " needs an inside and an outside address");
                    check(isPort(t[2]) && isPort(t[4]), where + " needs ports between 1 and 65535");
                    out.line("ip nat inside source static " + proto + " " + t[1] + " " + t[2] + " " + t[3] + " " + t[4]);
                } else {
                    throw new IllegalArgumentException(where + ": use \"inside outside\" or \"tcp inside port outside port\"");
                }
            }
        }

        private void staticRoutes(Out out) {
            int line = 0;
            for (String raw : o.staticRoutes.split("\\R")) {
                line++;
                String s = raw.strip();
                if (s.isEmpty()) continue;
                int cut = s.lastIndexOf(' ');
                check(cut > 0, "Static route line " + line + ": use \"network/prefix next-hop\"");
                String hop = s.substring(cut + 1);
                check(isAddress(hop), "Static route line " + line + ": the next hop must be an IPv4 address");
                Subnet net = parseWithPrefix(s.substring(0, cut), "Static route line " + line);
                out.line("ip route " + format(net.network()) + " " + format(net.mask()) + " " + hop);
            }
        }

        // ---- switch ----------------------------------------------------------------------------

        private String switchConfig(boolean layer3) {
            Out out = new Out(o);
            out.begin();
            out.line("hostname " + o.switchName);
            if (o.extras) out.line("no ip domain-lookup");
            if (layer3) out.line("ip routing");

            out.section("VLANs");
            for (int i = 0; i < lans.size(); i++) {
                out.line("vlan " + vlans.get(i));
                out.line(" name " + lanNames.get(i));
            }
            if (o.nativeVlan > 0) {
                out.line("vlan " + o.nativeVlan);
                out.line(" name NATIVE");
            }

            if (o.accessPorts > 0) {
                out.section("Access ports");
                int port = 1;
                for (int i = 0; i < lans.size(); i++, port += o.accessPorts) {
                    int last = port + o.accessPorts - 1;
                    out.line(o.accessPorts == 1
                            ? "interface " + o.accessPortPrefix + port
                            : "interface range " + o.accessPortPrefix + port + " - " + last);
                    out.line(" description " + lanNames.get(i));
                    out.line(" switchport mode access");
                    out.line(" switchport access vlan " + vlans.get(i));
                    out.line(" no shutdown");
                }
            }

            if (layer3) {
                out.section("Routed VLAN interfaces");
                for (int i = 0; i < lans.size(); i++) {
                    Subnet s = lans.get(i).subnet();
                    out.line("interface Vlan" + vlans.get(i));
                    out.line(" description " + lanNames.get(i));
                    out.line(" ip address " + format(gateway(s)) + " " + format(s.mask()));
                    out.line(" no shutdown");
                }
                Subnet t = transit.subnet();
                out.line("interface " + o.uplinkPort);
                out.line(" description Link to " + o.routerName);
                out.line(" no switchport");
                out.line(" ip address " + format(t.lastHost()) + " " + format(t.mask()));
                out.line(" no shutdown");
                if (o.dhcp) dhcp(out);
                out.section("Routing");
                if (o.ospf) {
                    out.line("router ospf " + o.ospfProcess);
                    for (Vlsm.Allocation a : lans) {
                        out.line(" network " + format(a.subnet().network()) + " " + format(a.subnet().wildcard()) + " area " + o.ospfArea);
                    }
                    out.line(" network " + format(t.network()) + " " + format(t.wildcard()) + " area " + o.ospfArea);
                    if (o.passive) for (int v : vlans) out.line(" passive-interface Vlan" + v);
                } else {
                    out.line("ip route 0.0.0.0 0.0.0.0 " + format(t.firstHost()));
                }
            } else {
                out.section("Trunk to " + o.routerName);
                out.line("interface " + o.uplinkPort);
                out.line(" description Trunk to " + o.routerName);
                out.line(" switchport mode trunk");
                if (o.nativeVlan > 0) out.line(" switchport trunk native vlan " + o.nativeVlan);
                out.line(" no shutdown");
            }
            out.end();
            return out.toString();
        }

        // ---- shared pieces ---------------------------------------------------------------------

        private void dhcp(Out out) {
            out.section("DHCP");
            for (int i = 0; i < lans.size(); i++) {
                Subnet s = lans.get(i).subnet();
                int gw = gateway(s);
                if (s.prefix() >= 31 || o.reserved + 1 >= s.usable()) {
                    out.comment("No DHCP pool for " + lanNames.get(i) + ": no addresses left to hand out");
                    continue;
                }
                out.line("ip dhcp excluded-address " + format(gw));
                if (o.reserved > 0) {
                    int from = o.gatewayLast ? s.firstHost() : s.firstHost() + 1;
                    out.line("ip dhcp excluded-address " + format(from) + " " + format(from + o.reserved - 1));
                }
            }
            for (int i = 0; i < lans.size(); i++) {
                Subnet s = lans.get(i).subnet();
                if (s.prefix() >= 31 || o.reserved + 1 >= s.usable()) continue;
                out.line("ip dhcp pool " + lanNames.get(i));
                out.line(" network " + format(s.network()) + " " + format(s.mask()));
                out.line(" default-router " + format(gateway(s)));
                if (!o.dnsServer.isBlank()) out.line(" dns-server " + o.dnsServer.strip());
                if (!o.domainName.isBlank()) out.line(" domain-name " + o.domainName.strip());
            }
        }

        private int gateway(Subnet s) { return o.gatewayLast ? s.lastHost() : s.firstHost(); }

        private String ethernet(int n) {
            return o.interfaceType + (o.threeLevelNumbering ? "0/0/" : "0/") + n;
        }

        /** {@code base} itself for index 0, then the same name with its last number increased. */
        private static String numbered(String base, int index, String what) {
            String name = base.strip();
            check(INTERFACE.matcher(name).matches(), what + " must be an interface name like Serial0/1/0, or auto");
            if (index == 0) return name;
            Matcher m = TRAILING_NUMBER.matcher(name);
            check(m.matches(), what + " must end with a number");
            return m.group(1) + (Integer.parseInt(m.group(2)) + index);
        }
    }

    /** Config text with optional wrapping and comments. */
    private static final class Out {
        private final StringBuilder sb = new StringBuilder();
        private final Options o;

        Out(Options o) { this.o = o; }

        void begin() {
            if (o.wrap) {
                line("enable");
                line("configure terminal");
            }
        }

        void end() {
            if (o.extras) {
                section("Console");
                line("line con 0");
                line(" logging synchronous");
            }
            if (o.wrap) {
                line("end");
                line("write memory");
            }
        }

        void section(String title) {
            line("!");
            if (o.comments) line("! " + title);
        }

        void comment(String text) {
            if (o.comments) line("! " + text);
        }

        void line(String text) { sb.append(text).append('\n'); }

        @Override public String toString() { return sb.toString(); }
    }

    static boolean isAuto(String s) { return s.isBlank() || s.strip().equalsIgnoreCase("auto"); }

    /** An IOS-safe name for VLANs and DHCP pools: upper case letters, digits and underscores. */
    static String name(String raw, String fallback) {
        String n = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (n.isEmpty()) n = fallback;
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    /** Free text for descriptions, stripped of anything that could end the command line. */
    private static String clean(String s) {
        String c = s.replaceAll("[\\p{Cntrl}]", " ").strip();
        return c.length() > 200 ? c.substring(0, 200) : c;
    }

    private static String unique(String name, Set<String> used) {
        String n = name;
        for (int i = 2; !used.add(n); i++) n = name + "_" + i;
        return n;
    }

    private static Subnet parseWithPrefix(String text, String what) {
        String s = text.strip();
        check(s.indexOf('/') > 0 || s.indexOf(' ') > 0, what + " needs a prefix, e.g. 203.0.113.2/30");
        try {
            return Subnet.parse(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + ": " + e.getMessage());
        }
    }

    private static boolean isAddress(String s) {
        try {
            Subnet.parseAddress(s.strip());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isPort(String s) {
        return s.matches("\\d{1,5}") && Integer.parseInt(s) >= 1 && Integer.parseInt(s) <= 65535;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
