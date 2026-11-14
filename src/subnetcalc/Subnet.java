package subnetcalc;

/**
 * An IPv4 address plus prefix length. Every value is derived from the two ints on demand,
 * so instances are tiny, immutable and cheap enough to create per table cell.
 */
public record Subnet(int address, int prefix) {

    public Subnet {
        if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("Prefix must be between /0 and /32");
    }

    public static int mask(int prefix) { return prefix == 0 ? 0 : -1 << (32 - prefix); }

    public int mask() { return mask(prefix); }
    public int wildcard() { return ~mask(); }
    public int network() { return address & mask(); }
    public int broadcast() { return address | wildcard(); }
    public long total() { return 1L << (32 - prefix); }

    /** /31 point-to-point links (RFC 3021) and /32 host routes have no network or broadcast address to reserve. */
    public long usable() { return prefix >= 31 ? total() : total() - 2; }
    public int firstHost() { return prefix >= 31 ? network() : network() + 1; }
    public int lastHost() { return prefix >= 31 ? broadcast() : broadcast() - 1; }

    /** Number of {@code /newPrefix} subnets inside this network; {@code newPrefix} must be {@code >= prefix}. */
    public long subnetCount(int newPrefix) { return 1L << (newPrefix - prefix); }

    /** The {@code index}-th {@code /newPrefix} subnet of this network. */
    public Subnet subnet(long index, int newPrefix) {
        return new Subnet(network() + (int) (index << (32 - newPrefix)), newPrefix);
    }

    public char addressClass() {
        int o = address >>> 24;
        return o < 128 ? 'A' : o < 192 ? 'B' : o < 224 ? 'C' : o < 240 ? 'D' : 'E';
    }

    public String type() {
        int a = address;
        if (in(a, 0x0A000000, 8) || in(a, 0xAC100000, 12) || in(a, 0xC0A80000, 16)) return "Private (RFC 1918)";
        if (in(a, 0x7F000000, 8)) return "Loopback";
        if (in(a, 0xA9FE0000, 16)) return "Link-local";
        if (in(a, 0x64400000, 10)) return "Shared / CGNAT (RFC 6598)";
        if (in(a, 0xC0000200, 24) || in(a, 0xC6336400, 24) || in(a, 0xCB007100, 24)) return "Documentation (RFC 5737)";
        if (in(a, 0x00000000, 8)) return "\"This\" network";
        if (in(a, 0xE0000000, 4)) return "Multicast";
        if (a == -1) return "Limited broadcast";
        if (in(a, 0xF0000000, 4)) return "Reserved";
        return "Public";
    }

    private static boolean in(int address, int network, int prefix) { return (address & mask(prefix)) == network; }

    /**
     * Parses {@code 10.0.0.5/24}, {@code 10.0.0.5/255.255.255.0}, {@code 10.0.0.5 255.255.255.0} or
     * {@code 10.0.0.5 24}. A bare address gets its classful default prefix.
     */
    public static Subnet parse(String text) {
        String s = text.strip();
        if (s.isEmpty()) throw new IllegalArgumentException("Enter an address, e.g. 192.168.1.10/24");
        int cut = s.indexOf('/');
        for (int i = 0; cut < 0 && i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) cut = i;
        if (cut < 0) {
            int a = parseAddress(s);
            return new Subnet(a, classfulPrefix(a));
        }
        return new Subnet(parseAddress(s.substring(0, cut).strip()), parseMask(s.substring(cut + 1).strip()));
    }

    public static int classfulPrefix(int address) {
        int o = address >>> 24;
        return o < 128 ? 8 : o < 192 ? 16 : o < 224 ? 24 : 32;
    }

    public static int parseAddress(String s) {
        int value = 0, octet = -1, dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (octet < 0 || ++dots > 3) throw badAddress(s);
                value = value << 8 | octet;
                octet = -1;
            } else if (c >= '0' && c <= '9') {
                octet = octet < 0 ? c - '0' : octet * 10 + c - '0';
                if (octet > 255) throw badAddress(s);
            } else {
                throw badAddress(s);
            }
        }
        if (octet < 0 || dots != 3) throw badAddress(s);
        return value << 8 | octet;
    }

    /** Accepts a prefix length ({@code 24} or {@code /24}) or a dotted netmask. */
    public static int parseMask(String s) {
        if (s.indexOf('.') < 0) return parsePrefix(s);
        int m = parseAddress(s), p = Integer.bitCount(m);
        if (mask(p) != m) throw new IllegalArgumentException(s + " is not a valid netmask");
        return p;
    }

    public static int parsePrefix(String s) {
        if (s.startsWith("/")) s = s.substring(1);
        int p = 0;
        if (s.isEmpty() || s.length() > 2) throw badPrefix(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw badPrefix(s);
            p = p * 10 + c - '0';
        }
        if (p > 32) throw badPrefix(s);
        return p;
    }

    public static String format(int a) {
        return (a >>> 24) + "." + (a >>> 16 & 255) + "." + (a >>> 8 & 255) + "." + (a & 255);
    }

    private static IllegalArgumentException badAddress(String s) {
        return new IllegalArgumentException("\"" + s + "\" is not a valid IPv4 address");
    }

    private static IllegalArgumentException badPrefix(String s) {
        return new IllegalArgumentException("\"" + s + "\" is not a prefix between 0 and 32");
    }
}
