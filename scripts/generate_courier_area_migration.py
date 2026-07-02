#!/usr/bin/env python3
"""Generate V32 courier area seed migration from client area definitions."""

from __future__ import annotations

import re
import unicodedata
from pathlib import Path

CITY_LIMITS = """
Colombo 1-15, Fort, Pettah, Slave Island, Kollupitiya, Bambalapitiya, Milagiriya, Kirilapone,
Thimbirigasyaya, Havelock Town, Narahenpita, Jawatte, Torrington, Cinnamon Gardens, Borella,
Dematagoda, Orugodawatte, Maradana, Maligawatte, Punchi Borella, Grand pass, Kotahena,
Bloemendhal, Mutuwella, Mattakkuliya
"""

SUBURBS = """
Angoda, Angulana, Attidiya, Athurugiriya, Battaramulla, Batakettara, Beddagana, Bellanwila,
Bokundara, Boralesgamuwa, Borupana, EgodaUyana, Dehiwala, Homagama, Hunupitiya, Kadawatha,
Kelaniya, Kiribathgoda, Kalubowila, Katubedda, Kesbewa, Kohuwala, Kotte, Kottawa, Kolonnawa,
Makola, Malabe, Mt. Lavinia, Maharagama, Moratuwa, Nawala, Nugegoda, Pepiliyana, Peliyagoda,
Piliyandala, Pannipitiya, Ratmalana, Rukmalgama, Sapugaskanda, Wattala, Wellampitiya
"""

OUTSTATIONS = """
Alma,Ambepussa,Achchuveli,Avissawella, Agalawatte, Abanpola, Aluthgama, Ahungalla, Ambalangoda,
Akurana, Anamaduwa, Anuradhapura, Ambewela, Ampara, Arachchikattuwa ,Akurassa, Algama,
Angunukolapalassa, Ambagasyala, Ambagastenna, Ambalakanda, Ambalantota ,Ambuluwawa, Amugoda,
Andaradeniya, Ahangama, Akmeemana, Attanagalla, Balapitiya, Badalgama, Baddegama, Batticaloa,
Benthota, Beruwala, Bulathsinhala, Boossa, Bulathkohupitiya, Bogaswewa, Bandaragama, Beragala,
Belummahra, Biyagama, Bingiriya ,Badulla, Bandarawela, BooOya, Balangoda, Bangadeniya, Balana,
Bambaragaswewa, Beliatta, Bibila, Buttala Bothale, Chilaw, Dambadeniya, Digana, Dankotuwa,
Dangolla, Dehiowita, Dediyagala, Dedugala, Delthota Dedigama Dankanda, Derannawa, Demodara,
Diyavinna, Dikwella, Dodangaslanda, Dompe, Dorawaka, Delgoda, Dambulla, Dummalasooriya, Erathna,
Embilipitiya, Ella, Enamulla, Eppawalla, Elpitiya, Eheliyagoda, Ganemulla, Galle, Giriulla,
Ginthota, Galewela, Galagedara, Galatara, Galapitamada, Galaha, Ganewalpola, Ginigathhena,
Goodwood, Gelioya, Gonawila, Galgamuwa, Gampola, Hanwella, Horana, Haputhale, Habarana,
Habaraduwa, Hambantota, Haliella, Hunukete, Hatton, Hikkaduwa, Hagala, Hakmana, Handessa,
Horape, Ittepana, ibbagamuwa, Ja-Ela, jaffna, Kadugannawa, Kandy, Kahathuduwa, Kandana,
Kaluthara, Katana, Katuwana, Katunayake, Kirindiwela, Karaitivu,Kallar,Kahawatte, Karaveddi,
Kalupahana, Katugastota, Kekirawa, Kitulgala, Kundasale, Koggala, Kuliyapitiya, Kurunegala,
Kegalle, Kuruwita, Kiriella, Kirillawala, Kobeigane, Kandiyapita, Kellapatha, Kepumgoda,
Ketandola, Kilinochchi, Koongahawela, Kotmale, Lenama Matugama, Meegoda, Minuwangoda,
Miriswatte, Matale, Morawaka, Maskeliya, Matara, Madiwaka Madampe, Mahawa, Mahawewa,
Mirigama, Monaragala, Nalanda, Naula, Naththandiya, Niwithigala, Navatkuli, Nelliady,
Norochchole, Nalluruwa, Nindur, Narammala, Nikaweratiya, NuwaraEliya, Neluyaya,Niyagama,
Nochchiyagama, Nainamadama, Negombo, Nittabuwa, Padukka, Pugoda, Padeniya, Palavi,
Pallebedda, Pelmadulla, Pasikuda, Puttlam, Padalangala, Pannala, Pothuhera, Polgahawela,
Pokunuwita, Pinnawala, Pitigala, Peradeniya, Pelampitiya Padiyathalawa,Polonnaruwa, Palagala,
Pallegama, Passara, Pussellawa,Rambukkana, Ruwanwella, Rajanganaya, Rathnapura, Ranna,
Ramboda, Ragama, Ranajayapura, Rakwana, Seeduwa, Sangarajapura, Trincomalee, Talawa,
Thalawakele, Tabuththegama, Thangalle ,Tewatta, Wariyapola, Wattegama Weligama, Welimada,
Wennapuwa, Warakapola, Weyangoda,Wadduwa, Vavuniya Vitharandeniya,Yakkala, Yatiyanthota
"""

