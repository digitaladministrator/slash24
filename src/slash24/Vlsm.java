package slash24;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Variable-length subnet masking. Requirements are placed largest first, so every block
 * lands on a boundary aligned to its own size and the network is packed without gaps.
 */
final class Vlsm {
    private Vlsm() { }

    static final long MAX_HOSTS = (1L << 32) - 2;

    record Need(String name, long hosts) { }

    record Allocation(Need need, Subnet subnet) {
        long spare() { return subnet.usable() - need.hosts(); }
    }

    record Plan(Subnet network, List<Allocation> allocations, List<Subnet> free, long used) { }

    /**
     * One requirement per line (commas and semicolons also separate). Each ends with its host count,
     * optionally preceded by a name: {@code Sales 60}, {@code WAN link: 2}, or just {@code 12}.
     */
    static List<Need> parse(String text) {
        List<Need> needs = new ArrayList<>();
        int entry = 0;
        for (String raw : text.split("\\R|[,;]")) {
            String s = raw.strip();
            if (s.isEmpty()) continue;
            entry++;
            int i = s.length();
            while (i > 0 && s.charAt(i - 1) >= '0' && s.charAt(i - 1) <= '9') i--;
            if (i == s.length()) throw new IllegalArgumentException("Entry " + entry + " (\"" + s + "\") must end with a host count");
            String digits = s.substring(i);
            long hosts = digits.length() > 10 ? Long.MAX_VALUE : Long.parseLong(digits);
            if (hosts < 1) throw new IllegalArgumentException("Entry " + entry + " (\"" + s + "\") needs at least 1 host");
            if (hosts > MAX_HOSTS) throw new IllegalArgumentException("Entry " + entry + " (\"" + s + "\") has more hosts than IPv4 allows");
            String name = s.substring(0, i).replaceAll("[\\s:=–-]+$", "");
            needs.add(new Need(name.isEmpty() ? "Subnet " + entry : name, hosts));
        }
        if (needs.isEmpty()) throw new IllegalArgumentException("Add at least one subnet, e.g. \"Sales 50\"");
        return needs;
    }

    /** Smallest prefix with room for {@code hosts}; /31 and /32 only when point-to-point blocks are allowed. */
    static int prefixFor(long hosts, boolean pointToPoint) {
        if (pointToPoint && hosts <= 2) return hosts == 1 ? 32 : 31;
        int p = 30;
        while (p > 0 && (1L << (32 - p)) - 2 < hosts) p--;
        return p;
    }

    static Plan plan(Subnet network, List<Need> needs, boolean pointToPoint) {
        List<Need> sorted = new ArrayList<>(needs);
        sorted.sort(Comparator.comparingLong(Need::hosts).reversed());
        long need = 0;
        for (Need n : sorted) need += 1L << (32 - prefixFor(n.hosts(), pointToPoint));
        if (need > network.total()) {
            throw new IllegalArgumentException(String.format("These subnets need %,d addresses, but a /%d only has %,d",
                    need, network.prefix(), network.total()));
        }
        long start = network.network() & 0xFFFFFFFFL, cursor = start;
        List<Allocation> allocations = new ArrayList<>(sorted.size());
        for (Need n : sorted) {
            int p = prefixFor(n.hosts(), pointToPoint);
            allocations.add(new Allocation(n, new Subnet((int) cursor, p)));
            cursor += 1L << (32 - p);
        }
        return new Plan(network, List.copyOf(allocations), Cidr.range(cursor, start + network.total()), cursor - start);
    }
}
