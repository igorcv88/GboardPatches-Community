package dev.jason.gboardpatches.extension.flagsettings;

import android.content.Context;
import android.os.UserManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowUserManager;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class GboardFlagRuntimeContextDirectBootTest {
    @Test
    public void preferencesAreAvailableOnlyAfterUnlock() {
        Context context = RuntimeEnvironment.getApplication();
        UserManager manager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        ShadowUserManager shadow = Shadow.extract(manager);
        shadow.setUserUnlocked(false);
        Assert.assertNull(GboardFlagRuntimeContext.preferencesOrNull(context));
        shadow.setUserUnlocked(true);
        Assert.assertNotNull(GboardFlagRuntimeContext.preferencesOrNull(context));
    }
}
