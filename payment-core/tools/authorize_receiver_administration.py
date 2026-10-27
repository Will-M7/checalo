#!/usr/bin/env python3
"""Opt-in a trusted Laravel server client. Does NOT verify/enable receivers or generate keys."""
import argparse
import json
import os
from pathlib import Path
import stat
import tempfile
import time


def authorize(path: Path, client_id: str) -> bool:
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
        raise ValueError('Configuration must be a private regular file owned by the operator (0600).')
    original = path.read_bytes()
    config = json.loads(original)
    selected = [c for c in config['clients'] if c['id'] == client_id]
    if len(selected) != 1:
        raise ValueError('Select exactly one existing trusted server client.')
    if selected[0].get('receiverAdministration') is True:
        return False
    selected[0]['receiverAdministration'] = True
    backup = path.with_name(path.name + '.before-receiver-admin-' + str(time.time_ns()))
    with os.fdopen(os.open(backup, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'wb') as f:
        f.write(original)
        f.flush()
        os.fsync(f.fileno())
    fd, staged = tempfile.mkstemp(prefix='.receiver-admin-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as f:
            json.dump(config, f, indent=2)
            f.write('\n')
            f.flush()
            os.fsync(f.fileno())
        current = path.lstat()
        if current.st_ino != info.st_ino or current.st_mtime_ns != info.st_mtime_ns or path.read_bytes() != original:
            raise ValueError('Configuration changed concurrently; nothing replaced.')
        os.replace(staged, path)
    finally:
        Path(staged).unlink(missing_ok=True)
    return True


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--client', required=True)
    parser.add_argument('--confirm-trusted-laravel-server', action='store_true', required=True,
                        help='Explicitly delegate receiver administration to the existing private server credential.')
    args = parser.parse_args()
    try:
        changed = authorize(args.config, args.client)
        print('Administrative scope saved; private backup retained. Receiver states and credentials unchanged. Restart Core manually to load.' if changed else 'Scope already present; no changes.')
    except (OSError, ValueError, KeyError):
        raise SystemExit('Scope not changed. Check ownership, permissions, existing client and concurrent changes; no secrets displayed.')
