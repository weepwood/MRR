import hashlib
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("evaluate", Path(__file__).with_name("evaluate.py"))
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class IndependentEvaluationTest(unittest.TestCase):
    def row(self):
        return {"groupHash": hashlib.sha256(b"synthetic-validation").hexdigest(), "expectedType": 3,
                "pageHash": hashlib.sha256(b"synthetic-page").hexdigest(),
                "predictedType": 3, "autoEligible": True, "clearPrinted": True,
                "referenceText": "合成正文", "recognizedText": "合成正文"}

    def test_rejects_development_group_overlap(self):
        row = self.row()
        with self.assertRaises(ValueError):
            evaluation.evaluate([row], {row["groupHash"]}, "rules", "a" * 64)

    def test_errors_and_cer_are_measured_without_patient_text_in_report(self):
        row = self.row()
        row["predictedType"] = 5
        row["recognizedText"] = "合成文"
        result = evaluation.evaluate([row], set(), "rules", "a" * 64)
        self.assertEqual(result["autoErrors"], 1)
        self.assertEqual(result["clearPrintedCer"], 0.25)
        self.assertNotIn("referenceText", result)
        self.assertEqual(result["byType"]["5"]["errors"], 1)

    def test_unknown_page_is_not_automatically_other(self):
        row = self.row()
        row.update(expectedType=0, predictedType=None, autoEligible=False)
        result = evaluation.evaluate([row], set(), "rules", "a" * 64)
        self.assertEqual(result["autoCandidates"], 0)

    def test_duplicate_pages_cannot_inflate_independent_sample_count(self):
        row = self.row()
        with self.assertRaises(ValueError):
            evaluation.evaluate([row, row], set(), "rules", "a" * 64)


if __name__ == "__main__":
    unittest.main()
