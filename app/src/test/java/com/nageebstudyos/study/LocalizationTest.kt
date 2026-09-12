package com.nageebstudyos.study

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class LocalizationTest {
    private fun arabicContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration(context.resources.configuration).apply {
                setLocale(Locale("ar"))
                setLayoutDirection(Locale("ar"))
            }
        return context.createConfigurationContext(config)
    }

    @Test
    fun appIsArabicWithRtlLayout() {
        val ar = arabicContext()
        assertEquals("المواد", ar.getString(R.string.subjects))
        assertEquals("حفظ", ar.getString(R.string.save))
        assertEquals("التركيز", ar.getString(R.string.focus))
        assertEquals("المخطط", ar.getString(R.string.planner))
        assertEquals("التحليلات", ar.getString(R.string.analytics))
        assertEquals(1, ar.resources.configuration.layoutDirection)
    }

    @Test
    fun v2StringsAreTranslated() {
        val ar = arabicContext()
        assertNotNull(ar.getString(R.string.start_focus))
        assertNotNull(ar.getString(R.string.add_review))
        assertNotNull(ar.getString(R.string.session_result))
        assertNotNull(ar.getString(R.string.streak_explained))
    }

    @Test
    fun applicationHasNoNetworkPermissionAndKeepsOfflinePolicy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        // Local notifications and foreground focus service are the only new capabilities.
        assertTrue(permissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(permissions.contains("android.permission.VIBRATE"))
        assertTrue(permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }
}
