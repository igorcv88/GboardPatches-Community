/** Test double for Lmln: records native favorites show/hide calls. */
public class QuickToolbarFakeManager {
    public int shown, hidden;
    public pfz lastReason;
    public static class Holder { public android.view.View j; }
    public final Holder s = new Holder();
    public boolean refuseShow;
    void M(boolean a, boolean b, pfz reason, boolean c) { shown++; lastReason = reason; if (s.j != null && !refuseShow) s.j.setVisibility(android.view.View.VISIBLE); }
    void y(boolean a, boolean b) { hidden++; if (s.j != null) s.j.setVisibility(android.view.View.GONE); }
}
