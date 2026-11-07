package se.liban.adblock.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class InstallResult(
    val blockedDomains: Int,
    val outcome: ApplyOutcome
)

class HostsManager(
    context: Context,
    private val rootExecutor: RootExecutor,
    private val client: OkHttpClient
) {
    private val appContext = context.applicationContext
    private val stagingFile = File(appContext.cacheDir, "hosts.validated")

    suspend fun installLatest(onProgress: (Float?) -> Unit): InstallResult = withContext(Dispatchers.IO) {
        ensureNetwork()
        onProgress(null)
        try {
            val prepared = downloadBlocklist(onProgress, domainFile = null)
            val outcome = rootExecutor.applyFile(prepared.file, rootExecutor.detectTarget())
            InstallResult(prepared.blockedDomains, outcome)
        } finally {
            stagingFile.delete()
        }
    }

    suspend fun saveVpnBlocklist(onProgress: (Float?) -> Unit): Int = withContext(Dispatchers.IO) {
        ensureNetwork()
        onProgress(null)
        val temp = File(appContext.filesDir, "blocked-domains.txt.tmp")
        try {
            val prepared = downloadBlocklist(onProgress, temp)
            val destination = blocklistFile(appContext)
            destination.delete()
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            prepared.blockedDomains
        } finally {
            stagingFile.delete()
            temp.delete()
        }
    }

    suspend fun revertToDefault(): InstallResult = withContext(Dispatchers.IO) {
        stagingFile.writeText(DEFAULT_HOSTS)
        try {
            val outcome = rootExecutor.applyFile(stagingFile, rootExecutor.detectTarget())
            InstallResult(blockedDomains = 0, outcome = outcome)
        } finally {
            stagingFile.delete()
        }
    }

    private fun ensureNetwork() {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        if (manager.activeNetwork == null) {
            throw UserMessageException("No network connection")
        }
        if (SettingsRepository(appContext).current().wifiOnly && !appContext.isWifiOrEthernet()) {
            throw UserMessageException("Update is set to Wi-Fi only")
        }
    }

    private fun downloadBlocklist(onProgress: (Float?) -> Unit, domainFile: File?): PreparedHosts {
        val source = BlocklistCatalog.require(SettingsRepository(appContext).current().blocklistId)
        val raw = File(appContext.cacheDir, "blocklist-raw.tmp")
        try {
            downloadRaw(source.url, raw, onProgress)
            if (looksLikeHtml(raw)) {
                throw UserMessageException("Download was not a block list")
            }
            val blocked = BlocklistParser.writeHosts(raw, source.format, stagingFile, domainFile)
            if (blocked < source.minDomains || stagingFile.length() < MIN_BYTES) {
                throw UserMessageException("Block list looks incomplete ($blocked domains)")
            }
            validateHeader(stagingFile)
            onProgress(1f)
            return PreparedHosts(stagingFile, blocked)
        } finally {
            raw.delete()
        }
    }

    private fun downloadRaw(url: String, destination: File, onProgress: (Float?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserMessageException("Download failed (${response.code})")
            }
            val body = response.body ?: throw UserMessageException("Download was empty")
            val total = body.contentLength()
            var read = 0L
            var lastReport = 0L
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0L && read - lastReport >= REPORT_BYTES) {
                            lastReport = read
                            onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 0.9f))
                        }
                    }
                }
            }
        }
    }

    private fun looksLikeHtml(file: File): Boolean {
        val head = file.inputStream().use { input ->
            val sniff = ByteArray(80)
            val count = input.read(sniff)
            if (count <= 0) "" else String(sniff, 0, count, Charsets.UTF_8)
        }
        return head.contains("<html", ignoreCase = true) || head.contains("<!DOCTYPE", ignoreCase = true)
    }

    private fun validateHeader(file: File) {
        val head = file.inputStream().use { input ->
            val sniff = ByteArray(80)
            val count = input.read(sniff)
            if (count <= 0) "" else String(sniff, 0, count, Charsets.UTF_8)
        }
        if (!head.startsWith(DEFAULT_HOSTS)) {
            throw UserMessageException("Hosts file is missing localhost entries")
        }
    }

    private data class PreparedHosts(val file: File, val blockedDomains: Int)

    companion object {
        fun blocklistFile(context: Context): File = File(context.filesDir, "blocked-domains.txt")

        const val DEFAULT_HOSTS = BlocklistParser.HOSTS_HEADER
        const val MIN_BYTES = 1_000L
        const val REPORT_BYTES = 256 * 1024L
    }
}
