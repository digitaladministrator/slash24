package slash24;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Build helper, not shipped in the app: writes the app icon as {@code slash24.ico} (Windows; every
 * size stored as PNG, which Windows Vista and later read) and {@code slash24.png} (256 px) into the
 * folder given as the first argument. Run it when the icon design changes and commit the results.
 */
final class IconTool {
    private IconTool() { }

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final Color ACCENT = new Color(61, 174, 233); // Breeze blue, as in the SVG icon

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args[0]);
        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) pngs.add(png(size));
        Files.write(dir.resolve("slash24.png"), pngs.getLast());

        // ICO: a 6-byte header, a 16-byte directory entry per image, then the PNG data.
        ByteBuffer ico = ByteBuffer.allocate(6 + 16 * SIZES.length + pngs.stream().mapToInt(b -> b.length).sum())
                .order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) SIZES.length);
        int offset = 6 + 16 * SIZES.length;
        for (int i = 0; i < SIZES.length; i++) {
            int size = SIZES[i];
            ico.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size)) // 0 means 256
                    .put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32)
                    .putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) ico.put(png);
        Files.write(dir.resolve("slash24.ico"), ico.array());
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(Ui.icon(size, ACCENT), "png", out);
        return out.toByteArray();
    }
}
