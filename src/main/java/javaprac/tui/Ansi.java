package javaprac.tui;

/** ANSI escape sequences used by the renderer. */
final class Ansi {
    static final String ESC = "\u001b[";
    static final String RESET = ESC + "0m";
    static final String BOLD = ESC + "1m";
    static final String DIM = ESC + "2m";
    static final String RED = ESC + "31m";
    static final String GREEN = ESC + "32m";
    static final String YELLOW = ESC + "33m";
    static final String CYAN = ESC + "36m";
    static final String GRAY = ESC + "90m";
    static final String CLEAR_TO_EOL = ESC + "K";
    static final String CLEAR_BELOW = ESC + "J";
    static final String HIDE_CURSOR = ESC + "?25l";
    static final String SHOW_CURSOR = ESC + "?25h";
    static final String ALT_SCREEN_ON = ESC + "?1049h";
    static final String ALT_SCREEN_OFF = ESC + "?1049l";

    private Ansi() {}

    static String moveTo(int row, int col) {
        return ESC + row + ";" + col + "H";
    }

    static String style(String code, String text) {
        return code + text + RESET;
    }
}
