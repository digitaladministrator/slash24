package slash24;

import static slash24.Subnet.format;

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

    /** What the switch is: absent, a plain access switch, or one that routes between VLANs itself. */
    enum SwitchKind { NONE, LAYER2, LAYER3 }

    /** How the router routes between VLANs when the switch doesn't. */
    enum Routing { ROUTER_ON_A_STICK, PORT_PER_SUBNET }

    enum Nat { OFF, PAT, POOL }

    enum Remote { OFF, TELNET, SSH, BOTH }

    /** Login, password and remote-access settings for one device. Empty strings mean "leave unset". */
    static final class Access {
        String enableSecret = "", consolePassword = "", username = "", userPassword = "", vtyPassword = "";
        Remote remote = Remote.OFF;
        String sshDomain = "lab.local";
        int keyBits = 2048;
        boolean allVty;
        /** Minutes of idle time before a session is closed; 0 = never, -1 = leave the default. */
        int timeout = -1;
        boolean encrypt = true;
        String banner = "";
    }

    /** Every generator setting, with lab-friendly defaults. "auto" picks the next free port. */
    static final class Options {
        // router
        String routerName = "R1";
        String routerPortType = "GigabitEthernet";
        boolean routerThreeLevel;
        Routing routing = Routing.ROUTER_ON_A_STICK;
        String lanPort = "auto";
        // switch
        SwitchKind switchKind = SwitchKind.LAYER2;
        String switchName = "S1";
        String switchPortType = "FastEthernet";
        boolean switchStacked;
        String uplinkPort = "auto";
        int vlanStart = 10, vlanStep = 10, nativeVlan;
        int accessPorts = 2;
        String mgmtVlan = "auto", mgmtAddress = "auto";
        // addressing
        boolean gatewayLast;
        boolean smallSubnetsAreLinks = true;
        String linkInterface = "auto";
        // services
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
        // security
        Access routerAccess = new Access(), switchAccess = new Access();
        // output
        boolean wrap = true, extras = true, comments = true;
    }

    /** {@code switchConfig} is null when there is no switch to configure. */
    record Result(String router, String switchConfig) { }

    /** The most access ports one switch has (a 48-port model, or one member of a stack). */
    static final int MAX_SWITCH_PORTS = 48;
    private static final Pattern HOSTNAME = Pattern.compile("[A-Za-z][A-Za-z0-9-]{0,62}");
    /** Up to 4 digits per number, so increasing the last one for later ports can't overflow. */
    private static final Pattern INTERFACE = Pattern.compile("[A-Za-z][A-Za-z-]*\\d{1,4}(/\\d{1,4})*");
    private static final Pattern DOMAIN = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(.*?)(\\d+)$");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.@-]{1,32}");
    /** No spaces (they split the command) and no '?' (it triggers CLI help when pasted). */
    private static final Pattern PASSWORD = Pattern.compile("[^\\s?]{1,64}");

    static Result generate(Vlsm.Plan plan, Options o) {
        return new Generator(plan, o).run();
    }

    /** One run of the generator; holds the parsed settings and the port allocation. */
    private static final class Generator {
        private final Vlsm.Plan plan;
        private final Options o;
        private final boolean layer3, stick, hasSwitch;
        private final List<Vlsm.Allocation> lans = new ArrayList<>(), links = new ArrayList<>();
        private final List<String> lanNames = new ArrayList<>();
        private final List<Integer> vlans = new ArrayList<>();
        private final List<String> lanPorts = new ArrayList<>(), linkPorts = new ArrayList<>();
        private final Set<String> usedPorts = new HashSet<>();
        private Vlsm.Allocation transit;
        private String uplinkName, switchUplink;
        private int mgmtIndex = -1, mgmtIp;
        private String routerLabel, switchLabel;
        private int nextPort;

        Generator(Vlsm.Plan plan, Options o) {
            this.plan = plan;
            this.o = o;
            layer3 = o.switchKind == SwitchKind.LAYER3;
            hasSwitch = o.switchKind != SwitchKind.NONE;
            stick = !layer3 && o.routing == Routing.ROUTER_ON_A_STICK;
        }

        Result run() {
            check(o.routerName.isBlank() || HOSTNAME.matcher(o.routerName.strip()).matches(),
                    "Router hostname must start with a letter and use only letters, digits and hyphens, or be empty");
            if (hasSwitch) {
                check(o.switchName.isBlank() || HOSTNAME.matcher(o.switchName.strip()).matches(),
                        "Switch hostname must start with a letter and use only letters, digits and hyphens, or be empty");
            }
            routerLabel = o.routerName.isBlank() ? "router" : o.routerName.strip();
            switchLabel = o.switchName.isBlank() ? "switch" : o.switchName.strip();
            check(hasSwitch || !stick, "Router on a stick needs a switch to trunk to: choose a switch, or a port per subnet");
            for (Vlsm.Allocation a : plan.allocations()) {
                (o.smallSubnetsAreLinks && a.need().hosts() <= 2 ? links : lans).add(a);
            }
            if (layer3) {
                // a /32 has a single address, so it can't number both ends of the router–switch link
                transit = links.stream().filter(a -> a.subnet().usable() >= 2).findFirst().orElse(null);
                check(transit != null, "A layer 3 switch needs a 2-host link in the plan (for example \"Core link 2\") "
                        + "to connect it to the router, with 1–2 host subnets treated as links");
                links.remove(transit);
            }
            names();
            if (hasSwitch) switchSettings();
            services();
            access(o.routerAccess, "Router", o.routerName);
            if (hasSwitch) access(o.switchAccess, "Switch", o.switchName);
            ports();
            return new Result(routerConfig(), hasSwitch ? switchConfig() : null);
        }

        private void names() {
            Set<String> used = new HashSet<>();
            if (!hasSwitch) {
                for (Vlsm.Allocation a : lans) lanNames.add(unique(name(a.need().name(), "LAN"), used));
                return;
            }
            check(!lans.isEmpty(), "The plan has no LAN subnets to put in VLANs");
            check(o.vlanStep > 0, "VLAN ID step must be at least 1");
            check(o.nativeVlan >= 0 && o.nativeVlan <= 4094 && (o.nativeVlan < 1002 || o.nativeVlan > 1005),
                    "Native VLAN must be from 1 to 1001 or 1006 to 4094, or empty");
            for (int i = 0; i < lans.size(); i++) {
                int vlan = o.vlanStart + i * o.vlanStep;
                check(vlan >= 2 && vlan <= 4094 && (vlan < 1002 || vlan > 1005),
                        "VLAN " + vlan + " is not usable: choose IDs from 2 to 1001 or 1006 to 4094");
                check(vlan != o.nativeVlan, "VLAN " + vlan + " is also the native VLAN");
                vlans.add(vlan);
                lanNames.add(unique(name(lans.get(i).need().name(), "VLAN" + vlan), used));
            }
        }

        private void switchSettings() {
            check(o.accessPorts >= 0 && o.accessPorts <= 48, "Access ports per VLAN must be between 0 and 48");
            String prefix = accessPrefix();
            int lastAccess = o.accessPorts * lans.size();
            check(lastAccess <= MAX_SWITCH_PORTS, lans.size() + " VLANs × " + o.accessPorts + " access ports need "
                    + lastAccess + " ports, more than a " + MAX_SWITCH_PORTS + "-port switch has: lower access ports per VLAN");
            if (isAuto(o.uplinkPort)) {
                switchUplink = o.switchStacked ? "GigabitEthernet1/1/1" : "GigabitEthernet0/1";
                // gigabit access ports start at the same 0/1, so take the first port after them
                if (switchUplink.startsWith(prefix)) {
                    check(!needsSwitchUplink() || lastAccess < MAX_SWITCH_PORTS, "The access ports use every port of a "
                            + MAX_SWITCH_PORTS + "-port switch, leaving none for the uplink: lower access ports per VLAN");
                    switchUplink = prefix + (lastAccess + 1);
                }
            } else {
                switchUplink = numbered(o.uplinkPort, 0, "Switch uplink port");
            }
            if (needsSwitchUplink() && switchUplink.startsWith(prefix)) {
                String rest = switchUplink.substring(prefix.length());
                check(!rest.matches("\\d+") || Integer.parseInt(rest) > lastAccess,
                        "Switch uplink port " + switchUplink + " is also one of the access ports");
            }
            if (o.switchKind == SwitchKind.LAYER2 && !o.mgmtVlan.isBlank() && !o.mgmtAddress.isBlank()) {
                if (isAutoWord(o.mgmtVlan)) {
                    mgmtIndex = 0;
                    for (int i = 0; i < lanNames.size(); i++) if (lanNames.get(i).contains("MANAG")) mgmtIndex = i;
                } else {
                    check(o.mgmtVlan.strip().matches("\\d{1,4}") && vlans.contains(Integer.parseInt(o.mgmtVlan.strip())),
                            "Management VLAN must be one of the plan's VLAN IDs " + vlans + ", or auto");
                    mgmtIndex = vlans.indexOf(Integer.parseInt(o.mgmtVlan.strip()));
                }
                Subnet s = lans.get(mgmtIndex).subnet();
                if (isAutoWord(o.mgmtAddress)) {
                    check(s.usable() >= 2, "The management VLAN needs room for the switch's address");
                    mgmtIp = o.gatewayLast ? s.lastHost() - 1 : s.firstHost() + 1;
                } else {
                    check(isAddress(o.mgmtAddress), "Management address must be an IPv4 address, or auto");
                    mgmtIp = Subnet.parseAddress(o.mgmtAddress.strip());
                    check((mgmtIp & s.mask()) == s.network() && mgmtIp != s.network() && mgmtIp != s.broadcast(),
                            "Management address must be a host address in " + format(s.network()) + "/" + s.prefix());
                    check(mgmtIp != gateway(s), "Management address can't be the gateway address");
                }
            }
        }

        private boolean needsSwitchUplink() { return layer3 || stick; }

        private String accessPrefix() { return o.switchPortType + (o.switchStacked ? "1/0/" : "0/"); }

        private void services() {
            check(!o.dhcp || o.reserved >= 0, "Addresses to keep for static devices can't be negative");
            check(!o.dhcp || o.dnsServer.isBlank() || isAddress(o.dnsServer), "DNS server must be an IPv4 address, or empty");
            check(!o.dhcp || o.domainName.isBlank() || DOMAIN.matcher(o.domainName).matches(), "Domain name can only use letters, digits, dots and hyphens");
            if (o.ospf) {
                check(o.ospfProcess >= 1 && o.ospfProcess <= 65535, "OSPF process ID must be between 1 and 65535");
                check(o.ospfArea >= 0, "OSPF area can't be negative");
                check(o.routerId.isBlank() || isAddress(o.routerId), "OSPF router ID must look like an IPv4 address, or be empty");
            }
        }

        private static void access(Access a, String device, String hostname) {
            for (String[] p : new String[][] {{a.enableSecret, "enable secret"}, {a.consolePassword, "console password"},
                    {a.userPassword, "user password"}, {a.vtyPassword, "VTY password"}}) {
                check(p[0].isEmpty() || PASSWORD.matcher(p[0]).matches(), device + " " + p[1] + " can't contain spaces or '?'");
                // IOS reads a lone digit as the encryption type ("enable secret 5 <hash>"), so the command is incomplete
                check(!p[0].matches("\\d"), device + " " + p[1] + " can't be a single digit");
            }
            boolean ssh = a.remote == Remote.SSH || a.remote == Remote.BOTH;
            check(a.banner.indexOf('#') < 0 && !a.banner.matches(".*\\p{Cntrl}.*"), device + " banner can't contain '#'");
            check(a.timeout >= -1 && a.timeout <= 35791, device + " session timeout must be a number of minutes");
            if (ssh) {
                check(!a.username.isEmpty() && !a.userPassword.isEmpty(),
                        device + " SSH needs an SSH username and password: IOS only accepts SSH logins from a local user");
                check(USERNAME.matcher(a.username).matches(), device + " SSH username can only use letters, digits and . _ @ -");
                check(!hostname.isBlank(), device + " SSH needs a hostname to create its keys");
                check(a.sshDomain.isBlank() || DOMAIN.matcher(a.sshDomain.strip()).matches(),
                        device + " SSH domain can only use letters, digits, dots and hyphens, or be empty");
            }
            if (a.remote == Remote.TELNET) {
                check(!a.vtyPassword.isEmpty(), device + " Telnet needs a VTY password");
            }
        }

        /** Names every router port: explicit choices are claimed first, then "auto" ports fill the gaps. */
        private void ports() {
            int lanCount = stick || layer3 ? 1 : lans.size();
            for (int i = 0; i < lanCount; i++) {
                lanPorts.add(isAuto(o.lanPort) ? null : claim(numbered(o.lanPort, i, "Inter-VLAN port")));
            }
            for (int i = 0; i < links.size(); i++) {
                linkPorts.add(isAuto(o.linkInterface) ? null : claim(numbered(o.linkInterface, i, "Point-to-point link interface")));
            }
            if (o.uplink) uplinkName = isAuto(o.uplinkInterface) ? null : claim(numbered(o.uplinkInterface, 0, "Uplink interface"));
            for (int i = 0; i < lanPorts.size(); i++) if (lanPorts.get(i) == null) lanPorts.set(i, nextFree());
            for (int i = 0; i < linkPorts.size(); i++) if (linkPorts.get(i) == null) linkPorts.set(i, nextFree());
            if (o.uplink && uplinkName == null) uplinkName = nextFree();
        }

        private String claim(String port) {
            check(usedPorts.add(port.toLowerCase(Locale.ROOT)), port + " is used for two different things");
            return port;
        }

        private String nextFree() {
            String port;
            do {
                port = o.routerPortType + (o.routerThreeLevel ? "0/0/" : "0/") + nextPort++;
            } while (!usedPorts.add(port.toLowerCase(Locale.ROOT)));
            return port;
        }

        // ---- router ----------------------------------------------------------------------------

        private String routerConfig() {
            Out out = new Out(o);
            out.begin();
            if (!o.routerName.isBlank()) out.line("hostname " + o.routerName.strip());
            if (o.extras) out.line("no ip domain-lookup");
            securityTop(out, o.routerAccess);
            boolean natOn = o.uplink && o.nat != Nat.OFF; // NAT lives on the uplink, so it is off without one
            List<String> passiveInterfaces = new ArrayList<>();
            List<Subnet> routed = new ArrayList<>();

            if (o.dhcp && !layer3) dhcp(out);

            out.section("Interfaces");
            if (stick) {
                String trunk = lanPorts.getFirst();
                out.line("interface " + trunk);
                out.line(" description Trunk to " + switchLabel);
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
            } else if (layer3) {
                Subnet s = transit.subnet();
                out.line("interface " + lanPorts.getFirst());
                out.line(" description Link to " + switchLabel + " (" + clean(transit.need().name()) + ")");
                out.line(" ip address " + format(s.firstHost()) + " " + format(s.mask()));
                if (natOn) out.line(" ip nat inside");
                out.line(" no shutdown");
                routed.add(s);
            } else {
                for (int i = 0; i < lans.size(); i++) {
                    Subnet s = lans.get(i).subnet();
                    String port = lanPorts.get(i);
                    out.line("interface " + port);
                    out.line(" description " + lanNames.get(i));
                    out.line(" ip address " + format(gateway(s)) + " " + format(s.mask()));
                    if (natOn) out.line(" ip nat inside");
                    out.line(" no shutdown");
                    passiveInterfaces.add(port);
                    routed.add(s);
                }
            }
            for (int i = 0; i < links.size(); i++) {
                Vlsm.Allocation a = links.get(i);
                out.line("interface " + linkPorts.get(i));
                out.line(" description " + clean(a.need().name()));
                out.line(" ip address " + format(a.subnet().firstHost()) + " " + format(a.subnet().mask()));
                if (natOn) out.line(" ip nat inside");
                out.line(" no shutdown");
                routed.add(a.subnet());
            }
            String defaultRoute = null;
            if (o.uplink) {
                check(!o.uplinkAddress.isBlank(), "The uplink needs an address with a prefix, or dhcp");
                boolean viaDhcp = o.uplinkAddress.strip().equalsIgnoreCase("dhcp");
                out.line("interface " + uplinkName);
                out.line(" description Internet uplink");
                Subnet up = null;
                if (viaDhcp) {
                    out.line(" ip address dhcp");
                } else {
                    up = parseWithPrefix(o.uplinkAddress, "Uplink address");
                    String upNet = format(up.network()) + "/" + up.prefix();
                    check(up.prefix() <= 31, "Uplink address needs a prefix of /31 or shorter");
                    check(isHost(up, up.address()), "Uplink address can't be the network or broadcast address of " + upNet);
                    Subnet lan = plan.network();
                    check(!overlaps(up, lan), "The uplink subnet " + upNet + " overlaps the plan's network "
                            + format(lan.network()) + "/" + lan.prefix());
                    out.line(" ip address " + format(up.address()) + " " + format(up.mask()));
                }
                if (natOn) out.line(" ip nat outside");
                out.line(" no shutdown");
                if (!o.nextHop.isBlank()) {
                    check(isAddress(o.nextHop), "Next hop must be an IPv4 address, or empty for no default route");
                    if (up != null) { // with dhcp the uplink subnet isn't known until the device gets a lease
                        int hop = Subnet.parseAddress(o.nextHop.strip());
                        check(hop != up.address() && isHost(up, hop), "Next hop must be another host address in the uplink subnet "
                                + format(up.network()) + "/" + up.prefix());
                    }
                    defaultRoute = "ip route 0.0.0.0 0.0.0.0 " + o.nextHop.strip();
                } else if (viaDhcp) {
                    defaultRoute = ""; // learned from the DHCP server
                }
            }

            out.section("Routing");
            if (o.ospf) {
                out.line("router ospf " + o.ospfProcess);
                if (!o.routerId.isBlank()) out.line(" router-id " + o.routerId.strip());
                for (Subnet s : routed) out.line(" network " + format(s.network()) + " " + format(s.wildcard()) + " area " + o.ospfArea);
                if (o.passive) for (String p : passiveInterfaces) out.line(" passive-interface " + p);
                if (defaultRoute != null) out.line(" default-information originate");
            } else if (layer3) {
                for (Vlsm.Allocation a : lans) {
                    out.line("ip route " + format(a.subnet().network()) + " " + format(a.subnet().mask()) + " "
                            + format(transit.subnet().lastHost()));
                }
            }
            if (defaultRoute != null && !defaultRoute.isEmpty()) out.line(defaultRoute);
            else if (defaultRoute != null) out.comment("The default route is learned from DHCP on the uplink");
            staticRoutes(out);

            if (natOn) nat(out);
            lines(out, o.routerAccess);
            out.end();
            return out.toString();
        }

        private void nat(Out out) {
            out.section("NAT");
            Subnet inside = plan.network();
            out.line("access-list 1 permit " + format(inside.network()) + " " + format(inside.wildcard()));
            if (o.nat == Nat.PAT) {
                out.line("ip nat inside source list 1 interface " + uplinkName + " overload");
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
                String s = Subnet.clean(raw);
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
                String s = Subnet.clean(raw);
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

        private String switchConfig() {
            Out out = new Out(o);
            out.begin();
            if (!o.switchName.isBlank()) out.line("hostname " + o.switchName.strip());
            if (o.extras) out.line("no ip domain-lookup");
            if (layer3) out.line("ip routing");
            securityTop(out, o.switchAccess);

            out.section("VLANs");
            for (int i = 0; i < lans.size(); i++) {
                out.line("vlan " + vlans.get(i));
                out.line(" name " + lanNames.get(i));
            }
            if (o.nativeVlan > 1 && stick) { // VLAN 1 always exists and can't be renamed
                out.line("vlan " + o.nativeVlan);
                out.line(" name NATIVE");
            }

            if (o.accessPorts > 0) {
                out.section("Access ports");
                if (!stick && !layer3) out.comment("Connect each router LAN port to an access port in its VLAN");
                String prefix = accessPrefix();
                int port = 1;
                for (int i = 0; i < lans.size(); i++, port += o.accessPorts) {
                    int last = port + o.accessPorts - 1;
                    out.line(o.accessPorts == 1 ? "interface " + prefix + port : "interface range " + prefix + port + " - " + last);
                    out.line(" description " + lanNames.get(i));
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
                out.line("interface " + switchUplink);
                out.line(" description Link to " + routerLabel);
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
                if (stick) {
                    out.section("Trunk to " + routerLabel);
                    out.line("interface " + switchUplink);
                    out.line(" description Trunk to " + routerLabel);
                    out.line(" switchport mode trunk");
                    if (o.nativeVlan > 0) out.line(" switchport trunk native vlan " + o.nativeVlan);
                    out.line(" no shutdown");
                }
                if (mgmtIndex >= 0) management(out);
            }
            lines(out, o.switchAccess);
            out.end();
            return out.toString();
        }

        /** The layer 2 switch's own address, which remote access to it needs. */
        private void management(Out out) {
            Subnet s = lans.get(mgmtIndex).subnet();
            out.section("Management");
            out.line("interface Vlan" + vlans.get(mgmtIndex));
            out.line(" description Management (" + lanNames.get(mgmtIndex) + ")");
            out.line(" ip address " + format(mgmtIp) + " " + format(s.mask()));
            out.line(" no shutdown");
            out.line("ip default-gateway " + format(gateway(s)));
        }

        // ---- shared pieces ---------------------------------------------------------------------

        /** Global security commands; these go near the top so SSH keys see the hostname and domain. */
        private void securityTop(Out out, Access a) {
            boolean ssh = a.remote == Remote.SSH || a.remote == Remote.BOTH;
            boolean anyPassword = !a.enableSecret.isEmpty() || !a.consolePassword.isEmpty()
                    || (a.remote == Remote.TELNET && !a.vtyPassword.isEmpty()) || ssh;
            if (!anyPassword && !ssh && a.banner.isBlank()) return;
            out.section("Security");
            if (a.encrypt && anyPassword) out.line("service password-encryption");
            if (!a.enableSecret.isEmpty()) out.line("enable secret " + a.enableSecret);
            if (ssh) out.line("username " + a.username + " privilege 15 secret " + a.userPassword);
            if (!a.banner.isBlank()) out.line("banner motd #" + a.banner.strip() + "#");
            if (ssh) {
                if (a.sshDomain.isBlank()) out.comment("SSH keys need ip domain-name to be set on the device already");
                else out.line("ip domain-name " + a.sshDomain.strip());
                out.line("crypto key generate rsa general-keys modulus " + a.keyBits);
                out.line("ip ssh version 2");
            }
        }

        /** Console and VTY line settings. */
        private void lines(Out out, Access a) {
            List<String> con = new ArrayList<>();
            if (!a.consolePassword.isEmpty()) {
                con.add(" password " + a.consolePassword);
                con.add(" login");
            }
            if (o.extras) con.add(" logging synchronous");
            if (a.timeout >= 0) con.add(" exec-timeout " + a.timeout + " 0");
            boolean vty = a.remote != Remote.OFF;
            if (con.isEmpty() && !vty) return;
            out.section("Console and remote access");
            if (!con.isEmpty()) {
                out.line("line con 0");
                con.forEach(out::line);
            }
            if (vty) {
                out.line(a.allVty ? "line vty 0 15" : "line vty 0 4");
                if (a.remote != Remote.TELNET) {
                    out.line(" login local");
                } else {
                    out.line(" password " + a.vtyPassword);
                    out.line(" login");
                }
                out.line(" transport input " + switch (a.remote) {
                    case TELNET -> "telnet";
                    case SSH -> "ssh";
                    default -> "ssh telnet";
                });
                if (a.timeout >= 0) out.line(" exec-timeout " + a.timeout + " 0");
            }
        }

        private void dhcp(Out out) {
            out.section("DHCP");
            for (int i = 0; i < lans.size(); i++) {
                Subnet s = lans.get(i).subnet();
                if (!hasPool(s)) {
                    out.comment("No DHCP pool for " + lanNames.get(i) + ": no addresses left to hand out");
                    continue;
                }
                out.line("ip dhcp excluded-address " + format(gateway(s)));
                int from = o.gatewayLast ? s.firstHost() : s.firstHost() + 1;
                if (o.reserved > 0) out.line("ip dhcp excluded-address " + format(from) + " " + format(from + o.reserved - 1));
                boolean mgmtCovered = o.reserved > 0 && Integer.compareUnsigned(mgmtIp - from, o.reserved) < 0;
                if (i == mgmtIndex && !mgmtCovered) out.line("ip dhcp excluded-address " + format(mgmtIp));
            }
            for (int i = 0; i < lans.size(); i++) {
                Subnet s = lans.get(i).subnet();
                if (!hasPool(s)) continue;
                out.line("ip dhcp pool " + lanNames.get(i));
                out.line(" network " + format(s.network()) + " " + format(s.mask()));
                out.line(" default-router " + format(gateway(s)));
                if (!o.dnsServer.isBlank()) out.line(" dns-server " + o.dnsServer.strip());
                if (!o.domainName.isBlank()) out.line(" domain-name " + o.domainName.strip());
            }
        }

        private boolean hasPool(Subnet s) { return s.prefix() < 31 && o.reserved + 1 < s.usable(); }

        private int gateway(Subnet s) { return o.gatewayLast ? s.lastHost() : s.firstHost(); }

        /** {@code base} itself for index 0, then the same name with its last number increased. */
        private static String numbered(String base, int index, String what) {
            String name = base.strip();
            check(INTERFACE.matcher(name).matches(), what + " must be an interface name like GigabitEthernet0/1, or auto");
            if (index == 0) return name;
            Matcher m = TRAILING_NUMBER.matcher(name);
            check(m.matches(), what + " must end with a number");
            return m.group(1) + (Integer.parseInt(m.group(2)) + index);
        }
    }

    /** Config text with optional wrapping and comments. */
    private static final class Out {
        private final StringBuilder sb = new StringBuilder(2048);
        private final Options o;
        private String pending;

        Out(Options o) { this.o = o; }

        void begin() {
            if (o.wrap) {
                line("enable");
                line("configure terminal");
            }
        }

        void end() {
            pending = null;
            if (o.wrap) {
                line("end");
                line("write memory");
            }
        }

        /** Starts a section; its "!" separator and title are written only if something follows. */
        void section(String title) { pending = title; }

        void comment(String text) {
            if (o.comments) line("! " + text);
        }

        void line(String text) {
            if (pending != null) {
                String title = pending;
                pending = null;
                line("!");
                if (o.comments) line("! " + title);
            }
            sb.append(text).append('\n');
        }

        @Override public String toString() { return sb.toString(); }
    }

    /** Port fields: empty or "auto" both mean "pick the next free port", since a port can't be left out. */
    static boolean isAuto(String s) { return s.isBlank() || isAutoWord(s); }

    /** Optional fields: "auto" picks a value, while empty leaves the setting out of the config. */
    static boolean isAutoWord(String s) { return s.strip().equalsIgnoreCase("auto"); }

    /** An IOS-safe name for VLANs and DHCP pools: upper case letters, digits and underscores. */
    static String name(String raw, String fallback) {
        String n = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (n.isEmpty()) n = fallback;
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    /** Free text for descriptions, stripped of anything that could end the command line or, like '?', trigger CLI help. */
    private static String clean(String s) {
        String c = s.replaceAll("[\\p{Cntrl}]", " ").replace("?", "").strip();
        return c.length() > 200 ? c.substring(0, 200) : c;
    }

    /** {@code name}, or with a {@code _2}, {@code _3}… suffix if taken, still within the 32-character limit. */
    private static String unique(String name, Set<String> used) {
        String n = name;
        for (int i = 2; !used.add(n); i++) {
            String suffix = "_" + i;
            n = name.substring(0, Math.min(name.length(), 32 - suffix.length())) + suffix;
        }
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

    /** Whether {@code address} is inside {@code s} and usable by a device (a /31 has no network or broadcast address). */
    private static boolean isHost(Subnet s, int address) {
        return (address & s.mask()) == s.network()
                && (s.prefix() >= 31 || (address != s.network() && address != s.broadcast()));
    }

    /** CIDR blocks either nest or don't touch, so they overlap exactly when the shorter prefix contains both. */
    private static boolean overlaps(Subnet a, Subnet b) {
        int m = Subnet.mask(Math.min(a.prefix(), b.prefix()));
        return (a.network() & m) == (b.network() & m);
    }

    private static boolean isPort(String s) {
        return s.matches("\\d{1,5}") && Integer.parseInt(s) >= 1 && Integer.parseInt(s) <= 65535;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
