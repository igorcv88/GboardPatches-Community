package dev.jason.gboardpatches.extension.quicktoolbar;
import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
public class QuickToolbar1803RuntimeBindingTest {
    public static class Action { public int c=-40005; }
    public static class KeyBase extends FrameLayout {
        final FrameLayout contents; KeyBase(Activity context) { super(context); contents=new FrameLayout(context); addView(contents); }
        public Object h() { return new Action(); }
        public Object d() { return contents; }
    }
    public static class Key extends KeyBase { Key(Activity context) { super(context); } }
    public static class Candidates { public View c; public View r; public int g=3; }
    public static class Controller {
        public View j; public boolean p=false;
        public Object G() { return this; }
    }
    @Test public void findsNavigationSiblingOutsideCandidateSubtreeAndInheritedKeyMethods() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root=new FrameLayout(activity), candidateRegion=new FrameLayout(activity);
        Key key=new Key(activity); key.setId(0x7f0b053e); root.addView(key);
        root.addView(candidateRegion); View candidate=new View(activity); candidateRegion.addView(candidate);
        View bar=new View(activity); root.addView(bar); activity.setContentView(root);
        Candidates candidates=new Candidates(); candidates.c=candidate; candidates.r=candidateRegion;
        Controller controller=new Controller(); controller.j=bar;
        QuickToolbar1803Runtime.clear();
        QuickToolbar1803Runtime.onControllerView(controller);
        QuickToolbar1803Runtime.onCandidateList(candidates,List.of("one","two","three"));
        QuickToolbar1803Runtime.onCandidates(candidates);
        assertEquals(View.INVISIBLE,key.contents.getVisibility()); assertNotNull(key.getForeground());
        QuickToolbar1803Runtime.onCandidatesCleared(new Candidates());
        assertEquals(View.INVISIBLE,key.contents.getVisibility());
        QuickToolbar1803Runtime.onCandidateList(candidates,List.of());
        candidates.g=0; QuickToolbar1803Runtime.onCandidates(candidates);
        assertEquals(View.VISIBLE,key.contents.getVisibility()); QuickToolbar1803Runtime.clear();
    }
    @Test public void candidateListBeforeControllerCanStillBindToNewRow() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root=new FrameLayout(activity), candidateRegion=new FrameLayout(activity);
        Key key=new Key(activity); key.setId(0x7f0b053e); root.addView(key);
        root.addView(candidateRegion); View candidate=new View(activity); candidateRegion.addView(candidate);
        View bar=new View(activity); root.addView(bar); activity.setContentView(root);
        Candidates owner=new Candidates(); owner.c=candidate; owner.r=candidateRegion;
        Controller controller=new Controller(); controller.j=bar;
        QuickToolbar1803Runtime.clear();
        QuickToolbar1803Runtime.onCandidateList(owner,List.of("one","two"));
        QuickToolbar1803Runtime.onControllerView(controller);
        assertEquals(View.INVISIBLE,key.contents.getVisibility());
        QuickToolbar1803Runtime.clear();
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout root = new FrameLayout(activity);
        final Key key = new Key(activity);
        final Candidates owner = new Candidates();
        final Object manager;
        Fixture() throws Exception {
            manager = Class.forName("QuickToolbarFakeManager").getDeclaredConstructor().newInstance();
            key.setId(0x7f0b053e); root.addView(key);
            owner.c = new View(activity); root.addView(owner.c);
            activity.setContentView(root);
            QuickToolbar1803Runtime.clear();
            QuickToolbar1803Runtime.enabledOverride = true;
            QuickToolbar1803Runtime.onCandidateList(owner, List.of("one", "two"));
        }
        void bind() {
            ManagedController controller = new ManagedController();
            controller.j = new View(activity); root.addView(controller.j);
            controller.manager = manager;
            QuickToolbar1803Runtime.onControllerView(controller);
        }
        void tap() { assertTrue(QuickToolbar1803Runtime.handleEvent(manager,
                new QuickToolbar1803FallbackTest.Event(-40005))); }
        int count(String name) throws Exception { return manager.getClass().getField(name).getInt(manager); }
        void idle() { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }
        void clear() { QuickToolbar1803Runtime.clear(); QuickToolbar1803Runtime.enabledOverride = null; }
    }
    public static class ManagedController {
        public View j; public boolean p, o;
        Object manager;
        public Object G() { return manager; }
    }

    @Test public void fallbackToBoundTransfersModeAndOneTapClosesCodexP2() throws Exception {
        Fixture f = new Fixture();
        try {
            f.tap(); f.bind();
            Candidates stale = new Candidates(); stale.c = f.owner.c;
            QuickToolbar1803Runtime.onCandidates(stale);
            f.tap(); f.idle();
            assertEquals(1, f.count("shown"));
            assertEquals(1, f.count("hidden"));
        } finally { f.clear(); }
    }

    @Test public void boundToFallbackAndRecreatedButtonKeepSelectedMode() throws Exception {
        Fixture f = new Fixture();
        try {
            f.bind(); f.tap();
            f.root.removeView(f.key); f.root.addView(f.key);
            QuickToolbar1803Runtime.onCandidates(f.owner);
            f.tap(); f.idle();
            assertEquals(1, f.count("shown"));
            assertEquals(1, f.count("hidden"));
            f.tap();
            QuickToolbar1803Runtime.onControllerView(new QuickToolbar1803FallbackTest.NonViewController());
            f.tap();
            assertEquals(2, f.count("hidden"));
        } finally { f.clear(); }
    }

    @Test public void repeatedHistoryActionsKeepFavoritesEvenWithEmptyCandidatesAndCoalesce() throws Exception {
        Fixture f = new Fixture();
        try {
            f.bind(); f.tap();
            for (int i = 0; i < 5; i++) {
                QuickToolbar1803Runtime.onCandidateList(f.owner, List.of());
                QuickToolbar1803Runtime.onCandidates(f.owner);
                QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
                QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
                f.idle();
            }
            assertEquals(1, f.count("shown"));
            assertEquals(0, f.count("hidden"));
            f.tap();
            assertEquals(1, f.count("hidden"));
        } finally { f.clear(); }
    }

    @Test public void pendingRestoreUsesReboundManagerAndCannotOutliveDisable() throws Exception {
        Fixture f = new Fixture();
        try {
            f.tap(); QuickToolbar1803Runtime.afterSuccessfulHistoryAction(); f.bind();
            QuickToolbar1803Runtime.enabledOverride = false;
            f.idle();
            assertEquals(1, f.count("shown"));
        } finally { f.clear(); }
    }

    @Test public void controllerWithoutReadyViewReconcilesWithoutWaitingForAnotherCandidateList() throws Exception {
        Fixture f = new Fixture();
        try {
            f.tap();
            QuickToolbar1803Runtime.onControllerView(new QuickToolbar1803FallbackTest.NonViewController());
            f.idle();
            assertEquals(2, f.count("shown"));
            f.tap(); f.idle();
            assertEquals(1, f.count("hidden"));
            assertEquals(2, f.count("shown"));
        } finally { f.clear(); }
    }

}
