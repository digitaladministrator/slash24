package slash24;

import java.util.Map;

/**
 * Hover help for every setting: a short description and an example. Keys are "Section/Label", or
 * just "Label" for settings that read the same everywhere (for example the security rows, which
 * appear for both the router and the switch).
 */
final class Help {
    private Help() { }

    record Entry(String text, String example) { }

    /** The entry for {@code section}/{@code label}, falling back to {@code label} alone. */
    static Entry find(String section, String label) {
        Entry e = ENTRIES.get(section + "/" + label);
        return e != null ? e : ENTRIES.get(label);
    }

    private static Entry e(String text, String example) { return new Entry(text, example); }

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
        // ---- Calculator, VLSM planner and Route tools
        Map.entry("IPv4 address with prefix or netmask", e(
            "The address to work out. Add a prefix length or a netmask; a bare address uses its classful default.",
            "192.168.1.10/24  or  10.0.0.5 255.255.255.0")),
        Map.entry("Contains?", e(
            "Checks whether an address is inside this network, where it sits, and which split subnet it falls in.",
            "192.168.1.77")),
        Map.entry("Split into  /", e(
            "Divides the network into equal subnets of this prefix length. It must be at least as long as the network's own prefix.",
            "26  splits a /24 into four /26s")),
        Map.entry("Network to divide", e(
            "The address block the VLSM planner fits your subnets into.",
            "192.168.10.0/24")),
        Map.entry("Subnets needed: a name and host count on each line, e.g. \"Sales 60\"", e(
            "One subnet per line: a name, then a space or colon, then how many hosts it needs. Commas also separate entries.",
            "Sales 60\nWAN link 2")),
        Map.entry("Use /31 and /32 links", e(
            "On: 2-host needs get a /31 (RFC 3021) and 1-host needs a /32. Off: both get a /30, the traditional point-to-point size.",
            "WAN link 2  becomes a /31 instead of a /30")),
        Map.entry("Networks to summarize: one per line, or separated by commas", e(
            "Networks to combine into one summary route. A bare address counts as a /32.",
            "192.168.0.0/24, 192.168.1.0/24")),
        Map.entry("First and last address of the range", e(
            "Any address range; it is split into the fewest CIDR blocks that cover exactly that range.",
            "10.0.0.5  to  10.0.0.77")),

        // ---- IOS config: router
        Map.entry("Router/Hostname", e(
            "The router's name, shown in its prompt. Empty leaves the hostname line out (SSH needs one).",
            "R1")),
        Map.entry("Router/Port type", e(
            "The kind of Ethernet ports the router model has.",
            "GigabitEthernet  on a 2911 or 4321")),
        Map.entry("Router/Numbering", e(
            "How the router numbers its ports: slot/port, or slot/subslot/port on ISR 4000 routers.",
            "0/0 → GigabitEthernet0/1,  0/0/0 → GigabitEthernet0/0/1")),
        Map.entry("Router/Inter-VLAN routing", e(
            "How the router routes between VLANs: one trunk port with a subinterface per VLAN, or a separate port per subnet.",
            "Router on a stick → GigabitEthernet0/0.10, .20, .30")),
        Map.entry("Router/Trunk port", e(
            "The router port that carries traffic between VLANs. auto (or empty) uses the first free port.",
            "GigabitEthernet0/1")),
        Map.entry("Router/First LAN port", e(
            "The router port for the first LAN; the others count up from it. auto (or empty) uses free ports.",
            "GigabitEthernet0/0  → next LANs on 0/1, 0/2")),
        Map.entry("Router/Port to switch", e(
            "The router port connected to the layer 3 switch. auto (or empty) uses the first free port.",
            "GigabitEthernet0/0")),