FARAWAY = """
Adikarigama, Agarapatana, Agarapatana Farm, Agbopura, Akiriyankumbura, Akkara 100, Akkara Panaha,
Akkaraseeya, Akkarayankulam, Ambagaswewa, Ampagala -Kegalle, Ampalavanpokkanai, Ampilanthurai,
Analai Theevu, Anandapuram Kilinochchi, Aranaganvila, Aranthalawa, Arugambay, Balaluwewa,
Balaharuwa, Ballaketuwa, Bambarakele, Belwood, Beminiyangala, Bopaththalawa, Bopitiya-Kegalle,
Budugekanda, Bulupitiya, Buluthota, Bundala, Calsey, Chandanagama, Chenaiyoor, Dahaiyagala,
Daluggolla, Delf, Dellawa, Deniyaya, Derangala, Dewalakanda, Dewramvehera, Dickyaya, Diddeniya,
Dimbulagala, Galoya, Gammaduwa, Hadigalla Halgolla Estate, Halmillagolla, Hatharaskotuwa,
Higurukaduwa, Hinguralakanda, Hiniduma, Horowpathana, Hulankapoola, Kahatagollewa, Kajuwatta,
Kakkaddicholai, Kala Oya, Kalahagala, Kalawana, Kalaweldeniya, Kallar (Kanthale), Kallar(Ampara),
Kalugala - Ududumbara, Katharagama, Kathiravely, Kathnoruwa, Kaudulla, Kayts, Kebithigollewa,
Keenagahawila, Keerthibandarapura, Ketawela, Kew Estate, Kiliwetti, Kirinda, Kiriwaneliya,
Kiriweldola, Kolamanthalawa, Kolapathana, Kyts, Lagamuwa, Laggala, Lahugala, Lakshapana, Leula,
Lewaland, Lihiniyagama, Lindawewa, Lipakele, Madaganoya, Madolsima, Madu, Madugalla, Madulkele,
Mahagirilla, Mahanagapura, Mahaoya, Mahasenpura-Tissa, Makandura, Makuldeniya, Makulpotha,
Medirigiriya, Minneriya, Minuwangete, Molkawa, Mulana, Mulankavil, Mulativ, Mullayaveli,
Mullegama - Ampara, Mulliyawalai, Neluwa, Opatha, Padavisiripura, Padaviya, Padiyapalalla,
Parayankulam, Pelawatte (Kalutara), Peraso, Periyamadu, Periyaneelavanai, Pitapola, Point Pedro,
Pothuvil, Punani, Puthukudeirippu (Kili), Rajagalathenna, Rantabe, Ridee Ella, Rideebendi Ella,
Ridimaliyadda, Ridiyagama, Rikillagaskada, Ritigala, Samanalatenna, Samanalawatte - Balangoda,
Sellakataragama, Sembuwatta, Senapura, Sirikanduyaya, Siripura, Sirisethagama, Sithankerney,
Sithul Pahuwa, Siyabalagoda, Siyabalagoda - Matara, Skanthapuram, Somapura, Somawathiya,
Sooriya Ara, Sooriyakanda Emb, Sooriyakanda -Kahawatte, Sripura, Srithissapura, Talai Mannar,
Thanamalvila, Thanthirimale, Thondammanaru, Thoppigala, Thopur, Thudawa, Thunukkai, Tirukkovil,
Tondamannar, Torington, Uda Delwala, Uda Mattala, Urani - Monaragala, Uva Thenna, Uwakele,
Uyangalla, Vijayapura - Polonnaruwa, Vijithapura, Wadihitikanda, Wilaoya, Willgamuwa, Willpaththu,
Yagirala (Yagiraliya), Yakalla, Yala, Yalagamuwa, Yalkumbura, Yalwela, Yan Oya, Yaththalgoda,
Yatipawwa, Yattapatha
"""

