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
    /** Mirrors ays: exposes get without implementing java.util.Map. */
    public static final class Catalog {
        public Object get(Object key) { return "clipboard".equals(key) ? new Descriptor() : null; }
    }
    public static final class NativeController { public final Catalog h = new Catalog(); }
    /** Mirrors mln.h, which an exit hook mistakenly captures instead of mlh.h. */
    public static final class ManagerReceiver { public final boolean h = true; }
    public static Object controller() { return new Controller(); }
    public static Object nativeController() { return new NativeController(); }
    public static Object overwrittenReceiver() { return new ManagerReceiver(); }
    private ClipboardTestNative() {}
}
