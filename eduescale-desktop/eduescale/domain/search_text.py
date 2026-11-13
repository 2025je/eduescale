import unicodedata


def normalize_search_text(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value).casefold()
    characters: list[str] = []
    for character in decomposed:
        if unicodedata.combining(character):
            if character == "\u0303" and characters and characters[-1] == "n":
                characters.append(character)
            continue
        characters.append(character)
    return unicodedata.normalize("NFC", " ".join("".join(characters).split()))


def normalize_search_text_legacy(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()
