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
}
