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
    @Test
    fun arabicResourcesSetRtlAndTranslateCoreStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration(context.resources.configuration).apply {
                setLocale(Locale("ar"))
                setLayoutDirection(Locale("ar"))
            }
        val ar = context.createConfigurationContext(config)
        assertEquals("المواد", ar.getString(R.string.subjects))
        assertEquals("حفظ", ar.getString(R.string.save))
        assertEquals(1, ar.resources.configuration.layoutDirection)
    }

    @Test
    fun englishResourcesSetLtrAndTranslateCoreStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.ENGLISH)
                setLayoutDirection(Locale.ENGLISH)
            }
        val en = context.createConfigurationContext(config)
        assertEquals("Subjects", en.getString(R.string.subjects))
        assertEquals("Save", en.getString(R.string.save))
        assertEquals(0, en.resources.configuration.layoutDirection)
    }

    @Test
    fun applicationHasNoNetworkOrBroadStoragePermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
    }
}
