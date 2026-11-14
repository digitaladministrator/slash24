package slash24;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/** The last few valid inputs of one field, kept in the user's preferences. */
final class History {
    static final int MAX = 10;

    private final Preferences prefs;
    private final String key;
    private final List<String> items = new ArrayList<>();
    private final Timer settle;
    private String candidate;

    History(Preferences prefs, String key) {
        this.prefs = prefs;
        this.key = key;
        for (String s : prefs.get(key, "").split("\n")) if (!s.isBlank() && items.size() < MAX) items.add(s);
        settle = new Timer(1500, e -> add(candidate));
        settle.setRepeats(false);
    }

    /** Records {@code text} once it has stayed valid and unchanged for a moment. */
    void offer(String text) {
        candidate = text.strip();
        settle.restart();
    }

    void cancel() { settle.stop(); }

    void add(String text) {
        if (text == null || text.isBlank()) return;
        items.remove(text);
        items.addFirst(text);
        while (items.size() > MAX) items.removeLast();
        prefs.put(key, String.join("\n", items));
    }

    void clear() {
        items.clear();
        prefs.remove(key);
    }

    /** A "Recent" button for {@code field}; the Down arrow in the field opens the same menu. */
    JButton button(JTextField field) {
        JButton b = new JButton("Recent");
        b.setToolTipText("Recently used networks (Down arrow in the field)");
        b.addActionListener(e -> menu(field).show(b, 0, b.getHeight()));
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "history");
        field.getActionMap().put("history", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { menu(field).show(field, 0, field.getHeight()); }
        });
        return b;
    }

    private JPopupMenu menu(JTextField field) {
        JPopupMenu menu = new JPopupMenu();
        if (items.isEmpty()) {
            JMenuItem none = new JMenuItem("Nothing yet");
            none.setEnabled(false);
            menu.add(none);
            return menu;
        }
        for (String s : items) {
            JMenuItem item = new JMenuItem(s);
            item.setFont(Ui.MONO);
            item.addActionListener(e -> {
                field.setText(s);
                field.requestFocusInWindow();
            });
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem clear = new JMenuItem("Clear history");
        clear.addActionListener(e -> clear());
        menu.add(clear);
        return menu;
    }
}
