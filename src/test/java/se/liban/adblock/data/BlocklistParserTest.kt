package se.liban.adblock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BlocklistParserTest {
    @Test
    fun catalogIdsAreUniqueAndTheDefaultExists() {
        val ids = BlocklistCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("stevenblack-unified", BlocklistCatalog.DEFAULT_ID)
        assertEquals(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            BlocklistCatalog.require(null).url
        )
        assertEquals(BlocklistCatalog.DEFAULT_ID, BlocklistCatalog.require("missing").id)
    }

    @Test
    fun requestedProvidersAreSelectable() {
        val names = BlocklistCatalog.all.map { "${it.provider} ${it.name}" }.toSet()
        listOf(
            "HaGeZi Light",
            "HaGeZi Normal",
            "HaGeZi Pro",
            "HaGeZi Pro++",
            "HaGeZi Ultimate",
            "HaGeZi TIF",
            "StevenBlack Unified",
            "OISD Big",
            "OISD Small",
            "AdGuard DNS filter",
            "1Hosts Lite",
            "1Hosts Xtra",
            "URLhaus Malware hosts",
            "Phishing Army Phishing hosts",
            "Dandelion Sprout Anti-Malware"
        ).forEach { assertTrue(it, it in names) }
        assertTrue(BlocklistCatalog.all.none { it.id.contains("pro") && it.provider == "1Hosts" && it.name == "Pro" })
    }

    @Test
    fun hostsWildcardAndAdblockRulesBecomeDomains() {
        val raw = File.createTempFile("blocklist", ".txt")
        val hosts = File.createTempFile("hosts", ".txt")
        val domains = File.createTempFile("domains", ".txt")
        try {
            raw.writeText(
                """
                # comment
                127.0.0.1 localhost
                0.0.0.0 ads.example.com
                127.0.0.1 Track.Example.net
                0.0.0.0 1.2.3.4
                """.trimIndent()
            )
            val count = BlocklistParser.writeHosts(raw, ListFormat.HOSTS, hosts, domains)
            assertEquals(2, count)
            assertTrue(domains.readText().contains("ads.example.com"))
            assertTrue(domains.readText().contains("track.example.net"))
            assertTrue(hosts.readText().startsWith(BlocklistParser.HOSTS_HEADER))

            raw.writeText("*.Ads.Example.com\n# note\n*.1.2.3.4\n")
            assertEquals(1, BlocklistParser.writeHosts(raw, ListFormat.WILDCARD, hosts, domains))
            assertEquals("ads.example.com\n", domains.readText())

            raw.writeText(
                """
                ! comment
                ||ads.example^
                ||track.example.com^
                @@||track.example.com^
                ||1.2.3.4^
                ||ads.example.com^${'$'}third-party
                example.com##.banner
                """.trimIndent()
            )
            assertEquals(1, BlocklistParser.writeHosts(raw, ListFormat.ADBLOCK, hosts, domains))
            assertEquals("ads.example\n", domains.readText())
            assertFalse(domains.readText().contains("track.example.com"))
        } finally {
            raw.delete()
            hosts.delete()
            domains.delete()
        }
    }
}
