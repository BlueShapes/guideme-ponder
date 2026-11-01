"""Compare authored entrances with independently audited, pinned registry/guide items."""
import json
from pathlib import Path

from localization import source_hash, sources

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def check():
    audit = read(ROOT / "docs/item-coverage.json")
    fixture = read(ROOT / "src/validation/resources/assets/ae2ponder/validation/item_coverage.json")
    lessons = read(ROOT / "src/main/resources/assets/ae2ponder/guide/lessons.json")
    lessons += read(ROOT / "src/main/resources/assets/ae2ponder/guide/addons.json")
    descriptions = read(ROOT / "src/main/resources/assets/ae2ponder/guide/item_descriptions.json")
    by_item = {item: entry for entry in descriptions for item in entry["items"]}
    assert fixture["sourceHash"] == source_hash(sources()), "Stale runtime fixture source hash"
    assert audit["baselineCommit"] == "c11d7de82a66e064ec0de328f070f36c5635d10c"
    for platform, inventory in audit["platforms"].items():
        targets = fixture["platforms"][platform]
        expected = {row["itemId"] for row in inventory["items"]}
        assert len(expected) == len(inventory["items"]), "Duplicate audited item"
        assert expected == set(targets["playerItems"] + targets["addonItems"])
        assert set(inventory["excludedItems"]) == set(targets["excludedItems"])
        assert not expected.intersection(targets["excludedItems"])
        for row in inventory["items"]:
            item = row["itemId"]
            actual = {"ponder": ["@native_course_all_lessons"] if item == "ae2:guide" else [
                lesson["id"] for lesson in lessons
                if item in set(lesson.get("components", [])) | {lesson["icon"]}],
                "description": by_item[item]["id"] if item in by_item else None}
            assert actual == row["after"], f"Changed reviewed entrance: {platform}/{item}: {actual}"
            assert actual["ponder"] or actual["description"], f"No entrance: {item}"
            if row["origin"] == "ae2":
                core_scene = item == "ae2:guide" or any(
                    not lesson.get("requiresMods") and item in set(lesson.get("components", [])) | {lesson["icon"]}
                    for lesson in lessons)
                core_description = item in by_item and not by_item[item].get("requiresMods")
                assert core_scene or core_description, f"Core item relies on an optional add-on entrance: {item}"
            if actual["description"]:
                mods = by_item[item].get("requiresMods", [])
                assert mods == ([] if row["origin"] == "ae2" else [row["origin"]]), f"Wrong optional guard: {item}"
        for family, spec in targets["cableFamilies"].items():
            assert len(spec["items"]) == 17 and len(set(spec["items"])) == 17
            courses = [[lesson["id"] for lesson in lessons
                        if item in set(lesson.get("components", [])) | {lesson["icon"]}]
                       for item in spec["items"]]
            assert all(course == courses[0] for course in courses), f"Color course mismatch: {family}"
            assert courses[0][0] == spec["firstLesson"], f"Wrong first scene: {family}"
        print(f"PASS {platform}: {len(targets['playerItems'])} core + {len(targets['addonItems'])} add-on items; "
              f"85 cables; {len(targets['excludedItems'])} documented exclusions; no missing entrance")


if __name__ == "__main__":
    check()
