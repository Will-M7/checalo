#!/usr/bin/env python3
"""New compiled Core classes + Laravel isolated tests. Never overwrites the running distribution."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.request

laravel = Path(__file__).resolve().parents[1]
core = Path(os.environ.get('RECEIVER_TEST_CORE_PROJECT', str(laravel.parent / 'payment-core'))).resolve()
classes = core / 'build/classes/kotlin/main'
libs = core / 'build/install/qr/lib'
if not (classes / 'pe/willian/qrlab/server/ReceiverAdministration.class').is_file() or not libs.is_dir():
    raise SystemExit('Compile Core classes/tests first; runtime dependency jars from the existing distribution are required. Do not overwrite a running distribution.')
with tempfile.TemporaryDirectory(prefix='huata-receiver-test-') as directory:
    work = Path(directory)
    os.chmod(work, 0o700)
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        port = probe.getsockname()[1]
    assert port not in (8000, 8087)
    cfg = {
        'matchSecret': secrets.token_urlsafe(48),
        'clients': [{'id': 'wifi_suite', 'key': secrets.token_urlsafe(48), 'receivers': ['r1', 'r2', 'r3'], 'receiverAdministration': True}],
        'receivers': [
            {'id': 'r1', 'name': 'Receptor ficticio WiFi', 'phone': '51999999999', 'qrSha256': hashlib.sha256(b'fixture-image').hexdigest(), 'verified': True, 'enabled': True},
            {'id': 'r2', 'name': 'No verificado ficticio', 'phone': '51988888888', 'qrSha256': 'b' * 64},
            {'id': 'r3', 'name': 'Titular administrativo ficticio', 'phone': '977777777', 'qrSha256': hashlib.sha256(b'fixture-image').hexdigest()}],
        'devices': [{'id': f'fixture-device-{n}', 'key': secrets.token_urlsafe(48), 'receiverId': f'r{n}'} for n in (1, 2, 3)],
        'allowLocalHttp': True,
    }
    private = work / 'commercial.json'
    with os.fdopen(os.open(private, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'w') as f:
        json.dump(cfg, f)
    env = {**os.environ, 'PAYMENT_MODE': 'commercial', 'PAYMENT_HOST': '127.0.0.1', 'PAYMENT_PORT': str(port), 'PAYMENT_CONFIG_FILE': str(private), 'PAYMENT_DB_PATH': str(work / 'commercial.sqlite')}
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    classpath = os.pathsep.join((str(classes), str(core / 'build/resources/main'), str(libs / '*')))
    url = f'http://127.0.0.1:{port}'
    print('Pruebas de administración contra Core aislado, SQLite temporal y datos ficticios.', flush=True)
    with (work / 'core.log').open('w') as log:
        process = subprocess.Popen([java, '-cp', classpath, 'pe.willian.qrlab.MainKt', 'server'], cwd=work, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(150):
                if process.poll() is not None:
                    raise RuntimeError('No inició Core temporal; sin cambios operativos.')
                try:
                    with urllib.request.urlopen(url + '/health', timeout=.3) as response:
                        if json.load(response).get('mode') == 'commercial':
                            break
                except (OSError, ValueError):
                    pass
                time.sleep(.1)
            else:
                raise RuntimeError('Core temporal no respondió.')
            testenv = {**os.environ, 'APP_ENV': 'testing', 'DB_CONNECTION': 'sqlite', 'DB_DATABASE': ':memory:', 'DB_URL': '', 'CACHE_STORE': 'array', 'SESSION_DRIVER': 'array', 'QUEUE_CONNECTION': 'sync',
                       'RECEIVER_TEST_CORE_URL': url, 'RECEIVER_TEST_CORE_CONFIG': str(private), 'BRIDGE_TEST_CORE_URL': url, 'BRIDGE_TEST_CORE_CONFIG': str(private)}
            result = subprocess.run(['php', 'vendor/bin/pest', '--compact', 'tests/Feature/ReceptoresCoreIntegrationTest.php', 'tests/Feature/YapeBridgeIntegrationTest.php'], cwd=laravel, env=testenv)
            if result.returncode:
                raise SystemExit(result.returncode)
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            print('Core temporal detenido y credenciales ficticias retiradas. Servicios manuales intactos.', flush=True)
