package subnetcalc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CIDR block arithmetic on unsigned address ranges: range splitting, merging and summarization. */
final class Cidr {
    private Cidr() { }

    /**
     * {@code route} is the smallest single block covering every input; {@code exact} is the fewest
     * blocks covering precisely the inputs (overlaps and neighbours merged); {@code covered} is their size.
     */
    record Summary(Subnet route, List<Subnet> exact, long covered) { }

    static long start(Subnet s) { return s.network() & 0xFFFFFFFFL; }

    /** The range {@code [from, to)} as the fewest CIDR blocks. */
    static List<Subnet> range(long from, long to) {
        List<Subnet> blocks = new ArrayList<>();
        while (from < to) {
            int bits = Math.min(32, Long.numberOfTrailingZeros(from));
            while (1L << bits > to - from) bits--;
            blocks.add(new Subnet((int) from, 32 - bits));
            from += 1L << bits;
        }
        return List.copyOf(blocks);
    }

    /** Networks separated by new lines, commas or semicolons. A bare address counts as a /32. */
    static List<Subnet> parseList(String text) {
        List<Subnet> nets = new ArrayList<>();
        int entry = 0;
        for (String raw : text.split("\\R|[,;]")) {
            String s = raw.strip();
            if (s.isEmpty()) continue;
            entry++;
            try {
                boolean bare = s.indexOf('/') < 0 && s.chars().noneMatch(Character::isWhitespace);
                Subnet n = bare ? new Subnet(Subnet.parseAddress(s), 32) : Subnet.parse(s);
                nets.add(new Subnet(n.network(), n.prefix()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Entry " + entry + ": " + e.getMessage());
            }
        }
        if (nets.isEmpty()) throw new IllegalArgumentException("Add the networks to summarize, e.g. 10.0.0.0/24");
        return nets;
    }

    static Summary summarize(List<Subnet> nets) {
        long lo = Long.MAX_VALUE, hi = -1;
        for (Subnet s : nets) {
            lo = Math.min(lo, start(s));
            hi = Math.max(hi, start(s) + s.total() - 1);
        }
        int prefix = Integer.numberOfLeadingZeros((int) lo ^ (int) hi);
        Subnet route = new Subnet((int) lo & Subnet.mask(prefix), prefix);

        List<Subnet> sorted = new ArrayList<>(nets);
        sorted.sort(Comparator.comparingLong(Cidr::start));
        List<Subnet> exact = new ArrayList<>();
        long covered = 0, runStart = -1, runEnd = -1;
        for (Subnet s : sorted) {
            long a = start(s), b = a + s.total();
            if (a > runEnd) {
                if (runEnd > runStart) {
                    exact.addAll(range(runStart, runEnd));
                    covered += runEnd - runStart;
                }
                runStart = a;
                runEnd = b;
            } else {
                runEnd = Math.max(runEnd, b);
            }
        }
        exact.addAll(range(runStart, runEnd));
        covered += runEnd - runStart;
        return new Summary(route, List.copyOf(exact), covered);
    }
}
