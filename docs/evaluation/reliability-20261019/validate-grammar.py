#!/usr/bin/env python3
"""Validate production planning GBNF with the pinned native parser, without a model."""
from pathlib import Path
import fcntl
import subprocess
import tempfile

here = Path(__file__).resolve().parent
root = here.parents[2]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
def jar(group, artifact, version):
    return next((cache / group / artifact / version).glob('*/*.jar'))
cp = ':'.join(map(str, [root / 'app/build/tmp/kotlin-classes/debug',
    jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.0'), jar('org.json', 'json', '20240303')]))
with open('/tmp/buildai-validation.lock', 'a') as lock, tempfile.TemporaryDirectory(prefix='buildai-grammar-') as output:
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    out = Path(output)
    subprocess.run(['javac', '-cp', cp, '-d', output, str(here / 'DumpPlanningGrammar.java')], check=True, timeout=30)
    subprocess.run(['java', '-Xmx128m', '-XX:ActiveProcessorCount=2', '-cp', output + ':' + cp,
        'DumpPlanningGrammar', output], check=True, timeout=30)
    source = root / 'third_party/llama.cpp'
    subprocess.run(['nice', '-n', '10', 'g++', '-std=c++17', '-O0', '-ffunction-sections', '-fdata-sections',
        '-I' + str(source / 'src'), '-I' + str(source / 'include'), '-I' + str(source / 'ggml/include'),
        str(here / 'ValidateGrammar.cpp'), str(source / 'src/llama-grammar.cpp'), '-Wl,--gc-sections',
        '-o', str(out / 'validate')], check=True, timeout=60)
    subprocess.run([str(out / 'validate'), str(out / 'planning-0.gbnf'), str(out / 'planning-1.gbnf')], check=True, timeout=30)
