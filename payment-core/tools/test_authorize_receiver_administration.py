import json
import os
from pathlib import Path
import tempfile
import unittest
from authorize_receiver_administration import authorize

class ScopeProvisioningTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'commercial.json'
        self.data = {'clients': [{'id': 'shop', 'key': 'fixture-only', 'receivers': ['r1']}], 'receivers': [{'id': 'r1', 'verified': False, 'enabled': False}], 'matchSecret': 'fixture-only', 'devices': []}
        self.path.write_text(json.dumps(self.data))
        os.chmod(self.path, 0o600)
    def test_only_scope_changes_with_private_backup_and_no_repeated_write(self):
        self.assertTrue(authorize(self.path, 'shop'))
        self.assertFalse(authorize(self.path, 'shop'))
        changed = json.loads(self.path.read_text())
        changed['clients'][0].pop('receiverAdministration')
        self.assertEqual(self.data, changed)
        backups = list(self.path.parent.glob('*.before-*'))
        self.assertEqual(1, len(backups))
        self.assertEqual(self.data, json.loads(backups[0].read_text()))
        self.assertEqual(0o600, backups[0].stat().st_mode & 0o777)
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)
    def test_unknown_client_changes_nothing(self):
        with self.assertRaises(ValueError): authorize(self.path, 'other')
        self.assertEqual(self.data, json.loads(self.path.read_text()))
        self.assertEqual(1, len(list(self.path.parent.iterdir())))
    def test_public_read_permissions_rejected(self):
        os.chmod(self.path, 0o640)
        with self.assertRaises(ValueError): authorize(self.path, 'shop')
    def test_symlink_rejected(self):
        link = self.path.parent / 'link'
        link.symlink_to(self.path)
        with self.assertRaises(ValueError): authorize(link, 'shop')

if __name__ == '__main__': unittest.main()
