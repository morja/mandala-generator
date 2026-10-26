"""Add a paragraph with the guide link before the open-source line of every listing (26 October 2026).

    python3 -I docs/store/play/play_guide_line.py docs/store/play [--write]

Without --write it only prints what would change. German links to the German guide, every other
language to the English one, saying so. Listings that already contain the link are left alone.
"""
import sys; sys.path.insert(0, sys.argv[1]); from play_api import *
WRITE = "--write" in sys.argv
GUIDE = "https://www.tearoom.one/{}/apps/dahlia/help"
SOURCE = "https://github.com/morja/mandala-generator"
LINE = {
    "en-US": "Guide and answers to common questions: ",
    "de-DE": "Anleitung und Antworten auf häufige Fragen: ",
    "es-ES": "Guía y preguntas frecuentes (en inglés): ",
    "es-419": "Guía y preguntas frecuentes (en inglés): ",
    "pt-BR": "Guia e perguntas frequentes (em inglês): ",
    "fr-FR": "Guide et questions fréquentes (en anglais) : ",
    "zh-CN": "使用指南与常见问题（英文）：",
    "ja-JP": "ガイドとよくある質問（英語）：",
    "ar": "الدليل والأسئلة الشائعة (بالإنجليزية): ",
    "cs-CZ": "Návod a časté dotazy (v angličtině): ",
    "da-DK": "Vejledning og ofte stillede spørgsmål (på engelsk): ",
    "fi-FI": "Opas ja usein kysytyt kysymykset (englanniksi): ",
    "hi-IN": "गाइड और अक्सर पूछे जाने वाले प्रश्न (अंग्रेज़ी में): ",
    "hu-HU": "Útmutató és gyakori kérdések (angolul): ",
    "id": "Panduan dan pertanyaan umum (dalam bahasa Inggris): ",
    "it-IT": "Guida e domande frequenti (in inglese): ",
    "iw-IL": "מדריך ושאלות נפוצות (באנגלית): ",
    "ko-KR": "가이드 및 자주 묻는 질문(영어): ",
    "nl-NL": "Handleiding en veelgestelde vragen (in het Engels): ",
    "no-NO": "Veiledning og vanlige spørsmål (på engelsk): ",
    "pl-PL": "Przewodnik i najczęstsze pytania (po angielsku): ",
    "ro": "Ghid și întrebări frecvente (în engleză): ",
    "ru-RU": "Руководство и ответы на частые вопросы (на английском): ",
    "sk": "Návod a časté otázky (v angličtine): ",
    "sv-SE": "Guide och vanliga frågor (på engelska): ",
    "th": "คู่มือและคำถามที่พบบ่อย (ภาษาอังกฤษ): ",
    "tr-TR": "Kılavuz ve sık sorulan sorular (İngilizce): ",
    "uk": "Посібник і відповіді на часті запитання (англійською): ",
    "vi": "Hướng dẫn và câu hỏi thường gặp (tiếng Anh): ",
}
eid = call("POST", "/edits", {})["id"]
try:
    changed = 0
    for l in call("GET", f"/edits/{eid}/listings")["listings"]:
        lang, full = l["language"], l["fullDescription"]
        if "tearoom.one" in full and "/apps/dahlia/help" in full:
            print("has it:", lang); continue
        assert lang in LINE, f"no line for {lang}"
        lines = full.split("\n")
        at = max(i for i, s in enumerate(lines) if SOURCE in s)
        guide = LINE[lang] + GUIDE.format("de" if lang == "de-DE" else "en")
        l["fullDescription"] = "\n".join(lines[:at] + [guide, ""] + lines[at:])
        assert len(l["fullDescription"]) <= 4000, (lang, len(l["fullDescription"]))
        print(lang, len(l["fullDescription"]), "|", guide)
        if WRITE: call("PUT", f"/edits/{eid}/listings/{lang}", l)
        changed += 1
    if WRITE and changed:
        call("POST", f"/edits/{eid}:validate", {}); print("commit:", call("POST", f"/edits/{eid}:commit", {}))
    else:
        call("DELETE", f"/edits/{eid}", raw=True); print("dry run, nothing written" if not WRITE else "nothing to change")
except Exception as e:
    print("failed, deleting edit:", e); call("DELETE", f"/edits/{eid}", raw=True); raise
