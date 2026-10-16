package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33}, manifest = Config.NONE)
public class ClipboardSearchUiAdapterTest {
    @Test public void internalKeysEditOnlyQueryAndRestoreNativePanelOnClose() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout title = new FrameLayout(activity); TextView label = new TextView(activity);
        label.setText("Clipboard"); title.addView(label); root.addView(title);
        LinearLayout actions = new LinearLayout(activity); root.addView(actions);
        FrameLayout body = new FrameLayout(activity); root.addView(body);
        View recycler = new View(activity); body.addView(recycler, new FrameLayout.LayoutParams(-1, -1));
        View.OnClickListener listener = v -> {}; recycler.setOnClickListener(listener);
        activity.setContentView(root);
        List<String> queries = new ArrayList<>();
        TextView host = new TextView(activity); host.setText("Host text stays here");
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(title, label, actions, recycler, queries::add);
        ui.search.performClick(); assertEquals(View.VISIBLE, ui.input.getVisibility());
        ui.type("a"); ui.type("b"); ui.type("Space"); ui.type("c");
        assertEquals("ab c", ui.input.getText().toString());
        assertEquals("ab c", queries.get(queries.size()-1));
        assertEquals("Host text stays here", host.getText().toString());
        ui.type("⌫"); assertEquals("ab ", ui.input.getText().toString());
        ui.type("Done"); assertEquals(View.GONE, ui.keys.getVisibility());
        ui.reset(); assertEquals("", ui.input.getText().toString());
        assertEquals(View.VISIBLE, label.getVisibility());
        ui.close(); ui.close();
        assertSame(body, recycler.getParent()); assertTrue(recycler.hasOnClickListeners());
        assertEquals(1, body.getChildCount()); assertEquals(1, title.getChildCount());
        assertEquals(0, actions.getChildCount());
    }
    @Test public void queryIsBoundedAndBackClearsItWithoutTouchingHost() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout title = new FrameLayout(activity); TextView label = new TextView(activity); title.addView(label);
        LinearLayout actions = new LinearLayout(activity); FrameLayout body = new FrameLayout(activity);
        View recycler = new View(activity); body.addView(recycler);
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(title, label, actions, recycler, value -> {});
        ui.open(); ui.input.setText("a".repeat(ClipboardSearchMatcher.MAX_QUERY_LENGTH+50));
        assertEquals(ClipboardSearchMatcher.MAX_QUERY_LENGTH, ui.input.length());
        ui.input.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
        assertEquals("", ui.input.getText().toString()); assertEquals(View.GONE, ui.keys.getVisibility());
        ui.close();
    }
}
