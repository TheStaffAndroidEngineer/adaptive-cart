#!/usr/bin/env python3
"""Run the explicitly selected node-action check with TalkBack enabled.

Requires TalkBack already installed. Restores the device's prior accessibility
settings even when the test fails. Speech and gesture traversal remain manual.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--output', type=Path, required=True)
args = p.parse_args()
project = Path(__file__).resolve().parents[1]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
component = 'com.google.android.marvin.talkback/.TalkBackService'

def adb(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *parts], text=True).strip()

def get(name):
    return adb('shell', 'settings', 'get', 'secure', name)

baseline = {name: get(name) for name in ('enabled_accessibility_services', 'accessibility_enabled')}
report = {'serial': args.serial, 'baseline': baseline,
    'scope': 'accessibility-tree actions with TalkBack enabled; no speech or gesture-order assertion'}
try:
    package = adb('shell', 'dumpsys', 'package', 'com.google.android.marvin.talkback')
    assert 'versionName=' in package, 'Install TalkBack before this check'
    (out / 'talkback-package.txt').write_text(package)
    prior = baseline['enabled_accessibility_services']
    services = [] if prior in ('null', '') else prior.split(':')
    if component not in services:
        services.append(component)
    adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', ':'.join(services))
    adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1')
    environment = dict(os.environ, ANDROID_SERIAL=args.serial)
    with (out / 'service-run.log').open('w') as log:
        result = subprocess.run([str(project / 'gradlew'), '-p', str(project),
            ':app:connectedDebugAndroidTest', '-PserviceCheck'], env=environment,
            stdout=log, stderr=subprocess.STDOUT)
    report['gradle_exit'] = result.returncode
    report['passed'] = result.returncode == 0
    shutil.copytree(project / 'app/build/outputs/androidTest-results/connected/debug',
        out / 'android-service', dirs_exist_ok=True)
    (out / 'node-actions.log').write_text(adb('logcat', '-d', '-s', 'C8ServiceEvidence:I', '*:S') + '\n')
    (out / 'accessibility-services.txt').write_text(adb('shell', 'dumpsys', 'accessibility'))
finally:
    for name, value in baseline.items():
        if value == 'null':
            adb('shell', 'settings', 'delete', 'secure', name)
        else:
            adb('shell', 'settings', 'put', 'secure', name, value)
    report['restored'] = {name: get(name) for name in baseline}
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
if not report['passed']:
    raise SystemExit('Service check failed; see preserved report')
