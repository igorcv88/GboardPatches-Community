import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Test doubles for ClipboardKeyboard (manager x:Loxv) and its back-to-prime SoftKeyView. */
public final class ClipboardTestNative {
    public static final class Manager { public final List<nur> events = new ArrayList<>(); public void M(nur event) { events.add(event); } }
    public static final class Keyboard { public final Manager x = new Manager(); }
    public static final class Key extends View {
        final pnu action;
        public Key(Context context, int code) { super(context); action = new pnu(code); }
        public pnu h() { return action; }
    }
    public static int code(Object event) { return ((nur) event).action.c; }
    public static List<nur> events(Object keyboard) { return ((Keyboard) keyboard).x.events; }
    private ClipboardTestNative() {}
}