        // ---- IOS config: switch
        Map.entry("Switch/Switch", e(
            "None: no switch config. Layer 2: VLANs and access ports, with the router routing between VLANs. "
                + "Layer 3: the switch routes between VLANs itself.",
            "Layer 3 for a 3560 or 3650")),
        Map.entry("Switch/Hostname", e(
            "The switch's name, shown in its prompt. Empty leaves the hostname line out.",
            "S1")),
        Map.entry("Switch/Port type", e(
            "The kind of access ports the switch has.",
            "FastEthernet on a 2960,  GigabitEthernet on a 3650")),
        Map.entry("Switch/Numbering", e(
            "How the switch numbers its ports: 0/1 on most switches, 1/0/1 on stackable models.",
            "0/1 → FastEthernet0/1,  1/0/1 → GigabitEthernet1/0/1")),
        Map.entry("Switch/Trunk port", e(
            "The switch port that trunks VLANs to the router. auto (or empty) uses GigabitEthernet0/1, or 1/1/1 on stackable switches.",
            "GigabitEthernet0/1")),
        Map.entry("Switch/Routed port to router", e(
            "The layer 3 switch port that connects to the router as a routed (no switchport) link.",
            "GigabitEthernet0/1")),
        Map.entry("First VLAN ID", e(
            "The VLAN number for the first LAN in the plan; the others count up by the step.",
            "10  → VLANs 10, 20, 30 with a step of 10")),
        Map.entry("VLAN ID step", e(
            "How much each LAN's VLAN number goes up by.",
            "10  → 10, 20, 30;   1 → 10, 11, 12")),
        Map.entry("Native VLAN", e(
            "The untagged VLAN on the trunk. Empty keeps the default (VLAN 1) and leaves the setting out.",
            "99")),
        Map.entry("Access ports per VLAN", e(
            "How many switch ports to assign to each VLAN, numbered from port 1, up to 48 in all. 0 or empty adds none.",
            "2  → Fa0/1–2 for the first VLAN, Fa0/3–4 for the next")),
        Map.entry("Management VLAN", e(
            "The VLAN the layer 2 switch gets its own address in, for remote access. auto picks a VLAN named like "
                + "\"Management\"; empty leaves management out.",
            "40")),
        Map.entry("Management address", e(
            "The switch's own address in the management VLAN. auto takes the address next to the gateway; empty leaves management out.",
            "192.168.10.114")),

        // ---- IOS config: addressing
        Map.entry("Gateway", e(
            "Whether each LAN's gateway is its first or last usable address.",
            "First usable → 192.168.10.1 in 192.168.10.0/26")),
        Map.entry("1–2 host subnets are links", e(
            "Treat subnets with 1 or 2 hosts as router-to-router links instead of LANs (no VLAN or DHCP).",
            "WAN link 2  → its own router port")),
        Map.entry("Link interface", e(
            "The router port for the first point-to-point link; more links count up from it. auto (or empty) uses free ports.",
            "Serial0/1/0  → next link on Serial0/1/1")),

        // ---- IOS config: DHCP
        Map.entry("DHCP pools", e(
            "Creates a DHCP pool for every LAN, with the gateway excluded.",
            "ip dhcp pool SALES")),
        Map.entry("DNS server", e(
            "The DNS server handed out by DHCP. Empty leaves it out.",
            "8.8.8.8")),
        Map.entry("Domain name", e(
            "The domain name handed out by DHCP. Empty leaves it out.",
            "lab.local")),
        Map.entry("Keep for static devices", e(
            "How many addresses at the start of each LAN DHCP must not hand out (for printers, servers...). Empty means none.",
            "5  → .2 to .6 stay free when the gateway is .1")),

        // ---- IOS config: routing
        Map.entry("OSPF", e(
            "Advertises every subnet with OSPF, using wildcard-mask network statements.",
            "network 192.168.10.0 0.0.0.63 area 0")),
        Map.entry("Process ID", e(
            "The OSPF process number. It only matters on this device and doesn't need to match neighbours.",
            "1")),
        Map.entry("Area", e(
            "The OSPF area for all the networks. Single-area designs use 0 (the backbone).",
            "0")),
        Map.entry("Router ID", e(
            "A fixed OSPF router ID. Empty lets IOS pick one from its interface addresses.",
            "1.1.1.1")),
        Map.entry("Passive LAN interfaces", e(
            "Stops OSPF hellos on LAN interfaces, where no other routers are, while still advertising those networks.",
            "passive-interface GigabitEthernet0/0.10")),
        Map.entry("Static routes: network/prefix next-hop, one per line", e(
            "Extra static routes to add to the router, one per line. Empty adds none.",
            "10.50.0.0/16 192.168.10.122")),

