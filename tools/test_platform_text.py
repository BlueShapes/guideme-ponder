import copy
import unittest

from localization import placeholders
from platform_text import review_hash, text_hash, validate_overrides


class PlatformTextChecks(unittest.TestCase):
    def setUp(self):
        self.key = "ae2ponder.ponder.shared.meteorite_0"
        self.base = {"fr_fr": {self.key: "La boussole indique le cube"}}
        text = "La boussole repère un Mysterious Cube proche…\n…dont la traduction proposée est « Cube Mystérieux »"
        roles = ["NORMAL", "NORMAL"]
        self.document = {"_source_hash": "source", "locales": {"fr_fr": {self.key: {
            "baseTextSha256": text_hash(self.base["fr_fr"][self.key]), "text": text, "roles": roles,
            "_review_hash": review_hash("fr_fr", self.key, text, roles)}}}}

    def check(self):
        return validate_overrides(self.document, self.base, "source", [], placeholders)

    def test_reviewed_override_does_not_mutate_shared_original(self):
        before = copy.deepcopy(self.base)
        self.assertEqual(self.check(), 1)
        self.assertEqual(self.base, before)

    def test_changed_shared_translation_requires_base_review(self):
        self.base["fr_fr"][self.key] += " proche"
        with self.assertRaisesRegex(AssertionError, "Stale platform base"):
            self.check()

    def test_changed_text_or_role_requires_review(self):
        self.document["locales"]["fr_fr"][self.key]["roles"][0] = "HINT"
        with self.assertRaisesRegex(AssertionError, "Stale platform text/role"):
            self.check()

    def test_roles_must_follow_authored_boundaries(self):
        self.document["locales"]["fr_fr"][self.key]["roles"].pop()
        with self.assertRaisesRegex(AssertionError, "roles must match"):
            self.check()

    def test_unknown_locale_is_rejected(self):
        self.document["locales"]["xx_xx"] = self.document["locales"].pop("fr_fr")
        with self.assertRaisesRegex(AssertionError, "Unsupported platform locale"):
            self.check()

    def test_unknown_key_is_rejected(self):
        self.document["locales"]["fr_fr"]["unknown"] = self.document["locales"]["fr_fr"].pop(self.key)
        with self.assertRaisesRegex(AssertionError, "Unknown platform text key"):
            self.check()


if __name__ == "__main__":
    unittest.main()
