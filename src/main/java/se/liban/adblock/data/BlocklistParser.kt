package se.liban.adblock.data

import java.io.File

internal sealed class ListAction {
    data class Block(val host: String) : ListAction()
    data class Allow(val host: String) : ListAction()
    data object Ignore : ListAction()
}

internal object BlocklistParser {
    const val HOSTS_HEADER = "127.0.0.1 localhost\n::1 localhost\n"

    fun writeHosts(raw: File, format: ListFormat, hostsOut: File, domainsOut: File?): Int {
        val allowed = if (format == ListFormat.ADBLOCK) collectAllows(raw) else emptySet()
        val seen = HashSet<String>(16_384)
        hostsOut.bufferedWriter().use { hosts ->
            val domains = domainsOut?.bufferedWriter()
            try {
                hosts.write(HOSTS_HEADER)
                raw.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val action = interpret(line, format)
                        if (action is ListAction.Block &&
                            action.host !in allowed &&
                            seen.add(action.host)
                        ) {
                            hosts.write("0.0.0.0 ")
                            hosts.write(action.host)
                            hosts.write("\n")
                            domains?.write(action.host)
                            domains?.write("\n")
                        }
                    }
                }
            } finally {
                domains?.close()
            }
        }
        return seen.size
    }

    internal fun interpret(line: String, format: ListFormat): ListAction {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return ListAction.Ignore
        }
        return when (format) {
            ListFormat.HOSTS -> hosts(trimmed)
            ListFormat.WILDCARD -> wildcard(trimmed)
            ListFormat.ADBLOCK -> adblock(trimmed)
        }
    }

    private fun collectAllows(raw: File): Set<String> {
        val allowed = HashSet<String>()
        raw.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val action = interpret(line, ListFormat.ADBLOCK)
                if (action is ListAction.Allow) allowed.add(action.host)
            }
        }
        return allowed
    }

    private fun hosts(line: String): ListAction {
        val match = HOSTS_LINE.find(line) ?: return ListAction.Ignore
        return block(match.groupValues[1])
    }

    private fun wildcard(line: String): ListAction {
        val domain = line.removePrefix("*.").substringBefore('#').trim()
        return block(domain)
    }

    private fun adblock(line: String): ListAction {
        val allow = ALLOW_RULE.find(line)
        if (allow != null) {
            val host = normalize(allow.groupValues[1])
            return if (host == null) ListAction.Ignore else ListAction.Allow(host)
        }
        val block = BLOCK_RULE.find(line) ?: return ListAction.Ignore
        return block(block.groupValues[1])
    }

    private fun block(rawHost: String): ListAction {
        val host = normalize(rawHost) ?: return ListAction.Ignore
        return ListAction.Block(host)
    }

    private fun normalize(rawHost: String): String? {
        val host = rawHost.trim().trim('.').lowercase()
        if (host in IGNORED_HOSTS || !HOST.matches(host)) return null
        return host
    }

    private val HOSTS_LINE = Regex("""^(?:0\.0\.0\.0|127\.0\.0\.1)\s+(\S+)""")
    private val BLOCK_RULE = Regex("""^\|\|([a-z0-9.-]+)\^$""", RegexOption.IGNORE_CASE)
    private val ALLOW_RULE = Regex("""^@@\|\|([a-z0-9.-]+)\^$""", RegexOption.IGNORE_CASE)
    private val HOST = Regex("""^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
    private val IGNORED_HOSTS = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "0.0.0.0",
        "ip6-localhost",
        "ip6-loopback"
    )
}
