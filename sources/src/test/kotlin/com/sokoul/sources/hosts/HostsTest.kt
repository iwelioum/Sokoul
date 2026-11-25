package com.sokoul.sources.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which host a link belongs to, and which links are refused before anything is asked. */
class HostsTest {
    private fun hostOf(url: String) = Hosts.forUrl(url)?.host

    @Test fun linksGoToTheirHostByDomainMirrorAndSubDomain() {
        assertEquals("Voe", hostOf("https://voe.sx/e/abc"))
        assertEquals("Voe", hostOf("https://jilliandescribecompany.com/e/abc"))
        assertEquals("Voe", hostOf("https://voeunblock3.com/e/abc"))
        assertEquals("Vidmoly", hostOf("https://vidmoly.to/embed-abc.html"))
        assertEquals("Vidmoly", hostOf("https://www.ansembed.net/embed-abc.html"))
        assertEquals("Sibnet", hostOf("https://video.sibnet.ru/shell.php?videoid=1"))
        assertEquals("Uqload", hostOf("https://uqload.cx/embed-abc.html"))
        assertEquals("Uqload", hostOf("https://www.uqload.bz/abc.html"))
        assertEquals("DoodStream", hostOf("https://d0000d.com/e/abc"))
        assertEquals("DoodStream", hostOf("https://dood.to/d/abc"))
        assertEquals("DoodStream", hostOf("https://playmogo.com/e/abc"))
        assertEquals("Veev", hostOf("https://doods.to/e/abc"))
        assertEquals("Veev", hostOf("https://veev.to/e/abc"))
        assertEquals("LuluStream", hostOf("https://luluvdo.com/e/abc"))
        assertEquals("LuluStream", hostOf("https://lulu.st/e/abc"))
        assertEquals("LuluStream", hostOf("https://cdn1.site/e/abc"))
        assertEquals("Vidara", hostOf("https://vidara.so/e/abc"))
        assertEquals("Fsvid", hostOf("https://fsvid.lol/embed-abc.html"))
        assertEquals("Vidzy", hostOf("https://vidzy.org/embed-abc.html"))
        assertEquals("SeekStreaming", hostOf("https://embed4me.com/#abc123"))
        assertEquals("SeekStreaming", hostOf("https%3A%2F%2Fembed4me.com%2F%23abc123"))
    }

    @Test fun unknownAndUnsafeLinksHaveNoHost() {
        assertNull(hostOf("https://example.com/e/abc"))
        assertNull(hostOf("https://notvoe.example/e/abc"))
        assertNull(hostOf("ftp://voe.sx/e/abc"))
        assertNull(hostOf("https://user:pw@voe.sx/e/abc"))
        assertNull(hostOf("https://93.184.216.34/e/abc"))
        assertNull(hostOf("https://[::1]/e/abc"))
        assertNull(hostOf("https://localhost/#abc"))
        assertNull(hostOf("https://voe.sx:8443/e/abc"))
        assertNull(hostOf("https://voe.sx/e/a b"))
        assertNull(hostOf("not a url"))
        // A root page with a query is not a SeekStreaming embed.
        assertNull(hostOf("https://embed4me.com/?x=1#abc"))
    }
}
