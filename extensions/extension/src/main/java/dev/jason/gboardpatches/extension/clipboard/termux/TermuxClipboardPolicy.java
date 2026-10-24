package dev.jason.gboardpatches.extension.clipboard;

/** Pure policy: no clipboard persistence, logging, or terminal operations. */
final class TermuxClipboardPolicy {
    static final long MAX_AGE_MS = 120_000;
    private TermuxClipboardPolicy() { }

    static boolean eligible(String packageName, int inputType, int imeOptions,
            boolean incognito, boolean sensitive, boolean disabled, boolean unlocked,
            String text, long copiedAt, long now) {
        return "com.termux".equals(packageName) && inputType == 0
                && (imeOptions & 0x01000000) == 0 // IME_FLAG_NO_PERSONALIZED_LEARNING
                && !incognito && !sensitive && !disabled && unlocked
                && text != null && !text.isEmpty() && text.length() <= 1_000_000
                && copiedAt <= now && now - copiedAt <= MAX_AGE_MS && safeControls(text);
    }

    static boolean needsConfirmation(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                || text.indexOf('\t') >= 0 || text.indexOf('\u2028') >= 0
                || text.indexOf('\u2029') >= 0;
    }

    private static boolean safeControls(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c < 32 && c != '\n' && c != '\r' && c != '\t')
                    || (c >= 127 && c <= 159)) return false;
        }
        return true;
    }
}
