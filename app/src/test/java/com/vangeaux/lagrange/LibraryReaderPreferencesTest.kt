package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import java.io.File

class LibraryReaderPreferencesTest {
    @Test
    fun `reader header adds only missing visible status bar space`() {
        assertEquals(24, readerHeaderTopMargin(0, 0, 24, 24, 0, statusBarVisible = true))
        assertEquals(0, readerHeaderTopMargin(24, 0, 24, 24, 24, statusBarVisible = true))
        assertEquals(24, readerHeaderTopMargin(0, 0, 0, 24, 24, statusBarVisible = true))
        assertEquals(0, readerHeaderTopMargin(48, 0, 24, 24, 48, statusBarVisible = true))
        assertEquals(0, readerHeaderTopMargin(0, 0, 24, 24, 24, statusBarVisible = false))
    }

    @Test
    fun `PDF and comic header reservation preserves hidden-header layout`() {
        assertEquals(30, readerHeaderReservedTopSpace(0, 30, 0, showHeader = true))
        assertEquals(54, readerHeaderReservedTopSpace(24, 30, 0, showHeader = true))
        assertEquals(48, readerHeaderReservedTopSpace(0, 30, 48, showHeader = true))
        assertEquals(48, readerHeaderReservedTopSpace(24, 30, 48, showHeader = false))
        assertEquals(0, readerHeaderReservedTopSpace(24, 30, 0, showHeader = false))
    }

    @Test
    fun `page progress is enabled by default and legacy profiles keep it enabled`() {
        assertTrue(LibraryReaderPreferences().showPageProgress)
        assertEquals(
            DEFAULT_READER_MARGIN_TAP_ZONE_EXCLUSION_PERCENT,
            LibraryReaderPreferences().marginTapZoneExclusionPercent
        )
        assertFalse(LibraryReaderPreferences().showHeader)
        assertFalse(LibraryReaderPreferences().showHeaderFooterBorders)

        val legacy = libraryReaderPreferencesFromStorage(
            """{"version":2,"profiles":{"library":{"theme":"dark"}}}"""
        ).getValue("library")

        assertTrue(legacy.showPageProgress)
        assertEquals(
            DEFAULT_READER_MARGIN_TAP_ZONE_EXCLUSION_PERCENT,
            legacy.marginTapZoneExclusionPercent
        )
        assertFalse(legacy.showHeader)
        assertFalse(legacy.showHeaderFooterBorders)
    }

    @Test
    fun `reader profile storage uses a versioned envelope`() {
        val profile = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
            volumeButtonPageNavigation = true,
            reverseVolumeButtonNavigation = true,
            marginTapZoneExclusionPercent = 65,
            theme = EpubReaderTheme.Dark,
            backgroundTheme = EpubReaderTheme.Light,
            customBackgroundColor = 0xFF123456.toInt(),
            useCustomBackground = true,
            customTextColor = 0xFFB8B8B8.toInt(),
            useCustomTextColor = true,
            fontFamily = EpubReaderFontFamily.OPEN_DYSLEXIC,
            fontScale = 1.3f,
            lineSpacing = 1.4f,
            wordSpacing = 0.4f,
            paragraphSpacing = 0.7f,
            preferServerProgressOnReopen = true,
            padding = EpubPaddingPercentages(10f, 20f, 30f, 40f),
            epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
            showHeader = true,
            pdfLayoutMode = ReaderLayoutMode.PAGINATED,
            pdfPageGapDp = 8f,
            invertPdfColors = true,
            comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
            comicPageGapDp = 24f
        )

        val storage = JSONObject(libraryReaderPreferencesStorageValue(mapOf("library" to profile)))

