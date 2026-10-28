@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.vangeaux.lagrange

import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer
import org.readium.r2.shared.util.tokenizer.TextUnit
import org.readium.r2.shared.util.tokenizer.Tokenizer

/**
 * Keeps Readium's sentence tokenizer, then joins boundaries that ICU places inside
 * English abbreviations, initials, ranks, names, references and dialogue tags.
 *
 * The returned ranges always refer to the original text so TTS highlighting and
 * locator tracking keep the same coordinate system.
 */
internal class EpubTtsSentenceTokenizer(
    language: Language?,
    delegate: Tokenizer<String, IntRange>? = null,
    fallbackLanguage: Language? = null
) : Tokenizer<String, IntRange> {
    private val effectiveLanguage = normalizeEpubTtsSentenceLanguage(language ?: fallbackLanguage)
    private val delegate = delegate ?: DefaultTextContentTokenizer(TextUnit.Sentence, effectiveLanguage)
    private val applyEnglishRules = isEnglishLanguage(effectiveLanguage)

    override fun tokenize(data: String): List<IntRange> {
        val ranges = delegate.tokenize(data)
        return if (applyEnglishRules) mergeEpubTtsSentenceRanges(data, ranges) else ranges
    }
}

internal fun normalizeEpubTtsSentenceLanguage(language: Language?): Language? =
    if (language?.code.equals("english", ignoreCase = true)) Language("en") else language

private fun isEnglishLanguage(language: Language?): Boolean {
    val primary = language?.code
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?: return false
    return primary.equals("en", ignoreCase = true) ||
        primary.equals("eng", ignoreCase = true)
}

// These case-sensitive groups were checked against ICU4J's en_GB sentence iterator.
// A forms do not end sentences. B forms usually prefix names, ranks or places and use
// the starter-word guard below. C forms merge only before numeric references.
private val alwaysMergeAbbreviations = tokenSet(
    """
    a.k.a. approx. attn. c/o cf. e.g. esp. excl. i.a. i.e. incl. Messrs. Mlle. Mme.
    Mmes. Mr. Mrs. Ms. Mx. N.B. n.b. p.p. P.P.S. P.S. s.v. sc. scil. v. v.i. v.s.
    viz. vs.
    """
)

private val namePrefixAbbreviations = tokenSet(
    """
    A.B. Abp. Adm. Adml. Adv. Ald. Am. Amb. Amer. Apt. arr. Assoc. Asst. Atty.
    Austral. b. Bdr. Benj. Bp. Br. Brig. Brit. Bro. Bros. bur. C.I.S. C.P.O. Capt.
    Card. Cdr. Cdre. Cert. Ch. Chas. Chron. Cllr. Cmdr. Cmdre. Cnr. Co. Col. comp.
    cond. Const. Cor. Coun. Cpl. Cr. d. D.C. D.G. def. dep. Det. Deut. Dip. Dir. dir.
    Dr. Drs. E.E.C. E.U. Eccles. ed. eds. Eph. Est. Ex. Exec. Exod. Ezek. F.M. Fg.
    Fid. Fl. Flt. Fr. Ft. ft. Fus. G.B. Gdsm. Gen. Geo. Gnr. Gp. Gt. H.M. H.M.S.
    H.R.H. Hab. Hag. Heb. Hon. Hos. illus. Insp. Intl. introd. Jas. Jer. Jno. Judg.
    L.A. L/Cpl. Lam. Ld. Ldr. Lieut. Lt. Lvl. Lwr. M. Macc. Maj. Mgr. Mid. Msgr.
    Mt. N.E. N.I. N.W. N.Y.C. N.Z. Nah. Natl. Neh. Nos. nr. Nth. Num. Obad. Off.
    Op. P.C. P.O. Philem. Plt. Pres. Prof. Prov. Ps. Pt. Pte. Pty. Pvt. R. R.M.S.
    R.S.A. Rev. Revd. Rfn. Rm. Rt. S.A. S.E. S.S. S.W. Sch. Sec. Sgt. Spr. Sqn.
    Sr. St. Ste. Sth. Sts. Sub-Lt. Supt. Thess. Thos. Tpr. tr. trans. U.K. U.N.
    U.S. U.S.A. Univ. Upr. V. Ven. W.O. Wg. Wm. Zech. Zeph.
    """
)

