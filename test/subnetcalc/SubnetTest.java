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

        if (failures > 0) {
            System.err.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("All " + checks + " subnet checks passed");
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
