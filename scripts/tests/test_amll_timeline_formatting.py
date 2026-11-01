"""Regression checks for the standalone AMLL timeline formatter."""
import importlib.util
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

SPEC = importlib.util.spec_from_file_location("amll_converter", Path(__file__).resolve().parents[1] / "amll_to_apple_ttml.py")
CONVERTER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CONVERTER
SPEC.loader.exec_module(CONVERTER)

TTML = "http://www.w3.org/ns/ttml"
ITUNES = "http://music.apple.com/lyric-ttml-internal"
TTM = "http://www.w3.org/ns/ttml#metadata"
QUOTED_KEYS = (
    "'a\"b'",
    "\"a'b\"",
    "'a\"&amp;b'",
    "\"a&amp;b\"",
    "\"a&quot;&apos;&lt;&gt;&amp;b\"",
    "\"a&#34;&#39;&#38;b\"",
    "\"a&#x22;&#x27;&#x26;b\"",
    "'a\"&amp;quot;b'",
)


def document(body, tracks=""):
    head = f'<iTunesMetadata xmlns="{ITUNES}">{tracks}</iTunesMetadata>' if tracks else ""
    return (f'<tt xmlns="{TTML}" xmlns:ttm="{TTM}" xmlns:itunes="{ITUNES}" xml:lang="ja" itunes:timing="Word">'
            f'<head><metadata>{head}</metadata></head><body>{body}</body></tt>')


def line(key, begin, end, translation=None, roman=None):
    translation = f'<span ttm:role="x-translation">{translation}</span>' if translation is not None else ""
    roman = f'<span ttm:role="x-roman">{roman}</span>' if roman is not None else ""
    return (f'<p begin="{begin}" end="{end}" itunes:key="{key}">'
            f'<span begin="{begin}" end="{end}">word</span> <span begin="{begin}" end="{end}">two</span>'
            f'{translation}{roman}</p>')


def keys(ttml):
    return [node.get(f"{{{ITUNES}}}key") for node in ET.fromstring(ttml).iter(f"{{{TTML}}}p")]


def texts(ttml, kind="translations"):
    track = next(node for node in ET.fromstring(ttml).iter() if node.tag == f"{{{ITUNES}}}{kind}")
    return ["".join(node.itertext()) for node in track.iter(f"{{{ITUNES}}}text")]


