package se.liban.adblock.data

import android.content.Context
import android.util.Base64
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class HostsTargetKind {
    MAGISK_HOSTS,
    SYSTEMLESS_MODULE,
    BIND_MOUNT
}

fun HostsTargetKind.displayLabel(): String = when (this) {
    HostsTargetKind.MAGISK_HOSTS -> "Magisk systemless hosts"
    HostsTargetKind.SYSTEMLESS_MODULE -> "systemless_adblock module"
    HostsTargetKind.BIND_MOUNT -> "Bind mount until reboot"
}

data class HostsTarget(
    val kind: HostsTargetKind,
    val path: String
)

data class ApplyOutcome(
    val target: HostsTarget,
    val mountedNow: Boolean
)

class RootExecutor(context: Context) {
    private val appContext = context.applicationContext

    suspend fun peekGranted(): Boolean = withContext(Dispatchers.IO) {
        Shell.isAppGrantedRoot() == true
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("id").exec()
            Shell.getShell().isRoot
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoShellException) {
            false
        }
    }

    suspend fun detectTarget(): HostsTarget = withContext(Dispatchers.IO) {
        val result = exec(DETECT_SCRIPT)
        val line = result.out.firstOrNull { it == "BIND" || it.startsWith("MODULE:") }
            ?: throw UserMessageException("Could not choose a hosts target")
        if (!result.isSuccess && line.isEmpty()) {
            throw UserMessageException(failure("Could not inspect root modules", result))
        }
        when {
            line == "BIND" -> HostsTarget(
                kind = HostsTargetKind.BIND_MOUNT,
                path = bindHostsFile().absolutePath
            )
            line.endsWith("/modules/hosts/system/etc/hosts") -> HostsTarget(
                kind = HostsTargetKind.MAGISK_HOSTS,
                path = line.removePrefix("MODULE:")
            )
            else -> HostsTarget(
                kind = HostsTargetKind.SYSTEMLESS_MODULE,
                path = line.removePrefix("MODULE:")
            )
        }
    }

    /**
     * Copies [source] onto the live hosts target, sets mode 644, and bind-mounts it
     * over /system/etc/hosts when that mount is not already in place.
     */
    suspend fun applyFile(source: File, target: HostsTarget): ApplyOutcome = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() <= 0L) {
            throw UserMessageException("Hosts file is empty")
        }
        val live = if (target.kind == HostsTargetKind.BIND_MOUNT) {
            bindHostsFile().absolutePath
        } else {
            target.path
        }
        val wrote = withRootReadable(source) {
            exec(writeScript(source.absolutePath, live, source.length()))
        }
        val applied = if (wrote.isSuccess) {
            wrote
        } else {
            exec("rm -f ${shQuote("$live.tmp")}")
            writeWithBase64(source, live, source.length())
        }
        if (!applied.isSuccess) {
            exec("rm -f ${shQuote("$live.tmp")}")
            throw UserMessageException(failure("Could not write hosts file", applied))
        }
        val mounted = exec(mountScript(live))
        if (!mounted.isSuccess && target.kind == HostsTargetKind.BIND_MOUNT) {
            throw UserMessageException(failure("Bind mount failed", mounted))
        }
        ApplyOutcome(target.copy(path = live), mountedNow = mounted.isSuccess)
    }

    private fun bindHostsFile(): File = File(appContext.filesDir, "hosts")

    private fun writeWithBase64(source: File, target: String, bytes: Long): Shell.Result {
        val parent = shQuote(File(target).parent ?: "/")
        val tmp = shQuote("$target.tmp")
        val live = shQuote(target)
        val setup = exec("mkdir -p $parent && : > $tmp && chmod 644 $tmp")
        if (!setup.isSuccess) return setup
        source.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                val chunk = exec("printf %s ${shQuote(encoded)} | base64 -d >> $tmp")
                if (!chunk.isSuccess) return chunk
            }
        }
        val size = "\"${'$'}(wc -c < $tmp | tr -d '[:space:]')\""
        return exec("[ $size -eq $bytes ] && cat $tmp > $live && chmod 644 $live && rm -f $tmp")
    }

    private fun exec(command: String): Shell.Result = Shell.cmd(command).exec()

    private inline fun <T> withRootReadable(file: File, block: () -> T): T {
        val dirs = listOf(
            appContext.cacheDir,
            appContext.filesDir,
            File(appContext.applicationInfo.dataDir)
        )
        dirs.forEach { it.setExecutable(true, false) }
        file.setReadable(true, false)
        return try {
            block()
        } finally {
            file.setReadable(true, true)
            dirs.forEach { it.setExecutable(true, true) }
        }
    }

    private fun writeScript(staging: String, target: String, bytes: Long): String {
        val source = shQuote(staging)
        val live = shQuote(target)
        val tmp = shQuote("$target.tmp")
        val parent = shQuote(File(target).parent ?: "/")
        val size = "\"${'$'}(wc -c < $tmp | tr -d '[:space:]')\""
        return "mkdir -p $parent" +
            " && cat $source > $tmp" +
            " && chmod 644 $tmp" +
            " && [ $size -eq $bytes ]" +
            " && cat $tmp > $live" +
            " && chmod 644 $live" +
            " && rm -f $tmp"
    }

    private fun mountScript(target: String): String {
        val live = shQuote(target)
        val pattern = shQuote("$target /system/etc/hosts")
        return "if grep -F -q $pattern /proc/mounts; then :;" +
            " else mount -o bind $live /system/etc/hosts; fi"
    }

    private fun failure(prefix: String, result: Shell.Result): String {
        val detail = (result.err + result.out).joinToString(" ").trim()
        if (detail.isEmpty()) return prefix
        return "$prefix: ${detail.take(180)}"
    }

    private companion object {
        const val DETECT_SCRIPT =
            "if [ -d /data/adb/modules/hosts ] && [ ! -f /data/adb/modules/hosts/disable ] && [ ! -f /data/adb/modules/hosts/remove ]; then " +
                "echo MODULE:/data/adb/modules/hosts/system/etc/hosts; " +
                "elif [ -d /data/adb/modules/systemless_adblock ] && [ ! -f /data/adb/modules/systemless_adblock/disable ] && [ ! -f /data/adb/modules/systemless_adblock/remove ]; then " +
                "echo MODULE:/data/adb/modules/systemless_adblock/system/etc/hosts; " +
                "else echo BIND; fi"
    }
}

class UserMessageException(message: String) : Exception(message)

private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
