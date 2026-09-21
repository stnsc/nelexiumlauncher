package com.example.nelexiumlauncher

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateSigningTest {
    @Suppress("DEPRECATION")
    @Test
    fun downloadedArchiveRetainsCurrentSigningCertificates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = UpdateManager.signingCertificateFlags()
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val partial = File(context.cacheDir, "signing-test.apk.part")
        try {
            File(context.applicationInfo.sourceDir).copyTo(partial, overwrite = true)
            val archive = context.packageManager.getPackageArchiveInfo(partial.path, flags)
            assertNotNull("Archive metadata", archive)
            if (Build.VERSION.SDK_INT >= 28) {
                assertNotNull("Installed signing info", installed.signingInfo)
                assertNotNull("Downloaded archive signing info", archive!!.signingInfo)
                assertEquals(installed.signingInfo!!.apkContentsSigners.toSet(), archive.signingInfo!!.apkContentsSigners.toSet())
            } else {
                assertNotNull("Downloaded archive signatures", archive!!.signatures)
                assertEquals(installed.signatures!!.toSet(), archive.signatures!!.toSet())
            }
        } finally {
            partial.delete()
        }
    }
}
