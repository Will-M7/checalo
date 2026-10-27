#!/usr/bin/env python3
"""Integración aislada Laravel ↔ Core; crea solo /tmp, usa puerto efímero y termina su propio proceso."""
from pathlib import Path
import os, sys, json, secrets, tempfile, socket, hashlib, subprocess, time, urllib.request

laravel = Path(__file__).resolve().parents[1]
core = Path(os.environ.get('BRIDGE_TEST_CORE_PROJECT', str(laravel.parent / 'payment-core'))).resolve()
executable = core / 'build/install/qr/bin/qr'
if not executable.is_file():
    raise SystemExit('Preparar primero el ejecutable existente de Core con ./gradlew --offline installDist; no se arranca el servicio operativo.')
work = Path(tempfile.mkdtemp(prefix='huata-bridge-test-'))
os.chmod(work, 0o700)
with socket.socket() as probe:
    probe.bind(('127.0.0.1', 0)); port = probe.getsockname()[1]
assert port not in (8000, 8087)
config = {
 'matchSecret': secrets.token_urlsafe(48),
 'clients': [{'id': 'wifi_suite', 'key': secrets.token_urlsafe(48), 'receivers': ['r1','r2']}],
 'receivers': [
  {'id':'r1','name':'Receptor ficticio WiFi','phone':'51999999999','qrSha256':hashlib.sha256(b'fixture-image').hexdigest(),'verified':True,'enabled':True},
  {'id':'r2','name':'No verificado ficticio','phone':'51988888888','qrSha256':'b'*64,'verified':False,'enabled':False}],
 'devices': [{'id':'fixture-device-1','key':secrets.token_urlsafe(48),'receiverId':'r1'}, {'id':'fixture-device-2','key':secrets.token_urlsafe(48),'receiverId':'r2'}],
 'allowLocalHttp': True,
}
private = work / 'commercial.json'
with os.fdopen(os.open(private, os.O_CREAT|os.O_EXCL|os.O_WRONLY, 0o600), 'w') as f: json.dump(config, f)
env = {**os.environ,  'PAYMENT_MODE':'commercial', 'PAYMENT_HOST':'127.0.0.1','PAYMENT_PORT':str(port), 'PAYMENT_CONFIG_FILE':str(private), 'PAYMENT_DB_PATH':str(work/'commercial.sqlite')}
url = f'http://127.0.0.1:{port}'
print('Core aislado de prueba:', url, '— datos sintéticos, sin usar puertos ni bases operativas.', flush=True)
with (work/'core.log').open('w') as log:
    process = subprocess.Popen([str(executable),'server'], cwd=work, env=env, stdout=log, stderr=subprocess.STDOUT)
    web_processes = []
    web_logs = []
    try:
        for _ in range(150):
            if process.poll() is not None: raise RuntimeError('Core de prueba no inició. Revisar el log aislado: '+str(work/'core.log'))
            try:
                with urllib.request.urlopen(url+'/health', timeout=.3) as response:
                    if json.load(response).get('mode') == 'commercial': break
            except (OSError, ValueError): pass
            time.sleep(.1)
        else: raise RuntimeError('No respondió el Core aislado')
        testenv = {**os.environ,'APP_ENV':'testing','DB_CONNECTION':'sqlite','DB_DATABASE':':memory:','DB_URL':'','CACHE_STORE':'array','SESSION_DRIVER':'array','QUEUE_CONNECTION':'sync','BRIDGE_TEST_CORE_URL':url,'BRIDGE_TEST_CORE_CONFIG':str(private)}
        tests = subprocess.run(['php','vendor/bin/pest','--compact','tests/Feature/YapeBridgeIntegrationTest.php'], cwd=laravel, env=testenv)
        if tests.returncode:
            sys.exit(tests.returncode)
        # Usa el transporte JVM realmente compilado del Bridge, no un cliente HTTP sustituto.
        bridge = Path(os.environ.get('BRIDGE_TEST_ANDROID_PROJECT', str(laravel.parent / 'yape-bridge'))).resolve()
        classes = bridge / 'app/build/tmp/kotlin-classes/debug'
        candidates = list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.21').glob('*/*.jar'))
        if not classes.is_dir() or not candidates:
            raise RuntimeError('Compilar primero Bridge debug; faltan sus clases o Kotlin stdlib local.')
        source = work/'BridgeWifiProbe.java'
        source.write_text('''import java.nio.file.*;
import pe.willian.yapebridge.bridge.*;
public class BridgeWifiProbe {
 public static void main(String[] args) throws Exception {
  var response = new BridgeHttpTransport(true).post(args[0], BridgeConfig.NOTIFICATION_PATH,
   Files.readString(Path.of(args[1])), Files.readString(Path.of(args[2])));
  if(response.getStatus()!=202) throw new IllegalStateException("No hubo ACK de Huata: HTTP "+response.getStatus());
  System.out.println(response.getBody());
 }
}
''')
        classpath = str(classes)+os.pathsep+str(candidates[0])
        java_home = Path(env['JAVA_HOME'])
        subprocess.run([str(java_home/'bin/javac'),'-cp',classpath,str(source)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        device_file=work/'device-token'
        with os.fdopen(os.open(device_file,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'w') as f:f.write(config['devices'][0]['key'])
        from datetime import datetime,timezone
        raw={'bridgeEventId':'bridge_http_'+secrets.token_hex(16),'receiverId':'r1','packageName':'com.bcp.innovacxion.yapeapp','postedAt':datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z'),'text':'Yape! Persona Ficticia te envió un pago por S/ 19.37','securityCode':'619'}
        body_file=work/'event.json';body_file.write_text(json.dumps(raw))
        web_env={**testenv,'PAYMENT_CORE_ENABLED':'true','PAYMENT_CORE_URL':url,'PAYMENT_CORE_KEY':config['clients'][0]['key'],'PAYMENT_CORE_ALLOW_LOCAL_HTTP':'true','APP_CONFIG_CACHE':str(work/'config-cache.php'),'APP_ROUTES_CACHE':str(work/'route-cache.php')}
        for host in ['127.0.0.1','localhost']:
            with socket.socket() as probe:
                probe.bind(('127.0.0.1',0));web_port=probe.getsockname()[1]
            assert web_port not in (8000,8087,port)
            web_log=(work/f'laravel-{web_port}.log').open('w');web_logs.append(web_log)
            web=subprocess.Popen(['php','-S',f'127.0.0.1:{web_port}','-t','public','public/index.php'],cwd=laravel,env=web_env,stdout=web_log,stderr=subprocess.STDOUT);web_processes.append(web)
            base=f'http://{host}:{web_port}'
            for _ in range(100):
                try:
                    with urllib.request.urlopen(base+'/up',timeout=.3) as response:
                        if response.status==200:break
                except (OSError,ValueError):pass
                if web.poll() is not None:raise RuntimeError('El Laravel aislado no inició')
                time.sleep(.05)
            else:raise RuntimeError('El Laravel aislado no respondió')
            probe=subprocess.run([str(java_home/'bin/java'),'-cp',str(work)+os.pathsep+classpath,'BridgeWifiProbe',base,str(device_file),str(body_file)],check=True,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            ack=json.loads(probe.stdout.strip())
            assert ack.get('accepted') is True and ack.get('eventId')==raw['bridgeEventId'] and ack.get('receiptOnly') is True
            assert ack.get('result') in ('OBSERVED','DUPLICATE')
            print('Transporte Bridge compilado → Laravel HTTP temporal → Core: ACK correcto en',base,'(mismo evento, sin pago automático).',flush=True)
        device_file.unlink(missing_ok=True)
    finally:
        for web in web_processes:
            web.terminate()
            try:web.wait(timeout=5)
            except subprocess.TimeoutExpired:web.kill();web.wait(timeout=5)
        for web_log in web_logs:web_log.close()
        (work/'device-token').unlink(missing_ok=True)
        process.terminate()
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
        # El archivo de configuración desechable no debe quedar con credenciales reutilizables.
        private.unlink(missing_ok=True)
        print('Core aislado detenido; credenciales de prueba retiradas. Servicios manuales intactos.', flush=True)
