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
    public static final class Controller { public final java.util.Map<String, Object> h = java.util.Map.of("clipboard", new Descriptor()); }
    public static final class Descriptor {
        public final String b = "clipboard";
        public Definition e(pmy click) { return new Definition(); }
    }
    public static final class Definition { public pnu b() { return new pnu(-10124); } }
    public static Object controller() { return new Controller(); }
    private ClipboardTestNative() {}
}
