package dev.jason.gboardpatches.extension.clipboard;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Streaming text matching: never normalize a 200k-character clip into a second full copy. */
public final class ClipboardSearchMatcher {
    public static final int MAX_QUERY_LENGTH = 256;
    private static final int CHUNK = 2048;
    private final List<String> terms;
    private final int overlap;

    public ClipboardSearchMatcher(String query) {
        String bounded = query == null ? "" : query.substring(0, Math.min(query.length(), MAX_QUERY_LENGTH));
        if (!bounded.isEmpty() && Character.isHighSurrogate(bounded.charAt(bounded.length() - 1))) {
            bounded = bounded.substring(0, bounded.length() - 1);
        }
        List<String> tokens = new ArrayList<>();
        int longest = 0;
        for (String token : normalize(bounded).split("[\\s\\p{Z}]+")) {
            if (!token.isEmpty() && !tokens.contains(token)) {
                tokens.add(token);
                longest = Math.max(longest, token.length());
            }
        }
        terms = List.copyOf(tokens);
        overlap = Math.max(0, longest - 1);
    }

    public boolean empty() { return terms.isEmpty(); }

    public boolean matches(String text, BooleanSupplier cancelled) {
        if (text == null) return false;
        if (terms.isEmpty()) return !cancelled.getAsBoolean();
        boolean[] found = new boolean[terms.size()];
        int remaining = found.length;
        String tail = "";
        for (int offset = 0; offset < text.length();) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return false;
            int end = Math.min(text.length(), offset + CHUNK);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) end--;
            String normalized = tail + normalize(text.substring(offset, end));
            for (int i = 0; i < found.length; i++) {
                if (!found[i] && normalized.contains(terms.get(i))) {
                    found[i] = true;
                    if (--remaining == 0) return !cancelled.getAsBoolean();
                }
            }
            tail = normalized.substring(Math.max(0, normalized.length() - overlap));
            offset = end;
        }
        return false;
    }

    private static String normalize(String text) {
        // ROOT uppercase has no final-sigma contextual rule. Use the same caseless key
        // for query/chunks, retaining full expansions such as sharp s -> SS.
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).toUpperCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(decomposed.length());
        decomposed.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) result.appendCodePoint(cp);
        });
        return result.toString();
    }
}
