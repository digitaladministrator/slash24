package subnetcalc;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.prefs.Preferences;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Subnet Calculator: a dependency-free Swing app styled after KDE Breeze. */
public final class App {
    /** From the jar manifest (see build.sh); "dev" when run from unpackaged classes. */
    static final String VERSION = App.class.getPackage().getImplementationVersion() != null
            ? App.class.getPackage().getImplementationVersion() : "dev";

    final JFrame frame = new JFrame("Subnet Calculator");
    private final Preferences prefs;
    private final CalculatorView calculator = new CalculatorView();
    private final VlsmView vlsm = new VlsmView();

    public static void main(String[] args) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        Ui.theme = Theme.of(prefs.getBoolean("dark", Theme.SYSTEM_DARK));
        SwingUtilities.invokeLater(() -> {
            Ui.installLookAndFeel();
            new App(prefs).frame.setVisible(true);
        });
    }

    App(Preferences prefs) {
        this.prefs = prefs;

        CardLayout cards = new CardLayout();
        JPanel pages = new JPanel(cards);
        pages.add(calculator.root, "calculator");
        pages.add(vlsm.root, "vlsm");
        ButtonGroup tabs = new ButtonGroup();
        JToggleButton calcTab = tab("Calculator", "calculator", tabs, cards, pages);
        JToggleButton vlsmTab = tab("VLSM planner", "vlsm", tabs, cards, pages);
        boolean showVlsm = "vlsm".equals(prefs.get("page", "calculator"));
        (showVlsm ? vlsmTab : calcTab).setSelected(true);
        cards.show(pages, showVlsm ? "vlsm" : "calculator");

        JLabel title = new JLabel("Subnet Calculator");
        title.setFont(Ui.SANS.deriveFont(Font.BOLD, 18f));
        Ui.Switch dark = new Ui.Switch(Ui.theme.dark(), "Switch between dark and light mode", this::setDark);
        JPanel header = new JPanel(new BorderLayout());
        header.add(Ui.row(title, Ui.dim(VERSION), Box.createHorizontalStrut(12), calcTab, vlsmTab), BorderLayout.WEST);
        header.add(Ui.row(dark.label("Dark mode"), dark), BorderLayout.EAST);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        root.add(header, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setIconImages(Ui.icons());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 640));
        frame.setSize(960, 780);
        frame.setLocationRelativeTo(null);
    }

    private JToggleButton tab(String text, String page, ButtonGroup group, CardLayout cards, JPanel pages) {
        JToggleButton b = new JToggleButton(text);
        group.add(b);
        b.addActionListener(e -> {
            cards.show(pages, page);
            prefs.put("page", page);
        });
        return b;
    }

    private void setDark(boolean dark) {
        prefs.putBoolean("dark", dark);
        Ui.theme = Theme.of(dark);
        Ui.installLookAndFeel();
        SwingUtilities.updateComponentTreeUI(frame);
        frame.setIconImages(Ui.icons());
        calculator.refresh();
        vlsm.refresh();
    }
}