ALIASES: list[tuple[str, str, str]] = [
    # (normalized_key, display_name, zone) — keys must already be normalized
    ("mountlavinia", "Mt. Lavinia", "SUBURBS"),
    ("dehiwalamountlavinia", "Dehiwala", "SUBURBS"),
    ("srijayawardenepurakotte", "Kotte", "SUBURBS"),
    ("jaela", "Ja-Ela", "OUTSTATION"),
    ("nuwaraeliya", "Nuwara Eliya", "OUTSTATION"),
    ("ratnapura", "Ratnapura", "OUTSTATION"),
    ("rathnapura", "Ratnapura", "OUTSTATION"),
    ("colombo", "Colombo", "CITY_LIMITS"),
    ("colombo115", "Colombo 1-15", "CITY_LIMITS"),
    ("grandpass", "Grand pass", "CITY_LIMITS"),
    ("cinnamongardens", "Cinnamon Gardens", "CITY_LIMITS"),
    ("havelocktown", "Havelock Town", "CITY_LIMITS"),
    ("slaveisland", "Slave Island", "CITY_LIMITS"),
    ("punchiborella", "Punchi Borella", "CITY_LIMITS"),
    ("bentota", "Benthota", "OUTSTATION"),
    ("kataragama", "Katharagama", "FARAWAY"),
]


def normalize(name: str) -> str:
    s = unicodedata.normalize("NFKD", name)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", "", s)
    return s


def split_names(blob: str) -> list[str]:
    parts = re.split(r"[,;]+", blob)
    out: list[str] = []
    for p in parts:
        name = p.strip()
        if not name:
            continue
        # split accidental glued pairs like "Madiwaka Madampe"
        if " " in name and len(name) > 30:
            for sub in name.split():
                if len(sub) > 2:
                    out.append(sub)
        else:
            out.append(name)
    return out


def collect(zone: str, blob: str) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for display in split_names(blob):
        norm = normalize(display)
        if not norm or norm in seen:
            continue
        seen.add(norm)
        rows.append((norm, display.strip(), zone))
    return rows


def main() -> None:
    rows: list[tuple[str, str, str]] = []
    rows.extend(collect("CITY_LIMITS", CITY_LIMITS))
    rows.extend(collect("SUBURBS", SUBURBS))
    rows.extend(collect("OUTSTATION", OUTSTATIONS))
    rows.extend(collect("FARAWAY", FARAWAY))

    for norm, display, zone in ALIASES:
        if norm not in {r[0] for r in rows}:
            rows.append((norm, display, zone))

    # far away wins over outstation on duplicate normalized keys
    priority = {"FARAWAY": 3, "OUTSTATION": 2, "SUBURBS": 1, "CITY_LIMITS": 0}
    merged: dict[str, tuple[str, str, str]] = {}
    for norm, display, zone in rows:
        if norm not in merged or priority[zone] > priority[merged[norm][2]]:
            merged[norm] = (norm, display, zone)

    final = sorted(merged.values(), key=lambda r: (r[2], r[1].lower()))

    lines = [
        "-- V32 — Citrek courier area definitions (client document, 2026).",
        "-- City name lookup at checkout; postal_zone updated as fallback.",
        "",
        "CREATE TABLE IF NOT EXISTS courier_area (",
        "    name_normalized VARCHAR(120) PRIMARY KEY,",
        "    display_name    VARCHAR(200) NOT NULL,",
        "    courier_zone    VARCHAR(32)  NOT NULL",
        "        CHECK (courier_zone IN ('CITY_LIMITS','SUBURBS','OUTSTATION','FARAWAY'))",
        ");",
        "",
        "TRUNCATE courier_area;",
        "",
        "INSERT INTO courier_area (name_normalized, display_name, courier_zone) VALUES",
    ]

    value_lines = []
    for norm, display, zone in final:
        esc_display = display.replace("'", "''")
        value_lines.append(f"    ('{norm}','{esc_display}','{zone}')")
    lines.append(",\n".join(value_lines) + ";")
    lines.append("")
    lines.append("-- Postal-code fallbacks (when city not matched)")
    lines.append("UPDATE postal_zone SET courier_zone = 'CITY_LIMITS'")
    lines.append("  WHERE postal_code >= '00100' AND postal_code <= '01500';")
    lines.append("")
    lines.append("UPDATE postal_zone SET courier_zone = 'SUBURBS'")
    lines.append("  WHERE district IN ('Colombo','Gampaha','Kalutara')")
    lines.append("    AND NOT (postal_code >= '00100' AND postal_code <= '01500');")
    lines.append("")
    lines.append("UPDATE postal_zone SET courier_zone = 'FARAWAY'")
    lines.append("  WHERE district IN ('Mannar','Mullaitivu');")
    lines.append("")
    lines.append("UPDATE postal_zone SET courier_zone = 'OUTSTATION'")
    lines.append("  WHERE district NOT IN ('Colombo','Gampaha','Kalutara','Mannar','Mullaitivu');")
    lines.append("")

    out = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration/V32__courier_area_definitions.sql"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(final)} areas to {out}")


if __name__ == "__main__":
    main()
