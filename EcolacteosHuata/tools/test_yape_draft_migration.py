#!/usr/bin/env python3
"""Exercise only a new temporary SQLite database; never open an installed app database."""
import json
from pathlib import Path
import re
import sqlite3
import tempfile


source = Path(__file__).resolve().parents[1] / 'shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db'
directory = Path(tempfile.mkdtemp(prefix='huata-android-draft-'))
directory.chmod(0o700)
database = directory / 'upgrade.sqlite'
connection = sqlite3.connect(database)
for file in source.glob('*.sq'):
    if file.name == 'BorradorVentaYape.sq':
        continue
    content = re.sub(r'--[^\n]*', '', file.read_text())
    for statement in re.findall(r'CREATE TABLE\s+[\s\S]+?;', content, re.I):
        connection.execute(statement)
connection.execute("INSERT INTO descuento(id,nombre,porcentaje,activo) VALUES ('old-discount','Anterior',5,1)")
connection.execute('PRAGMA user_version=19')
connection.executescript((source / '19.sqm').read_text())
connection.execute('PRAGMA user_version=20')
for owner in ['seller1', 'seller2']:
    connection.execute('INSERT OR REPLACE INTO borrador_venta_yape VALUES (?,?)', (owner, json.dumps({'pending': True})))
connection.execute('INSERT OR REPLACE INTO borrador_venta_yape VALUES (?,?)', ('seller1', '{"pending":"updated"}'))
connection.commit()
connection.close()
connection = sqlite3.connect(database)
assert connection.execute("SELECT nombre FROM descuento WHERE id='old-discount'").fetchone()[0] == 'Anterior'
assert connection.execute('SELECT COUNT(*) FROM borrador_venta_yape').fetchone()[0] == 2
assert connection.execute("SELECT payload FROM borrador_venta_yape WHERE vendedor_id='seller1'").fetchone()[0] == '{"pending":"updated"}'
assert connection.execute('SELECT COUNT(*) FROM venta').fetchone()[0] == 0
assert connection.execute('PRAGMA user_version').fetchone()[0] == 20
connection.close()
result = {'result': 'passed', 'checks': 5, 'database': str(database), 'schema': '19 -> 20', 'real_phone': False}
(directory / 'report.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
