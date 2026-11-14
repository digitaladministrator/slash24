package subnetcalc;

import javax.swing.JComponent;

/** One tab of the window. */
interface Page {
    JComponent root();

    /** Ctrl+L: focus and select the page's main input. */
    void focusInput();

    /** Ctrl+Shift+C: copy the page's results. */
    void copy();

    /** Recompute everything, e.g. after a theme change. */
    void refresh();
}