class AmllTimelineFormattingTest(unittest.TestCase):
    def convert(self, raw):
        result = CONVERTER.to_apple_format(raw)
        self.assertTrue(CONVERTER.is_acceptable(result.ttml))
        ET.fromstring(result.ttml)
        again = CONVERTER.to_apple_format(result.ttml)
        self.assertFalse(again.converted)
        self.assertEqual(result.ttml, again.ttml)
        return result.ttml

    def test_numeric_order_keeps_translation_romanization_and_word_whitespace(self):
        result = self.convert(document("<div>" + line("late", "10s", "11s", "Late", "RL") +
                                       line("early", "2s", "3s", "Early", "RE") + "</div>"))
        self.assertEqual(["early", "late"], keys(result))
        self.assertEqual(["Early", "Late"], texts(result))
        self.assertEqual(["RE", "RL"], texts(result, "transliterations"))
        self.assertIn('</span> <span begin="2s"', result)

    def test_matching_begins_merge_even_with_different_ends(self):
        result = self.convert(document("<div>" + line("one", "00:01.500", "2s", "First &amp; second", "R1") +
                                       line("two", "1.5s", "5s", "Another", "R2") +
                                       line("three", "3s", "4s", "Later", "R3") + "</div>"))
        self.assertEqual(["one", "two", "three"], keys(result))
        self.assertEqual(["First & second\nAnother", " ", "Later"], texts(result))
        self.assertEqual(["R1", "R2", "R3"], texts(result, "transliterations"))
        self.assertIn('end="5s"', result)
        self.assertIn("First &amp; second", result)

    def test_missing_and_duplicate_translations_keep_one_entry_per_voice(self):
        result = self.convert(document("<div>" + line("one", "1s", "2s") +
                                       line("two", "1s", "3s", "Same") +
                                       line("three", "1s", "4s", "Same") +
                                       line("four", "5s", "6s", "Next") + "</div>"))
        self.assertEqual(["Same", " ", " ", "Next"], texts(result))
        self.assertEqual(4, len(keys(result)))

    def test_equal_ends_do_not_merge(self):
        result = self.convert(document("<div>" + line("one", "1s", "4s", "One") + line("two", "2s", "4s", "Two") + "</div>"))
        self.assertEqual(["One", "Two"], texts(result))

    def test_exact_clock_offset_equivalence(self):
        for clock, offset in (("1:30.100", "90.1s"), ("00:00:01.500", "1500ms"), ("1:00", "1m"), ("01:00:00", "1h")):
            with self.subTest(clock=clock, offset=offset):
                result = self.convert(document("<div>" + line("one", clock, "02:00:00", "One") +
                                               line("two", offset, "03:00:00", "Two") + "</div>"))
                self.assertEqual(["One\nTwo", " "], texts(result))

    def test_existing_apple_tracks_sort_without_changing_language(self):
        tracks = ('<translations><translation xml:lang="en"><text for="late">Late</text>'
                  '<text for="early">Early</text></translation></translations>')
        result = self.convert(document("<div>" + line("late", "10s", "11s") + line("early", "2s", "3s") + "</div>", tracks))
        self.assertEqual(["early", "late"], keys(result))
        self.assertEqual(["Early", "Late"], texts(result))
        self.assertEqual("ja", ET.fromstring(result).get("{http://www.w3.org/XML/1998/namespace}lang"))

    def test_interleaved_sections_preserve_ownership_comments_and_bounds(self):
        result = self.convert(document('\n<!-- verse -->\n<div itunes:song-part="Verse" begin="1s" end="6s">' +
                                       line("late", "5s", "6s", "VL") + "<!-- early verse -->" +
                                       line("early", "1s", "2s", "VE") + "<!-- verse tail --></div>\n" +
                                       '<div itunes:song-part="Chorus" begin="3s" end="4s">' +
                                       line("chorus", "3s", "4s", "C") + "</div>\n"))
        self.assertEqual(["early", "chorus", "late"], keys(result))
        sections = list(ET.fromstring(result).iter(f"{{{TTML}}}div"))
        self.assertEqual(["Verse", "Chorus", "Verse"], [section.get(f"{{{ITUNES}}}song-part") for section in sections])
        self.assertEqual(["1s", "3s", "5s"], [section.get("begin") for section in sections])
        self.assertEqual(["2s", "4s", "6s"], [section.get("end") for section in sections])
        for comment in ("<!-- verse -->", "<!-- early verse -->", "<!-- verse tail -->"):
            self.assertEqual(1, result.count(comment))

    def test_equal_starts_merge_across_sections(self):
        result = self.convert(document("<div>" + line("one", "1s", "2s", "One") + "</div>" +
                                       "<div>" + line("two", "1s", "3s", "Two") + "</div>"))
        self.assertEqual(["One\nTwo", " "], texts(result))

    def test_background_and_timed_translation_markup_survive(self):
        result = self.convert(document("<div>" + line("one", "1s", "2s", '<span begin="1s" end="2s">Word</span>') +
                                       '<p begin="1s" end="3s" itunes:key="two"><span begin="1s" end="3s">voice</span>'
                                       '<span ttm:role="x-bg"><span begin="1s" end="2s">back</span>'
                                       '<span ttm:role="x-translation">BG</span></span></p></div>'))
        self.assertEqual(["Word\n (BG)", " "], texts(result))
        self.assertIn('<span begin="1s" end="2s">Word</span>\n '
                      '<span xmlns="http://www.w3.org/ns/ttml" '
                      'xmlns:ttm="http://www.w3.org/ns/ttml#metadata" ttm:role="x-bg">(BG)</span>', result)

    def test_invalid_times_are_not_guessed(self):
        for invalid in ("bad", "NaN", "-1s", ""):
            with self.subTest(invalid=invalid):
                result = self.convert(document("<div>" + line("late", "10s", "11s") + line("unknown", invalid, "3s") + "</div>"))
                self.assertEqual(["late", "unknown"], keys(result))

    def test_ambiguous_or_unknown_track_keys_preserve_the_whole_apple_timeline(self):
        track = '<translations><translation><text for="late">Late</text></translation></translations>'
        for body in (line("late", "10s", "11s") + line("", "2s", "3s"),
                     line("late", "10s", "11s") + line("late", "2s", "3s"),
                     line("other", "10s", "11s") + line("early", "2s", "3s")):
            with self.subTest(body=body):
                raw = document(f"<div>{body}</div>", track)
                result = CONVERTER.to_apple_format(raw)
                self.assertFalse(result.converted)
                self.assertEqual(raw, result.ttml)

    def test_empty_sections_survive_and_self_closing_text_becomes_placeholder(self):
        tracks = '<translations><translation><text for="late">Late</text><text for="early"/></translation></translations>'
        result = self.convert(document("<div/><div>" + line("late", "10s", "11s") + line("early", "2s", "3s") + "</div>", tracks))
        self.assertIn("<div/>", result)
        self.assertEqual(["early", "late"], keys(result))
        self.assertEqual([" ", "Late"], texts(result))


    def test_synthesized_entries_preserve_quoted_and_entity_encoded_keys(self):
        tracks = ('<translations><translation><text for="known">T</text></translation></translations>'
                  '<transliterations><transliteration><text for="known">R</text></transliteration></transliterations>')
        for quoted_key in QUOTED_KEYS:
            with self.subTest(key=quoted_key):
                first = line("placeholder", "1s", "2s").replace('itunes:key="placeholder"', f"itunes:key={quoted_key}")
                raw = document("<div>" + first + line("known", "2s", "3s") + "</div>", tracks)
                expected = keys(raw)  # Parse the input before conversion.
                result = self.convert(raw)
                self.assertEqual(expected, keys(result))
                self.assert_track_keys(result, expected)
                self.assertEqual([" ", "T"], texts(result))
                self.assertEqual([" ", "R"], texts(result, "transliterations"))

    def test_migrated_tracks_preserve_quoted_and_entity_encoded_keys(self):
        for quoted_key in QUOTED_KEYS:
            with self.subTest(key=quoted_key):
                first = line("placeholder", "1s", "2s", "T", "R").replace('itunes:key="placeholder"', f"itunes:key={quoted_key}")
                raw = document("<div>" + first + line("known", "2s", "3s", "T2", "R2") + "</div>")
                expected = keys(raw)
                result = self.convert(raw)
                self.assertEqual(expected, keys(result))
                self.assert_track_keys(result, expected)
                self.assertEqual(["T", "T2"], texts(result))
                self.assertEqual(["R", "R2"], texts(result, "transliterations"))

    def assert_track_keys(self, ttml, expected):
        root = ET.fromstring(ttml)
        for kind in ("translations", "transliterations"):
            track = next(node for node in root.iter() if node.tag == f"{{{ITUNES}}}{kind}")
            self.assertEqual(expected, [node.get("for") for node in track.iter(f"{{{ITUNES}}}text")])


if __name__ == "__main__":
    unittest.main()
