package com.getup.iClouDroid

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getup.iClouDroid.data.session.SessionManager
import com.getup.icloudcore.Icloudcore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEMPORARY DIAGNOSTIC — DELETE BEFORE RELEASE.
 *
 * Answers one question on a real account: what does CloudKit actually store on a `CPLMaster`
 * record, and does it include the EXIF/GPS metadata the photo-detail panel wants plus a
 * fingerprint for Phase 3 upload dedup?
 *
 * It runs as an instrumentation test rather than app code specifically so it can read the
 * encrypted session store (which normal code cannot do outside the app process) without adding
 * any debug-only backdoor to the shipping app.
 *
 * Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.getup.iClouDroid.RecordFieldsProbeTest
 *
 * Then read the output with:
 *   adb logcat -d | grep "RecordFieldsProbe"
 */
@RunWith(AndroidJUnit4::class)
class RecordFieldsProbeTest {

    private val tag = "RecordFieldsProbe"

    @Test
    fun probeMasterRecordFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionManager = SessionManager(context)

        val cookie = sessionManager.getCookieString()
        if (cookie.isNullOrBlank()) {
            Log.e(tag, "No stored cookie - log in through the app first")
            return
        }
        val isChina = sessionManager.isChinaMainland()
        Log.i(tag, "Restoring session (isChina=$isChina, cookieLen=${cookie.length})")

        // Restore the Go session; the probe needs it for the CloudKit base URL and cookies.
        val initResult = Icloudcore.initSession(cookie, isChina)
        Log.i(tag, "initSession -> $initResult")

        // 1) Compact answer: which field keys exist, and the shape of resource values.
        val keys = Icloudcore.probeFieldKeys(1)
        Log.i(tag, "FIELD KEYS >>> $keys")

        // 2) Full answer: the verbatim lookup payload, for reading actual EXIF values.
        val raw = Icloudcore.probeRecordFields(1)
        Log.i(tag, "RAW RECORD >>> $raw")
    }
}
