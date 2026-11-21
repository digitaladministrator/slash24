package slash24;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** RFC 4180 CSV output, streamed row by row so large subnet lists never sit in memory. */
final class Csv {
    private Csv() { }

    static String line(String... cells) { return append(new StringBuilder(), cells).toString(); }

    /** Appends one CSV record, quoting cells that contain commas, quotes or line breaks. */
    private static StringBuilder append(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            String c = defuse(cells[i]);
            if (c.indexOf(',') >= 0 || c.indexOf('"') >= 0 || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) {
                sb.append('"').append(c.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(c);
            }
        }
        return sb.append("\r\n");
    }

    /**
     * Spreadsheets run a cell starting with = + - or @ as a formula, so a subnet name like
     * {@code =HYPERLINK(...)} would run on opening. A leading apostrophe makes it plain text.
     */
    static String defuse(String cell) {
        return !cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0 ? "'" + cell : cell;
    }

    static void write(Path path, Ui.Rows rows) throws IOException {
        StringBuilder sb = new StringBuilder(128); // reused for every row
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.append(append(sb, rows.csvHeader()));
            for (int r = 0; r < rows.getRowCount(); r++) {
                sb.setLength(0);
                w.append(append(sb, rows.csvRow(r)));
            }
        }
    }
}
