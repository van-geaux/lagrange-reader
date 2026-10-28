package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCoverEntryTest {
    private fun epub(opf: String, vararg images: String): Pair<List<String>, (String) -> String?> {
        val files = mapOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to opf
        )
        return (files.keys + images).toList() to { name: String -> files[name] }
    }

    @Test
    fun `epub cover-image property wins`() {
        val (names, read) = epub(
            """<package><manifest>
                <item id="a" href="Images/first.jpg" media-type="image/jpeg"/>
                <item id="cov" href="Images/Cover%20Art.png" media-type="image/png" properties="nav cover-image"/>
            </manifest></package>""",
            "OEBPS/Images/first.jpg", "OEBPS/Images/Cover Art.png"
        )
        assertEquals("OEBPS/Images/Cover Art.png", selectLocalCoverEntry(names, read))
    }

    @Test
    fun `epub2 meta cover names the item`() {
        val (names, read) = epub(
            """<package><metadata><meta content="img2" name="cover"/></metadata><manifest>
                <item id="img1" href="../art/one.jpg" media-type="image/jpeg"/>
                <item href="../art/two.jpg" id="img2" media-type="image/jpeg"/>
            </manifest></package>""",
            "art/one.jpg", "art/two.jpg"
        )
        assertEquals("art/two.jpg", selectLocalCoverEntry(names, read))
    }

    @Test
    fun `epub without cover markup falls back to a cover-named image then the first image`() {
        val named = epub(
            """<package><manifest>
                <item id="i1" href="a.jpg" media-type="image/jpeg"/>
                <item id="i2" href="my-cover.jpg" media-type="image/jpeg"/>
            </manifest></package>""",
            "OEBPS/a.jpg", "OEBPS/my-cover.jpg"
        )
        assertEquals("OEBPS/my-cover.jpg", selectLocalCoverEntry(named.first, named.second))
        val plain = epub(
            """<package><manifest><item id="t" href="t.xhtml" media-type="application/xhtml+xml"/>
                <item id="i1" href="z.png" media-type="image/png"/></manifest></package>""",
            "OEBPS/z.png"
        )
        assertEquals("OEBPS/z.png", selectLocalCoverEntry(plain.first, plain.second))
    }

    @Test
    fun `epub hrefs keep literal plus signs and decode entities and percent escapes`() {
        val (names, read) = epub(
            """<package><manifest>
                <item id="c" href="Images/Cover+Art%20%26%20Map.jpg" media-type="image/jpeg" properties="cover-image"/>
            </manifest></package>""",
            "OEBPS/Images/Cover+Art & Map.jpg", "OEBPS/Images/Cover Art & Map.jpg"
        )
        assertEquals("OEBPS/Images/Cover+Art & Map.jpg", selectLocalCoverEntry(names, read))
        val entity = epub(
            """<package><manifest>
                <item id="c" href="Images/Tom &amp; Jerry.png" media-type="image/png" properties="cover-image"/>
            </manifest></package>""",
            "OEBPS/Images/Tom & Jerry.png"
        )
        assertEquals("OEBPS/Images/Tom & Jerry.png", selectLocalCoverEntry(entity.first, entity.second))
    }

    @Test
    fun `percent decoding keeps characters outside the basic plane`() {
        assertEquals("Images/\uD83D\uDE00 front+back.jpg", percentDecodePath("Images/\uD83D\uDE00%20front+back.jpg"))
        assertEquals("plain+name.jpg", percentDecodePath("plain+name.jpg"))
        assertEquals("100%.jpg", percentDecodePath("100%.jpg"))
    }

    @Test
    fun `comic archives use the first page in natural order`() {
        val names = listOf("__MACOSX/._001.jpg", "Vol 1/page10.jpg", "Vol 1/page2.jpg", "Vol 1/.hidden.jpg", "info.txt")
        assertEquals("Vol 1/page2.jpg", selectLocalCoverEntry(names) { null })
    }

    @Test
    fun `archives without images have no cover`() {
        assertNull(selectLocalCoverEntry(listOf("readme.txt", "data.xml")) { null })
    }
}
