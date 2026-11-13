"""Writes a CSV of 10,000 items for the performance test (INV-30, A9), in the Import page's format (A42).

    python3 perf/generate_seed.py > perf/seed-10000.csv

The same items every time (fixed random seed), all with SKUs starting PERF- and categories starting "Perf ", so they
are easy to tell apart from real items. Every item starts above its reorder level: an item created low raises a
low-stock alert (A14), and 10,000 of those would mean 10,000 emails.
"""
import csv
import random
import sys

COUNT = 10_000
CATEGORIES = ["Perf Stationery", "Perf Cables", "Perf Paper", "Perf Tools", "Perf Cleaning", "Perf Furniture",
              "Perf Kitchen", "Perf Packaging"]
NOUNS = ["pen", "cable", "notebook", "stapler", "folder", "marker", "tape", "envelope", "binder", "mug", "chair",
         "box", "label", "cleaner", "brush", "battery", "charger", "lamp", "clip", "ruler"]
ADJECTIVES = ["blue", "black", "red", "green", "large", "small", "heavy-duty", "recycled", "premium", "basic"]


def rows(rng):
    for number in range(1, COUNT + 1):
        reorder_level = rng.randint(5, 50)
        yield {
            "sku": f"PERF-{number:05d}",
            "name": f"{rng.choice(ADJECTIVES).capitalize()} {rng.choice(NOUNS)} {number}",
            "category": rng.choice(CATEGORIES),
            "quantity": reorder_level + rng.randint(1, 500),
            "reorder_level": reorder_level,
            "unit_price": f"{rng.uniform(0.1, 250):.2f}",
        }


def main():
    writer = csv.DictWriter(sys.stdout, fieldnames=["sku", "name", "category", "quantity", "reorder_level",
                                                    "unit_price"], lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows(random.Random(30)))


if __name__ == "__main__":
    main()