        assertTrue(storage.has("version"))
        assertTrue(storage.has("profiles"))
        assertEquals(profile, libraryReaderPreferencesFromStorage(storage.toString())["library"])
    }

    @Test
    fun `malformed profile does not erase valid profiles`() {
        val storage = JSONObject().apply {
            put("version", 1)
            put("profiles", JSONObject().apply {
                put("valid", JSONObject().apply {
                    put("theme", "dark")
                    put("fontFamily", "system_sans_serif")
                })
                put("malformed", "not-an-object")
            })
        }

        val decoded = libraryReaderPreferencesFromStorage(storage.toString())

        assertEquals(EpubReaderTheme.Dark, decoded.getValue("valid").theme)
        assertEquals(EpubReaderFontFamily.SYSTEM_SANS_SERIF, decoded.getValue("valid").fontFamily)
        assertFalse(decoded.containsKey("malformed"))
    }

    @Test
    fun `library reader profiles round trip independently`() {
        val novels = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            theme = EpubReaderTheme.Light,
            fontFamily = EpubReaderFontFamily.SYSTEM_SANS_SERIF,
            fontScale = 1.2f,
            lineSpacing = 1.3f,
            wordSpacing = 0.2f,
            padding = EpubPaddingPercentages(10f, 20f, 30f, 40f),
            epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
            pdfLayoutMode = ReaderLayoutMode.PAGINATED,
            pdfPageGapDp = 4f,
            showPageProgress = false,
            showHeaderFooterBorders = true,
            comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
            comicPageGapDp = 24f
        )
        val manga = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
            theme = EpubReaderTheme.Dark,
            fontScale = 1.4f,
            padding = EpubPaddingPercentages(5f, 6f, 7f, 8f)
        )

        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(mapOf("novels" to novels, "manga" to manga))
        )

        assertEquals(novels, decoded["novels"])
        assertEquals(manga, decoded["manga"])
        assertEquals(LibraryReaderPreferences(), AppPreferences(
            libraryReaderPreferences = decoded
        ).readerPreferencesFor("unknown"))
    }

    @Test
    fun `library profile updates only the selected library and clamps reader values`() {
        val initial = AppPreferences().withReaderPreferences(
            "manga",
            LibraryReaderPreferences(
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                fontScale = 2.5f,
                lineSpacing = 3f,
                wordSpacing = 2f,
                paragraphSpacing = 3f,
                marginTapZoneExclusionPercent = 102,
                padding = EpubPaddingPercentages(-1f, 101f, 25f, 50f),
                pdfPageGapDp = -10f,
                comicPageGapDp = 100f
            )
        )

        assertEquals(LibraryReadingDirection.RIGHT_TO_LEFT, initial.readerPreferencesFor("manga").readingDirection)
        assertEquals(MAX_EPUB_FONT_SCALE, initial.readerPreferencesFor("manga").fontScale)
        assertEquals(MAX_EPUB_LINE_SPACING, initial.readerPreferencesFor("manga").lineSpacing)
        assertEquals(MAX_EPUB_WORD_SPACING, initial.readerPreferencesFor("manga").wordSpacing)
        assertEquals(100, initial.readerPreferencesFor("manga").marginTapZoneExclusionPercent)
        assertEquals(
            MAX_EPUB_PARAGRAPH_SPACING,
            initial.readerPreferencesFor("manga").paragraphSpacing
        )
        assertEquals(EpubPaddingPercentages(0f, 100f, 25f, 50f), initial.readerPreferencesFor("manga").padding)
        assertEquals(0f, initial.readerPreferencesFor("manga").pdfPageGapDp)
        assertEquals(MAX_READER_PAGE_GAP_DP, initial.readerPreferencesFor("manga").comicPageGapDp)
        assertEquals(LibraryReadingDirection.LEFT_TO_RIGHT, initial.readerPreferencesFor("novels").readingDirection)
        assertTrue(libraryReaderPreferencesFromStorage("not-json").isEmpty())
    }

    @Test
    fun `legacy library profiles receive format layout defaults`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"library":{"readingDirection":"right_to_left"}}"""
        ).getValue("library")

        assertEquals(ReaderLayoutMode.CONTINUOUS, decoded.pdfLayoutMode)
        assertEquals(ReaderLayoutMode.PAGINATED, decoded.comicLayoutMode)
        assertEquals(DEFAULT_READER_PAGE_GAP_DP, decoded.pdfPageGapDp)
        assertFalse(decoded.invertPdfColors)
        assertEquals(DEFAULT_READER_PAGE_GAP_DP, decoded.comicPageGapDp)
        assertEquals(EpubReaderFontFamily.PUBLISHER_DEFAULT, decoded.fontFamily)
        assertEquals(DEFAULT_EPUB_LINE_SPACING, decoded.lineSpacing)
        assertEquals(DEFAULT_EPUB_WORD_SPACING, decoded.wordSpacing)
        assertEquals(DEFAULT_EPUB_PARAGRAPH_SPACING, decoded.paragraphSpacing)
        assertFalse(decoded.preferServerProgressOnReopen)
        assertFalse(decoded.volumeButtonPageNavigation)
    }

    @Test
    fun `spread preferences round trip through storage`() {
        val profile = LibraryReaderPreferences(
            twoPageSpread = ReaderSpreadMode.OFF,
            comicSpreadCoverAlone = false,
            comicSpreadShift = true
        )

        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(mapOf("manga" to profile))
        ).getValue("manga")

        assertEquals(profile, decoded)
        assertEquals(ReaderSpreadMode.OFF, decoded.twoPageSpread)
        assertFalse(decoded.comicSpreadCoverAlone)
        assertTrue(decoded.comicSpreadShift)
    }

    @Test
    fun `legacy profiles without spread keys default to automatic spreads with cover alone`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"version":2,"profiles":{"library":{"theme":"oled_black"}}}"""
        ).getValue("library")

        assertEquals(ReaderSpreadMode.AUTO, decoded.twoPageSpread)
        assertTrue(decoded.comicSpreadCoverAlone)
        assertFalse(decoded.comicSpreadShift)
        assertEquals(EpubReaderTheme.OledBlack, decoded.theme)
    }

    @Test
    fun `unknown spread mode value falls back to automatic without dropping the profile`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"version":2,"profiles":{"library":{"twoPageSpread":"sideways","comicSpreadShift":true}}}"""
        ).getValue("library")

        assertEquals(ReaderSpreadMode.AUTO, decoded.twoPageSpread)
        assertTrue(decoded.comicSpreadShift)
    }

    @Test
    fun `global navigation bar policy applies uniformly to supported readers`() {
        assertTrue(AppPreferences().hideNavigationBarWhileReading)
        assertFalse(AppPreferences().hideStatusBarWhileReading)
        assertFalse(readerSystemBarsPolicy(true).showNavigationBar)
        assertTrue(readerSystemBarsPolicy(false).showNavigationBar)
    }

    @Test
    fun `volume button navigation preference persists independently per library`() {
        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(
                mapOf(
                    "enabled" to LibraryReaderPreferences(
                        volumeButtonPageNavigation = true,
                        reverseVolumeButtonNavigation = true
                    ),
                    "disabled" to LibraryReaderPreferences()
                )
            )
        )

        assertTrue(decoded.getValue("enabled").volumeButtonPageNavigation)
        assertTrue(decoded.getValue("enabled").reverseVolumeButtonNavigation)
        assertFalse(decoded.getValue("disabled").volumeButtonPageNavigation)
        assertFalse(decoded.getValue("disabled").reverseVolumeButtonNavigation)
    }

    @Test
    fun `invalid font family storage falls back to publisher default`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"library":{"fontFamily":"not-a-font"}}"""
        ).getValue("library")

        assertEquals(EpubReaderFontFamily.PUBLISHER_DEFAULT, decoded.fontFamily)
    }

    @Test
    fun `custom font metadata is stored per library`() {
        val custom = CustomFontRecord("custom-font.ttf", "Atkinson.ttf", "bookorbit-custom-atkinson")
        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(
                mapOf("library" to LibraryReaderPreferences(
                    fontFamily = EpubReaderFontFamily.CUSTOM,
                    customFont = custom
                ))
            )
        ).getValue("library")

        assertEquals(EpubReaderFontFamily.CUSTOM, decoded.fontFamily)
        assertEquals(custom, decoded.customFont)
    }

    @Test
    fun `custom font validation accepts font signatures and rejects arbitrary files`() {
        val font = File.createTempFile("font", ".ttf")
        val invalid = File.createTempFile("font", ".ttf")
        try {
            font.writeBytes(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            invalid.writeText("not a font")
            assertTrue(isValidFontFile(font))
            assertFalse(isValidFontFile(invalid))
        } finally {
            font.delete()
            invalid.delete()
        }
    }

    @Test
    fun `EPUB preferences keep layout progression left to right for both tap directions`() {
        assertEquals(
            ReadingProgression.LTR,
            readiumEpubReadingProgression(LibraryReadingDirection.LEFT_TO_RIGHT)
        )
        assertEquals(
            ReadingProgression.LTR,
            readiumEpubReadingProgression(LibraryReadingDirection.RIGHT_TO_LEFT)
        )
    }

    @Test
    fun `EPUB line spacing maps to Readium line height and clamps`() {
        assertEquals(
            1.4,
            readiumEpubLineHeight(1.4f),
            0.0001
        )
        assertEquals(
            MAX_EPUB_LINE_SPACING.toDouble(),
            readiumEpubLineHeight(3f),
            0.0001
        )
    }

    @Test
    fun `EPUB word spacing maps to Readium word spacing and clamps`() {
        assertEquals(
            0.4,
            readiumEpubWordSpacing(0.4f),
            0.0001
        )
        assertEquals(
            MAX_EPUB_WORD_SPACING.toDouble(),
            readiumEpubWordSpacing(2f),
            0.0001
        )
    }

    @Test
    fun `EPUB paragraph spacing maps to Readium rem spacing and clamps`() {
        assertEquals(0.7, readiumEpubParagraphSpacing(0.7f), 0.0001)
        assertEquals(
            MAX_EPUB_PARAGRAPH_SPACING.toDouble(),
            readiumEpubParagraphSpacing(3f),
            0.0001
        )
    }

    @Test
    fun `reader options height defaults and clamps within drag bounds`() {
        val collapsedHeightFraction = READER_OPTIONS_RESIZE_HANDLE_MIN_HEIGHT_DP / 600f
        assertEquals(
            READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = 0f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
        assertEquals(
            READER_OPTIONS_MAX_HEIGHT_FRACTION,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = -600f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
        assertEquals(
            collapsedHeightFraction,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = 600f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
    }

    @Test
    fun `PDF layout maps to the native Readium axis and page gap`() {
        val paginated = pdfiumPreferencesFor(
            LibraryReaderPreferences(
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                pdfLayoutMode = ReaderLayoutMode.PAGINATED,
                pdfPageGapDp = 12f
            )
        )
        val continuous = pdfiumPreferencesFor(
            LibraryReaderPreferences(pdfLayoutMode = ReaderLayoutMode.CONTINUOUS)
        )

        assertEquals(Axis.HORIZONTAL, paginated.scrollAxis)
        assertEquals(ReadingProgression.RTL, paginated.readingProgression)
        assertEquals(12.0, paginated.pageSpacing)
        assertEquals(Axis.VERTICAL, continuous.scrollAxis)
    }

    @Test
    fun `PDF facing-page toggle removes spacing only for paginated layout`() {
        val joined = pdfiumPreferencesFor(
            LibraryReaderPreferences(
                pdfLayoutMode = ReaderLayoutMode.PAGINATED,
                pdfPageGapDp = 12f,
                joinPdfFacingPages = true
            )
        )
        val continuous = pdfiumPreferencesFor(
            LibraryReaderPreferences(
                pdfLayoutMode = ReaderLayoutMode.CONTINUOUS,
                pdfPageGapDp = 12f,
                joinPdfFacingPages = true
            )
        )

        assertEquals(0.0, joined.pageSpacing)
        assertEquals(12.0, continuous.pageSpacing)
    }

    @Test
    fun `PDF facing-page toggle defaults off and persists per library`() {
        assertFalse(LibraryReaderPreferences().joinPdfFacingPages)

        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(
                mapOf("joined" to LibraryReaderPreferences(joinPdfFacingPages = true))
            )
        )

        assertTrue(decoded.getValue("joined").joinPdfFacingPages)
    }

    @Test
    fun `PDF color inversion preference persists independently`() {
        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(
                mapOf(
                    "inverted" to LibraryReaderPreferences(invertPdfColors = true),
                    "normal" to LibraryReaderPreferences()
                )
            )
        )

        assertTrue(decoded.getValue("inverted").invertPdfColors)
        assertFalse(decoded.getValue("normal").invertPdfColors)
    }

    @Test
    fun `browse options expansion persists independently by library`() {
        val storage = libraryBrowseOptionsExpandedStorageValue(
            mapOf("library-b" to true, "library-a" to false, "" to true)
        )

        assertEquals(
            mapOf("library-a" to false, "library-b" to true),
            libraryBrowseOptionsExpandedFromStorage(storage)
        )
    }

    @Test
    fun `malformed browse options expansion falls back to collapsed`() {
        assertEquals(emptyMap<String, Boolean>(), libraryBrowseOptionsExpandedFromStorage("not-json"))
        assertEquals(false, libraryBrowseOptionsExpandedFromStorage("{\"library\":\"yes\"}")["library"])
    }

    @Test
    fun `layout-affecting reader changes require a fresh pagination pass`() {
        val base = LibraryReaderPreferences()

        assertFalse(epubLayoutChangeRequiresRelayout(base, base.copy(theme = EpubReaderTheme.Dark)))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(fontScale = base.fontScale + 0.1f)))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(lineSpacing = 1.5f)))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(padding = base.padding.copy(left = 30f))))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(showHeader = true)))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(showPageProgress = false)))
        assertTrue(epubLayoutChangeRequiresRelayout(base, base.copy(epubLayoutMode = ReaderLayoutMode.CONTINUOUS)))
        assertFalse(
            epubLayoutChangeRequiresRelayout(
                base.copy(epubLayoutMode = ReaderLayoutMode.CONTINUOUS),
                base.copy(
                    epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
                    padding = base.padding.copy(left = 30f)
                )
            )
        )
    }

    @Test
    fun `apply to all libraries only touches the active server profile`() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val source = LibraryReaderPreferences(
            theme = EpubReaderTheme.Dark,
            customTextColor = 0xFFB8B8B8.toInt(),
            fontScale = 1.25f
        )
        val otherServerProfile = LibraryReaderPreferences(theme = EpubReaderTheme.Sepia)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf(
                readerPreferencesStorageKey(profileA, "library-a") to
                    LibraryReaderPreferences(theme = EpubReaderTheme.Light),
                readerPreferencesStorageKey(profileA, "library-b") to
                    LibraryReaderPreferences(theme = EpubReaderTheme.Sepia),
                readerPreferencesStorageKey(profileB, "library-a") to otherServerProfile,
                defaultReaderPreferencesStorageKey(profileB) to otherServerProfile
            )
        )

        val updated = preferences.withReaderPreferencesForAllLibraries(
            profileId = profileA,
            libraryId = "library-a",
            value = source
        )

        val expected = source.normalized()
        assertEquals(expected, updated.libraryReaderPreferences.getValue(readerPreferencesStorageKey(profileA, "library-a")))
        assertEquals(expected, updated.libraryReaderPreferences.getValue(readerPreferencesStorageKey(profileA, "library-b")))
        assertEquals(expected, updated.libraryReaderPreferences.getValue(defaultReaderPreferencesStorageKey(profileA)))
        assertEquals(otherServerProfile, updated.libraryReaderPreferences.getValue(readerPreferencesStorageKey(profileB, "library-a")))
        assertEquals(otherServerProfile, updated.libraryReaderPreferences.getValue(defaultReaderPreferencesStorageKey(profileB)))
    }

    @Test
    fun `reader preferences fall back to the active server default for unknown libraries`() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val fallbackA = LibraryReaderPreferences(
            theme = EpubReaderTheme.Dark,
            customBackgroundColor = 0xFF101010.toInt()
        )
        val fallbackB = LibraryReaderPreferences(theme = EpubReaderTheme.Sepia)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf(
                defaultReaderPreferencesStorageKey(profileA) to fallbackA,
                defaultReaderPreferencesStorageKey(profileB) to fallbackB
            )
        )

        assertEquals(fallbackA.normalized(), preferences.readerPreferencesFor(profileA, "never-opened-library"))
        assertEquals(fallbackB.normalized(), preferences.readerPreferencesFor(profileB, "never-opened-library"))

        val onlyServerB = AppPreferences(
            libraryReaderPreferences = mapOf(
                defaultReaderPreferencesStorageKey(profileB) to fallbackB
            )
        )
        assertEquals(LibraryReaderPreferences(), onlyServerB.readerPreferencesFor(profileA, "new-library"))
    }

    @Test
    fun `an explicit library profile wins over the active server default`() {
        val profileId = "profile-a"
        val explicit = LibraryReaderPreferences(theme = EpubReaderTheme.Light)
        val fallback = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf(
                readerPreferencesStorageKey(profileId, "library-a") to explicit,
                defaultReaderPreferencesStorageKey(profileId) to fallback
            )
        )

        assertEquals(explicit.normalized(), preferences.readerPreferencesFor(profileId, "library-a"))
    }


    @Test
    fun `legacy profiles migrate to a known owner without leaking to another profile`() {
        val legacy = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
        val legacyFallback = LibraryReaderPreferences(theme = EpubReaderTheme.Sepia)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf(
                "shared-library" to legacy,
                DEFAULT_READER_PROFILE_KEY to legacyFallback
            )
        )

        val migrated = preferences.migrateLegacyReaderPreferences("profile-a")

        assertEquals(legacy.normalized(), migrated.readerPreferencesFor("profile-a", "shared-library"))
        assertEquals(legacyFallback.normalized(), migrated.readerPreferencesFor("profile-a", "new-library"))
        assertEquals(
            LibraryReaderPreferences(),
            migrated.readerPreferencesFor("profile-b", "shared-library")
        )
        assertFalse(migrated.libraryReaderPreferences.containsKey("shared-library"))
        assertFalse(migrated.libraryReaderPreferences.containsKey(DEFAULT_READER_PROFILE_KEY))
    }

    @Test
    fun `legacy profiles stay unscoped and are not reused when the owner is unknown`() {
        val legacy = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf("shared-library" to legacy)
        )

        val unmigrated = preferences.migrateLegacyReaderPreferences(null)

        assertEquals(
            LibraryReaderPreferences(),
            unmigrated.readerPreferencesFor("profile-a", "shared-library")
        )
        assertEquals(
            LibraryReaderPreferences(),
            unmigrated.readerPreferencesFor("profile-b", "shared-library")
        )
        // Local, unscoped books still retain the legacy setting.
        assertEquals(legacy.normalized(), unmigrated.readerPreferencesFor("", "shared-library"))
    }

    @Test
    fun `legacy migration keeps an existing scoped profile`() {
        val scoped = LibraryReaderPreferences(theme = EpubReaderTheme.Light)
        val legacy = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf(
                readerPreferencesStorageKey("profile-a", "shared-library") to scoped,
                "shared-library" to legacy
            )
        )

        val migrated = preferences.migrateLegacyReaderPreferences("profile-a")

        assertEquals(scoped.normalized(), migrated.readerPreferencesFor("profile-a", "shared-library"))
        assertFalse(migrated.libraryReaderPreferences.containsKey("shared-library"))
    }

    @Test
    fun `unowned legacy settings are not persisted into a scoped profile`() {
        val legacy = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf("shared-library" to legacy)
        )

        val resolved = preferences.readerPreferencesFor("profile-a", "shared-library")
        val withResolved = preferences.withReaderPreferences(
            profileId = "profile-a",
            libraryId = "shared-library",
            value = resolved
        )

        assertEquals(LibraryReaderPreferences(), resolved)
        assertEquals(
            LibraryReaderPreferences(),
            withResolved.libraryReaderPreferences.getValue(
                readerPreferencesStorageKey("profile-a", "shared-library")
            )
        )
        assertEquals(
            LibraryReaderPreferences(),
            withResolved.readerPreferencesFor("profile-b", "shared-library")
        )
    }

    @Test
    fun `legacy migration is blocked after multiple profiles and does not retry after removal`() {
        val legacy = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)

        assertEquals(
            LegacyReaderMigrationAction.BLOCK,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 2,
                migrationBlocked = false
            )
        )

        // The first ambiguous read persists the blocked marker. Removing one profile must not
        // make the same legacy values eligible for the remaining profile.
        val blockedAfterAmbiguity = legacyReaderMigrationAction(
            hasLegacyPreferences = true,
            profileCount = 2,
            migrationBlocked = false
        ) == LegacyReaderMigrationAction.BLOCK
        assertTrue(blockedAfterAmbiguity)
        assertEquals(
            LegacyReaderMigrationAction.NONE,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 1,
                migrationBlocked = blockedAfterAmbiguity
            )
        )

        val preferences = AppPreferences(
            libraryReaderPreferences = mapOf("shared-library" to legacy)
        )
        val result = preferences.migrateLegacyReaderPreferences(
            ownerProfileId = "remaining-profile",
            migrationBlocked = blockedAfterAmbiguity
        )

        assertEquals(legacy.normalized(), result.libraryReaderPreferences.getValue("shared-library"))
        assertFalse(
            result.libraryReaderPreferences.containsKey(
                readerPreferencesStorageKey("remaining-profile", "shared-library")
            )
        )
        assertEquals(
            LibraryReaderPreferences(),
            result.readerPreferencesFor("remaining-profile", "shared-library")
        )
    }

    @Test
    fun `legacy migration decision requires one unambiguous owner`() {
        assertEquals(
            LegacyReaderMigrationAction.NONE,
            legacyReaderMigrationAction(
                hasLegacyPreferences = false,
                profileCount = 0,
                migrationBlocked = false
            )
        )
        assertEquals(
            LegacyReaderMigrationAction.MIGRATE,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 1,
                migrationBlocked = false
            )
        )
        assertEquals(
            LegacyReaderMigrationAction.BLOCK,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 0,
                migrationBlocked = false
            )
        )
        assertEquals(
            LegacyReaderMigrationAction.BLOCK,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 3,
                migrationBlocked = false
            )
        )
        assertEquals(
            LegacyReaderMigrationAction.NONE,
            legacyReaderMigrationAction(
                hasLegacyPreferences = true,
                profileCount = 1,
                migrationBlocked = true
            )
        )
    }
}
