/** Test double for Lmln: records native favorites show/hide calls. */
public class QuickToolbarFakeManager {
    public int shown, hidden;
    public pfz lastReason;
    void M(boolean a, boolean b, pfz reason, boolean c) { shown++; lastReason = reason; }
    void y(boolean a, boolean b) { hidden++; }
}
