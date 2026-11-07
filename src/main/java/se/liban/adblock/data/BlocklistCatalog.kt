package se.liban.adblock.data

/**
 * One downloadable block list. The id is stored in preferences.
 * Formats match what the local hosts file and VPN DNS filter can enforce:
 * an exact name in hosts mode, and that name plus its subdomains in VPN mode.
 */
data class Blocklist(
    val id: String,
    val provider: String,
    val name: String,
    val detail: String,
    val url: String,
    val format: ListFormat,
    val minDomains: Int
) {
    val label: String get() = "$provider $name"
}

enum class ListFormat {
    HOSTS,
    WILDCARD,
    ADBLOCK
}

object BlocklistCatalog {
    const val DEFAULT_ID = "stevenblack-unified"

    private const val HAGEZI = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock"
    private const val STEVEN = "https://raw.githubusercontent.com/StevenBlack/hosts/master"

    val all: List<Blocklist> = listOf(
        Blocklist(
            id = "hagezi-light",
            provider = "HaGeZi",
            name = "Light",
            detail = "Basic ads, trackers, and some malware. Smallest Multi list.",
            url = "$HAGEZI/light.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "hagezi-normal",
            provider = "HaGeZi",
            name = "Normal",
            detail = "Everyday ads, trackers, phishing, and malware.",
            url = "$HAGEZI/multi.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "hagezi-pro",
            provider = "HaGeZi",
            name = "Pro",
            detail = "Recommended extended protection. False positives are uncommon.",
            url = "$HAGEZI/pro.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "hagezi-pro-plus",
            provider = "HaGeZi",
            name = "Pro++",
            detail = "More aggressive than Pro. A few legitimate sites can break.",
            url = "$HAGEZI/pro.plus.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "hagezi-ultimate",
            provider = "HaGeZi",
            name = "Ultimate",
            detail = "Strictest Multi list. Can break apps that rely on trackers.",
            url = "$HAGEZI/ultimate.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "hagezi-tif",
            provider = "HaGeZi",
            name = "TIF",
            detail = "Threat intelligence only: malware, phishing, and scam domains. Very large.",
            url = "$HAGEZI/tif.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = DEFAULT_ID,
            provider = "StevenBlack",
            name = "Unified",
            detail = "Base hosts file for adware and malware.",
            url = "$STEVEN/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "stevenblack-fakenews",
            provider = "StevenBlack",
            name = "Unified + fake news",
            detail = "Base list plus the fake-news extension.",
            url = "$STEVEN/alternates/fakenews/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "stevenblack-gambling",
            provider = "StevenBlack",
            name = "Unified + gambling",
            detail = "Base list plus the gambling extension.",
            url = "$STEVEN/alternates/gambling/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "stevenblack-social",
            provider = "StevenBlack",
            name = "Unified + social",
            detail = "Base list plus the social-network extension.",
            url = "$STEVEN/alternates/social/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "stevenblack-fakenews-gambling-social",
            provider = "StevenBlack",
            name = "Unified + fake news, gambling, social",
            detail = "Base list with the three non-adult extensions combined.",
            url = "$STEVEN/alternates/fakenews-gambling-social/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "stevenblack-full",
            provider = "StevenBlack",
            name = "Unified + all extensions",
            detail = "Base list plus fake news, gambling, adult, and social extensions.",
            url = "$STEVEN/alternates/fakenews-gambling-porn-social/hosts",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "oisd-big",
            provider = "OISD",
            name = "Big",
            detail = "Full list for ads, malware, phishing, and tracking. Includes subdomains.",
            url = "https://big.oisd.nl/domainswild",
            format = ListFormat.WILDCARD,
            minDomains = 10_000
        ),
        Blocklist(
            id = "oisd-small",
            provider = "OISD",
            name = "Small",
            detail = "Smaller ad-focused list. Big already includes it.",
            url = "https://small.oisd.nl/domainswild",
            format = ListFormat.WILDCARD,
            minDomains = 5_000
        ),
        Blocklist(
            id = "adguard-dns",
            provider = "AdGuard",
            name = "DNS filter",
            detail = "AdGuard's DNS filter, built from EasyList, EasyPrivacy, and AdGuard lists.",
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            format = ListFormat.ADBLOCK,
            minDomains = 10_000
        ),
        Blocklist(
            id = "1hosts-lite",
            provider = "1Hosts",
            name = "Lite",
            detail = "Smaller 1Hosts profile for ads and trackers.",
            url = "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/hosts.txt",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "1hosts-xtra",
            provider = "1Hosts",
            name = "Xtra",
            detail = "Full 1Hosts profile. Replaces the retired Pro list.",
            url = "https://raw.githubusercontent.com/badmojr/1Hosts/master/Xtra/hosts.txt",
            format = ListFormat.HOSTS,
            minDomains = 10_000
        ),
        Blocklist(
            id = "urlhaus",
            provider = "URLhaus",
            name = "Malware hosts",
            detail = "abuse.ch feed of hostnames recently used to deliver malware.",
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            format = ListFormat.HOSTS,
            minDomains = 20
        ),
        Blocklist(
            id = "phishing-army",
            provider = "Phishing Army",
            name = "Phishing hosts",
            detail = "Community phishing domains in hosts format.",
            url = "https://phishing.army/download/phishing-army-hosts.txt",
            format = ListFormat.HOSTS,
            minDomains = 1_000
        ),
        Blocklist(
            id = "dandelion-antimalware",
            provider = "Dandelion Sprout",
            name = "Anti-Malware",
            detail = "Actively maintained malware and scam hosts list.",
            url = "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Alternate%20versions%20Anti-Malware%20List/AntiMalwareHosts.txt",
            format = ListFormat.HOSTS,
            minDomains = 200
        )
    )

    fun require(id: String?): Blocklist =
        all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}