        // ---- IOS config: Internet uplink
        Map.entry("Uplink", e(
            "Adds an Internet-facing interface with a default route, and makes NAT available.",
            "interface GigabitEthernet0/2  +  ip route 0.0.0.0 0.0.0.0 …")),
        Map.entry("Internet uplink/Interface", e(
            "The router port facing the Internet. auto (or empty) uses the first free port.",
            "GigabitEthernet0/2")),
        Map.entry("Address", e(
            "The uplink's address with a prefix, or dhcp to get one from the provider.",
            "203.0.113.2/30  or  dhcp")),
        Map.entry("Next hop", e(
            "The provider's router, in the uplink subnet, used for the default route. Empty means no default route.",
            "203.0.113.1")),
        Map.entry("NAT", e(
            "Off; PAT, where every inside host shares the uplink address; or Pool, a range of public addresses.",
            "PAT → ip nat inside source list 1 interface Gi0/2 overload")),
        Map.entry("Pool start", e(
            "The first public address in the NAT pool.",
            "203.0.113.10")),
        Map.entry("Pool end", e(
            "The last public address in the NAT pool.",
            "203.0.113.20")),
        Map.entry("Pool prefix", e(
            "The prefix length of the public network the pool belongs to.",
            "24  → netmask 255.255.255.0")),
        Map.entry("Overload the pool", e(
            "Lets many inside hosts share the pool addresses using ports (PAT). Off maps one host per pool address.",
            "ip nat inside source list 1 pool PUBLIC overload")),
        Map.entry("Static NAT: \"inside outside\" or \"tcp inside port outside port\"", e(
            "Fixed mappings, one per line: a whole address, or a single TCP/UDP port forward.",
            "192.168.10.10 203.0.113.5\ntcp 192.168.10.20 80 203.0.113.2 8080")),

        // ---- IOS config: security and remote access (router and switch)
        Map.entry("Enable secret", e(
            "The password for privileged (enable) mode, stored hashed. Empty leaves it out. Not saved between runs.",
            "class")),
        Map.entry("Console password", e(
            "The password for the console port. Empty leaves the console open. Not saved between runs.",
            "cisco")),
        Map.entry("Remote access", e(
            "How the device can be reached over the network: Telnet (unencrypted), SSH (encrypted), both, or off.",
            "SSH → transport input ssh")),
        Map.entry("VTY password", e(
            "The Telnet login password for the VTY lines. Not saved between runs.",
            "telnetpw")),
        Map.entry("SSH username", e(
            "The local user that signs in over SSH (IOS only accepts SSH logins from a local user). With Both, Telnet uses it too.",
            "admin")),
        Map.entry("SSH password", e(
            "The SSH user's password, stored hashed. Not saved between runs.",
            "S3cret!")),
        Map.entry("SSH domain", e(
            "The domain name SSH keys are created with. Empty leaves it out, for a device that already has one.",
            "lab.local")),
        Map.entry("RSA key size", e(
            "The size of the SSH key. 2048 is stronger; 1024 is enough for labs and older devices.",
            "crypto key generate rsa general-keys modulus 2048")),
        Map.entry("VTY lines", e(
            "Which remote-session lines to configure: 0–4 (5 sessions) or 0–15 (16, on devices that have them).",
            "line vty 0 4")),
        Map.entry("Idle timeout (minutes)", e(
            "Signs idle console and remote sessions out after this many minutes; 0 never does. Empty keeps the device default.",
            "10  → exec-timeout 10 0")),
        Map.entry("Encrypt passwords", e(
            "Hides plain-text passwords in the running config with service password-encryption.",
            "service password-encryption")),
        Map.entry("Banner (MOTD)", e(
            "A message shown before login. Empty leaves it out; it can't contain #.",
            "Authorized access only")),

        // ---- IOS config: output
        Map.entry("enable … write memory", e(
            "Wraps the config so it can be pasted straight into a fresh device's CLI and saved.",
            "enable / configure terminal … end / write memory")),
        Map.entry("Lab extras", e(
            "Small lab conveniences: no DNS lookups on typos, and log messages that don't break your typing.",
            "no ip domain-lookup,  logging synchronous")),
        Map.entry("Comments", e(
            "Labels each section of the config with ! comments, which IOS ignores.",
            "! DHCP"))
    );
}
