package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33}, manifest = Config.NONE)
public class ClipboardSearchUiAdapterTest {
    @Test public void headerGetsOnlyASearchButtonAndCloseRemovesIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout actions = new LinearLayout(activity);
        View stock = new View(activity); actions.addView(stock);
        int[] searches = {0};
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(actions, () -> searches[0]++);
        assertSame(ui.search, actions.getChildAt(0));
        assertSame(stock, actions.getChildAt(1));
        for (int i = 0; i < actions.getChildCount(); i++) assertFalse(actions.getChildAt(i) instanceof EditText);
        ui.search.performClick();
        assertEquals(1, searches[0]);
        ui.close(); ui.close();
        assertFalse(ui.attached());
        assertEquals(1, actions.getChildCount());
        ui.search.performClick();
        assertEquals(1, searches[0]);
    }
}
