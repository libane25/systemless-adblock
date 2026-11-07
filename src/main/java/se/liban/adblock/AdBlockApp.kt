package se.liban.adblock

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import se.liban.adblock.data.SettingsRepository
import se.liban.adblock.ui.theme.nightMode

class AdBlockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = SettingsRepository(this).current().themeMode
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode())
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Shell.enableVerboseLogging = debuggable
        // Must be set before the main shell is created. Mount master makes bind
        // mounts visible outside this process on Magisk and compatible su.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(60)
                .setContext(this)
        )
    }
}
