from copy import deepcopy
import unittest

from scripts.ci_protection import LEGACY, TARGET, require_live_gate, status_payload


def policy(names, strict=True):
    return {'strict': strict, 'checks': [{'context': n, 'app_id': 15368} for n in sorted(names)]}


class CiProtectionTest(unittest.TestCase):
    def test_only_required_checks_are_updated_and_strict_app_binding_survives(self):
        old = policy(LEGACY)
        original = deepcopy(old)
        result = status_payload(old, policy(TARGET))
        self.assertEqual(original, old)
        self.assertEqual({'checks', 'strict'}, set(result))
        self.assertEqual(TARGET, {c['context'] for c in result['checks']})
        self.assertEqual(policy(LEGACY), status_payload(policy(TARGET), policy(LEGACY)))

    def test_loose_mode_preserves_both_required_checks_and_allows_explicit_restore(self):
        current = policy(TARGET, strict=False)
        self.assertEqual(current, status_payload(current, current))
        self.assertEqual(policy(TARGET, strict=False), status_payload(policy(LEGACY), current))
        self.assertEqual(policy(TARGET, strict=True), status_payload(current, policy(TARGET)))
        self.assertEqual(policy(LEGACY), status_payload(current, policy(LEGACY)))

    def test_strict_value_must_be_an_actual_boolean(self):
        for invalid in (None, 0, 1, 'false', 'true'):
            for current, target in ((policy(TARGET, invalid), policy(TARGET, False)),
                                    (policy(TARGET, False), policy(TARGET, invalid))):
                with self.subTest(invalid=invalid):
                    with self.assertRaises(ValueError):
                        status_payload(current, target)

    def test_unexpected_checks_or_app_cannot_be_silently_erased(self):
        for bad in (policy(LEGACY | {'new-admin-check'}), policy({'governance'})):
            with self.assertRaises(ValueError):
                status_payload(bad, policy(TARGET))
        bad = policy(LEGACY)
        bad['checks'][0]['app_id'] = 99
        with self.assertRaises(ValueError):
            status_payload(bad, policy(TARGET))

    def test_main_needs_actual_success_from_the_correct_app(self):
        good = {'name': 'pr-gate', 'conclusion': 'success', 'status': 'completed', 'app': {'id': 15368}}
        require_live_gate([good])
        for changes in ({'conclusion': 'skipped'}, {'status': 'in_progress'}, {'app': {'id': 99}}):
            with self.assertRaises(ValueError):
                require_live_gate([good | changes])
        with self.assertRaises(ValueError):
            require_live_gate([])
