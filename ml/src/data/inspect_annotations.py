"""Mostra as classes encontradas no COCO e quantas caixas há de cada uma, por split."""

import csv
from collections import Counter

from src.data.dataset import BANKNOTES, COINS, REPORTS_DIR, SOURCE_DIR, SPLITS, load_split, print_table


def main() -> None:
    print(f"Origem: {SOURCE_DIR}\n")
    counts = {split: Counter(b.category for s in load_split(split) for b in s.boxes) for split in SPLITS}

    rows = []
    for category in [*BANKNOTES, *sorted(COINS)]:
        kind = "banknote" if category in BANKNOTES else "coin (discarded)"
        per_split = [counts[split][category] for split in SPLITS]
        rows.append([category, kind, *per_split, sum(per_split)])
    rows.append(["TOTAL", "", *(sum(counts[s].values()) for s in SPLITS), sum(sum(c.values()) for c in counts.values())])

    header = ["class", "type", *SPLITS, "total"]
    print_table(rows, header)

    REPORTS_DIR.mkdir(exist_ok=True)
    with (REPORTS_DIR / "box_counts.csv").open("w", newline="", encoding="utf-8") as file:
        csv.writer(file).writerows([header, *rows])


if __name__ == "__main__":
    main()
