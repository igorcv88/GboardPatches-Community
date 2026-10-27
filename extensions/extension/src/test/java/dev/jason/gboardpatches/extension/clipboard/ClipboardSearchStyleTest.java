package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClipboardSearchStyleTest {
    private Activity activity;
    private FrameLayout input;
    private LinearLayout list;

    /** Window root → inputArea (translucent theme backdrop) → list: header row, then a card. */
    private void nativeClipboard() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout window = new FrameLayout(activity);
        window.setBackground(new ColorDrawable(Color.RED)); // Shared with the slot: must not be read.
        input = new FrameLayout(activity); input.setId(android.R.id.inputArea);
        input.setBackground(new ColorDrawable(0x99202124));
        window.addView(input, new FrameLayout.LayoutParams(-1, -1));
        list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        input.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView header = new TextView(activity); header.setText("Recent");
        list.addView(header, new LinearLayout.LayoutParams(-1, 40));
        FrameLayout item = new FrameLayout(activity);
        item.setPadding(0, 3, 0, 3);
        FrameLayout card = new FrameLayout(activity);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xff3c4043); shape.setCornerRadius(16);
        card.setBackground(shape);
        TextView text = new TextView(activity); text.setText("clip");
        text.setTextColor(0xffe8eaed); text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30);
        text.setMaxLines(3);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-1, -2);
        textParams.leftMargin = 14; textParams.topMargin = 10;
        card.addView(text, textParams);
        item.addView(card, new FrameLayout.LayoutParams(-1, 120));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, 126);
        itemParams.bottomMargin = 2;
        list.addView(item, itemParams);
        activity.setContentView(window);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 480, 800);
    }

    /** Translucent pixels round through premultiplied storage. */
    private static void assertColor(int expected, int actual) {
        for (int shift = 0; shift < 32; shift += 8) {
            assertEquals(Integer.toHexString(actual), (expected >>> shift) & 0xff, (actual >>> shift) & 0xff, 3);
        }
    }

    @Test public void readsTheNativeBackdropCardsAndTextInsteadOfThemeDefaults() {
        nativeClipboard();
        ClipboardSearchStyle style = ClipboardSearchStyle.capture(list, null);
        assertColor(0x99202124, style.panelColor); // Translucent, and not the window's red.
        assertEquals(0xff3c4043, style.cardColor);
        assertEquals(16f, style.cardRadius, 3f);
        assertEquals(0xffe8eaed, style.textColor);
        assertEquals(30f, style.textSize, 0.01f);
        assertEquals(3, style.maxLines);
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals(Math.max(Math.round(4 * density), 14), style.cardPaddingH);
        assertEquals(Math.max(Math.round(4 * density), 10), style.cardPaddingV);
        assertEquals(Math.max(Math.round(2 * density), 8), style.cardGap);
    }

    @Test public void surfaceUsesTheCapturedLookWithoutUnderlineOrOpaqueThemeBackground() {
        nativeClipboard();
        ClipboardSearchStyle style = ClipboardSearchStyle.capture(list, null);
        EditText field = new EditText(activity);
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, field, 400,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { }
                    public void onClose() { }
                }, style);
        assertColor(0x99202124, ((ColorDrawable) surface.getBackground()).getColor());
        assertNull("no platform underline", field.getBackground());
        assertNotNull(((View) field.getParent()).getBackground()); // Header pill.
        assertNull(surface.results.getDivider());
        assertTrue(surface.close.getDrawable() instanceof ClipboardSearchSurface.BackIcon);
        surface.setClips(List.of(new ClipboardSearchSurface.Clip(1, ClipboardSearchController.Section.RECENT, "alpha")));
        TextView row = (TextView) surface.results.getAdapter().getView(0, null, surface.results);
        assertEquals(0xffe8eaed, row.getCurrentTextColor());
        assertEquals(30f, row.getTextSize(), 0.01f);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 80, android.graphics.Bitmap.Config.ARGB_8888);
        row.getBackground().setBounds(0, 0, 200, 80);
        row.getBackground().draw(new android.graphics.Canvas(bitmap));
        assertEquals(0xff3c4043, bitmap.getPixel(100, 40)); // Card body.
        assertEquals(0, Color.alpha(bitmap.getPixel(0, style.cardGap / 2))); // Rounded corner.
        bitmap.recycle();
        surface.close();
    }

    @Test public void cardMeasurementIsReusedOnlyWhileTheNativeCardRendersTheSame() {
        nativeClipboard();
        ClipboardSearchStyle first = ClipboardSearchStyle.capture(list, null);
        ClipboardSearchStyle again = ClipboardSearchStyle.capture(list, null);
        assertEquals(first.cardColor, again.cardColor);
        assertEquals(first.cardRadius, again.cardRadius, 0f);
        assertEquals(first.cardGap, again.cardGap);
        // Same drawable instance recolored by a theme change: must be measured again.
        View card = ((android.view.ViewGroup) list.getChildAt(1)).getChildAt(0);
        ((GradientDrawable) card.getBackground()).setColor(0xff8ab4f8);
        ClipboardSearchStyle recolored = ClipboardSearchStyle.capture(list, null);
        assertEquals(0xff8ab4f8, recolored.cardColor);
    }
}
