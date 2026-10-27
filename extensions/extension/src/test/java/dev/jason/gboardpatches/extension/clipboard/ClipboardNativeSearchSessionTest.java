package dev.jason.gboardpatches.extension.clipboard;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClipboardNativeSearchSessionTest {
    static final class Host implements ClipboardNativeSearchSession.Host {
        final List<String> calls = new ArrayList<>();
        String fail = "";
        boolean surface = true, alphabet = true, routed = true;
        boolean step(String name, boolean ok) {
            calls.add(name);
            if (fail.equals(name)) throw new IllegalStateException(name);
            return ok;
        }
        public boolean showSurface() { return step("surface", surface); }
        public void hideSurface() { step("hide", true); }
        public boolean switchToAlphabet() { return step("alphabet", alphabet); }
        public boolean routeToField() { return step("route", routed); }
        public boolean restoreRouting() { return step("restore", !fail.equals("restore-false")); }
        public void returnToClipboard() { step("clipboard", true); }
        public boolean commitToEditor(String text) { return step("commit:" + text, true); }
    }

    @Test public void openRoutesInputLastAndCancelRestoresItFirst() {
        Host host = new Host();
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        assertTrue(session.open());
        assertEquals(List.of("surface", "alphabet", "route"), host.calls);
        host.calls.clear();
        session.cancel();
        assertEquals(List.of("restore", "hide", "clipboard"), host.calls);
        assertEquals(ClipboardNativeSearchSession.State.CLOSED, session.state());
        host.calls.clear();
        session.cancel(); session.end();
        assertTrue(host.calls.isEmpty());
    }

    @Test public void pickRestoresTheEditorBeforeCommittingAndStaysOnAlphabet() {
        Host host = new Host();
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        session.open(); host.calls.clear();
        assertTrue(session.pick("texto 😀"));
        assertEquals(List.of("restore", "hide", "commit:texto 😀"), host.calls);
        assertFalse(session.pick("again"));
    }

    @Test public void lifecycleEndRestoresWithoutSwitchingKeyboards() {
        Host host = new Host();
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        session.open(); host.calls.clear();
        session.end();
        assertEquals(List.of("restore", "hide"), host.calls);
    }

    @Test public void everyFailedOpenStepRollsBackWhatWasDone() {
        Object[][] cases = {
            {"surface-false", List.of("surface", "hide")},
            {"surface", List.of("surface", "hide")},
            {"alphabet-false", List.of("surface", "alphabet", "hide")},
            {"route-false", List.of("surface", "alphabet", "route", "restore", "hide", "clipboard")},
            {"route", List.of("surface", "alphabet", "route", "restore", "hide", "clipboard")},
            {"alphabet", List.of("surface", "alphabet", "hide")},
        };
        for (Object[] c : cases) {
            Host host = new Host();
            String name = (String) c[0];
            if (name.equals("surface-false")) host.surface = false;
            else if (name.equals("alphabet-false")) host.alphabet = false;
            else if (name.equals("route-false")) host.routed = false;
            else host.fail = name;
            ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
            assertFalse(name, session.open());
            assertEquals(name, c[1], host.calls);
            assertEquals(ClipboardNativeSearchSession.State.CLOSED, session.state());
        }
    }

    @Test public void failingRestoreStillHidesTheSurface() {
        Host host = new Host();
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        session.open(); host.calls.clear(); host.fail = "restore";
        session.end();
        assertEquals(List.of("restore", "hide"), host.calls);
        assertEquals(ClipboardNativeSearchSession.State.CLOSED, session.state());
    }
    @Test public void failedOrThrowingRestoreMustNeverCommitIntoTheSearchProvider() {
        for (String failure : List.of("restore", "restore-false")) {
            Host host = new Host();
            ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
            assertTrue(session.open()); host.calls.clear(); host.fail = failure;
            assertFalse(session.pick("must not be committed"));
            assertEquals(List.of("restore", "hide"), host.calls);
            assertFalse(session.pick("second tap"));
        }
    }

}