private val numberPrefixAbbreviations = tokenSet(
    """
    A.D. A.H. a/c amt. app. Apr. art. Aug. bk. c. c.c. C.E. ca. cap. ch. chap. chs.
    circ. cl. cm. col. cols. cu. cwt. Dan. dau. Dec. deg. dist. Div. doc. docs. doz.
    dsp. dwt. ep. eq. eqs. est. ex. exs. Ext. ext. Feb. fig. figs. fl. fn. fol. fols.
    Fri. Gal. gal. gals. gm. gn. gns. h.p. hr. hrs. Hwy. inc. ins. Isa. Jan. Josh.
    Jul. Jun. kg. km. l. lat. lb. lbs. Lev. ll. m. m.p.h. Mal. Mar. Matt. mi. Mic.
    min. mins. mm. mo. mob. Mon. mos. mth. n. nn. No. no. nos. Nov. ob. Oct. op.
    oz. p. para. paras. Pet. ph. Phil. pl. pop. pp. pron. pt. pts. pub. qt. qty. r.
    r.p.m. ref. refs. reg. Rom. Rte. s. Sam. Sat. sec. secs. sect. Sep. Sept. sq.
    ss. st. Sun. tbsp. tel. temp. Thu. Thur. Thurs. Tim. tsp. Tue. Tues. ver. Vic.
    vol. vols. vv. Wed. Weds. wk. wks. wt. yd. yds. yr. yrs.
    """
)

private val sentenceStarterWords = tokenSet(
    """
    The A He She It They We I You This That These Those There Then But And His Her Its
    Their Our My Your In On At As When While After Before If Now Yet Later Meanwhile
    Well Yes What Why How Where Was Were Is Are Did Does Had Has Have Not Some All Once
    Soon Suddenly Perhaps Even Although Because Since Here For With From To Of
    """
)

private val initialPattern = Regex("""^\p{Lu}\.$""")
private val romanNumeralPattern = Regex("""^[IVXLCDM]{2,}\b""")
private val closingPunctuation = setOf('"', '\'', '\u201D', '\u2019', ')', ']', '}')
private val openingPunctuation = setOf('"', '\'', '\u201C', '\u2018', '(', '[', '{')

internal fun mergeEpubTtsSentenceRanges(
    text: String,
    ranges: List<IntRange>
): List<IntRange> {
    val merged = ArrayList<IntRange>(ranges.size)
    for (range in ranges) {
        val previous = merged.lastOrNull()
        if (previous != null && shouldMergeEpubTtsSentenceRanges(text, previous, range)) {
            merged[merged.lastIndex] = previous.first..range.last
        } else {
            merged += range
        }
    }
    return merged
}

private fun shouldMergeEpubTtsSentenceRanges(
    text: String,
    previous: IntRange,
    next: IntRange
): Boolean {
    val segment = text.substring(previous).trimEnd()
    val following = text.substring(next).trimStart()
    if (segment.isEmpty() || following.isEmpty()) return false

    val beforeClosers = segment.trimEnd { it in closingPunctuation }
    if ((beforeClosers.endsWith('?') || beforeClosers.endsWith('!')) &&
        following.first().isLowerCase()
    ) {
        return true
    }

    val lastToken = segment
        .takeLastWhile { !it.isWhitespace() }
        .trimStart { it in openingPunctuation }
    val nextWord = following.takeWhile(Char::isLetter)
    val afterNextWord = following.getOrNull(nextWord.length)
    val nextIsSentenceStarter = nextWord in sentenceStarterWords &&
        (afterNextWord == null || afterNextWord == ' ' || afterNextWord == ',')

    return when {
        lastToken in alwaysMergeAbbreviations -> true
        lastToken in namePrefixAbbreviations -> !nextIsSentenceStarter
        lastToken in numberPrefixAbbreviations ->
            following.first().isDigit() || romanNumeralPattern.containsMatchIn(following)
        initialPattern.matches(lastToken) -> !nextIsSentenceStarter
        else -> false
    }
}

private fun tokenSet(source: String): Set<String> =
    source.trim().split(Regex("\\s+")).toSet()
