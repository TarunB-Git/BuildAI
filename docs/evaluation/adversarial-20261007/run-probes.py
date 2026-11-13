#!/usr/bin/env python3
"""Execute small probes against the compiled production classes; no Android device needed."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
root = here.parents[2]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
def jar(group, artifact, version):
    return next((cache / group / artifact / version).glob('*/*.jar'))
entries = [root / 'app/build/tmp/kotlin-classes/debug',
           jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.0'),
           jar('org.json', 'json', '20240303')]
classpath = ':'.join(map(str, entries))
with tempfile.TemporaryDirectory(prefix='buildai-audit-probes-') as output:
    subprocess.run(['/usr/lib/jvm/java-17-openjdk-amd64/bin/javac', '-cp', classpath,
                    '-d', output, str(here / 'AuditProbes.java')], check=True)
    subprocess.run(['/usr/bin/java', '-Xmx128m', '-XX:ActiveProcessorCount=2',
                    '-cp', output + ':' + classpath, 'AuditProbes'], check=True)
