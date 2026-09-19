package com.theo.wizardreal.g2p;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Japanese kanji -> kana readings (phrase level), the missing half of Tier-1
 * ja (docs/g2p/02 C4). Kanji in ja aliases previously failed G2P strictly
 * (no draft) because readings need context — per-kanji dictionaries give the
 * wrong reading for compounds (地割れ = じわれ, not ち/わり).
 *
 * <p>Phrase-level longest-match substitution over the WHOLE text before
 * segmentation (same semantics as the bench's engbench JA_READINGS pass,
 * 39/39 coverage over the 45-spell matrix): known phrases become kana and
 * then convert through {@link KanaIpa}; unknown kanji stay untouched and the
 * strict conversion rejects them (fail closed, no garbage readings).
 *
 * <p>This is a curated table like the spell JSON ipa column — new kanji
 * aliases must add a row here (or get no ja draft).
 */
public final class KanjiIpa {
    private KanjiIpa() {}

    /** kanji phrase -> kana reading (source of truth, unordered map). */
    private static final Map<String, String> TABLE = Map.ofEntries(
            Map.entry("紅蓮の雷を目覚めさせよ", "ぐれんのらいをめざめさせよ"),
            Map.entry("慈悲なき炎よ流れよ", "じひなきほのおよながれよ"),
            Map.entry("灰は目覚め空は燃える", "はいはめざめそらはもえる"),
            Map.entry("破滅の名のもとに封ぜよ", "はめつのなのもとにふうぜよ"),
            Map.entry("闇をもって天を覆え", "やみをもっててんをおおえ"),
            Map.entry("大地よ割れ開け", "だいちよわれあけ"),
            Map.entry("高き壁よひざせ", "たかきかべよひざせ"),
            Map.entry("天よかしこめ", "てんよかしこめ"),
            Map.entry("厄の鐘が鳴る", "やくのかねがなる"),
            Map.entry("剣の領域", "けんのりょういき"),
            Map.entry("火の領域", "ひのりょういき"),
            Map.entry("剣雨領域", "けんうりょういき"),
            Map.entry("降雨領域", "こううりょういき"),
            Map.entry("天罰領域", "てんばつりょういき"),
            Map.entry("豪雨領域", "ごううりょういき"),
            Map.entry("麻痺領域", "まひりょういき"),
            Map.entry("炎のブレス", "ほのおのブレス"),
            Map.entry("潮のブレス", "しおのブレス"),
            Map.entry("根のブレス", "ねのブレス"),
            Map.entry("混沌のブレス", "こんとんのブレス"),
            Map.entry("聖光のブレス", "せいこうのブレス"),
            Map.entry("落石のブレス", "らくせきのブレス"),
            Map.entry("霧のブレス", "きりのブレス"),
            Map.entry("吸収のブレス", "きゅうしゅうのブレス"),
            Map.entry("千の剣", "せんのけん"),
            Map.entry("天の鎖", "てんのくさり"),
            Map.entry("束縛蔓", "そくばくかずら"),
            Map.entry("鎧溶かし", "よろいとかし"),
            Map.entry("骸骨召喚", "がいこつしょうかん"),
            Map.entry("静止の域", "せいしのいき"),
            Map.entry("萎れの域", "しおれのいき"),
            Map.entry("渦潮", "うずしお"),
            Map.entry("石の壁", "いしのかべ"),
            Map.entry("虚空の裂け目", "こくうのさけめ"),
            Map.entry("竜巻", "たつまき"),
            Map.entry("突風", "とっぷう"),
            Map.entry("疾風歩", "しっぷうほ"),
            Map.entry("地割れ", "じわれ"),
            Map.entry("変身", "へんしん"),
            Map.entry("恐怖", "きょうふ"),
            Map.entry("催眠", "さいみん"),
            Map.entry("裁き", "さばき"),
            Map.entry("連鎖雷", "れんさらい"));

    /** Longest-first iteration order: a phrase that CONTAINS another must be
     * replaced before its substring (future-proofing; no such pair today). */
    private static final List<Map.Entry<String, String>> READINGS = TABLE.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
            .toList();

    /**
     * Replace every known kanji phrase with its kana reading (longest match
     * first). Text without kanji is returned unchanged; UNKNOWN kanji pass
     * through untouched — the caller's strict conversion rejects them.
     */
    public static String toKana(String text) {
        if (text == null || text.isBlank() || !containsKanji(text)) return text;
        StringBuilder out = new StringBuilder(text);
        for (Map.Entry<String, String> e : READINGS) {
            replaceAll(out, e.getKey(), e.getValue());
        }
        return out.toString();
    }

    private static boolean containsKanji(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)) return true;
        }
        return false;
    }

    private static void replaceAll(StringBuilder sb, String from, String to) {
        int idx = sb.indexOf(from);
        while (idx >= 0) {
            sb.replace(idx, idx + from.length(), to);
            idx = sb.indexOf(from, idx + to.length());
        }
    }

    /** Exposed for tests/lab tooling: the curated table, longest-first. */
    public static List<Map.Entry<String, String>> readings() {
        return READINGS;
    }
}
