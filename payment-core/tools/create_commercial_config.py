#!/usr/bin/env python3
"""Create a NEW private config from public receiver metadata; never enable real receivers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receivers', required=True, type=Path, help='Public JSON list: id, name, phone, qrFile, deviceId')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--client-id', default='shop')
    parser.add_argument('--allow-loopback-http', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,40}', args.client_id):
        parser.error('Invalid client ID')
    entries = json.loads(args.receivers.read_text())
    if not isinstance(entries, list) or not entries:
        parser.error('At least one receiver is required')
    receivers, devices = [], []
    for entry in entries:
        phone = re.sub(r'\D', '', entry['phone'])
        if len(phone) == 9:
            phone = '51' + phone
        if not re.fullmatch(r'519\d{8}', phone):
            parser.error('Receiver phone must be a Peruvian mobile number')
        if not all(re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', entry[field]) for field in ['id', 'deviceId']):
            parser.error('Invalid receiver/device identity')
        if not entry['name'].strip() or len(entry['name']) > 100:
            parser.error('Invalid receiver name')
        receivers.append(dict(id=entry['id'], name=entry['name'], phone=phone,
                              qrSha256=hashlib.sha256(Path(entry['qrFile']).read_bytes()).hexdigest(),
                              verified=False, enabled=False))
        devices.append(dict(id=entry['deviceId'], key=secrets.token_hex(32), receiverId=entry['id']))
    if any(len(set(row[field] for row in rows)) != len(rows) for rows, field in [(receivers, 'id'), (receivers, 'phone'), (devices, 'id')]):
        parser.error('Duplicate receiver, account phone or device')
    config = dict(matchSecret=secrets.token_hex(32), clients=[dict(id=args.client_id, key=secrets.token_hex(32), receivers=[r['id'] for r in receivers])],
                  receivers=receivers, devices=devices, allowLocalHttp=args.allow_loopback_http, ttlSeconds=300)
    # Exclusive creation protects existing keys and HMAC history from accidental replacement.
    descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        json.dump(config, stream, indent=2, ensure_ascii=False)
        stream.write('\n')
    print(f'Private configuration created: {args.output}; {len(receivers)} receivers disabled. No secrets printed.')


if __name__ == '__main__':
    main()
