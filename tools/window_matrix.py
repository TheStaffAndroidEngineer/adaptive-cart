#!/usr/bin/env python3
"""Host-driven emulator display/settings checks; never select a device implicitly.

Requires the debug APK installed, no other UI automation and TalkBack disabled.
These are emulated display configurations, not physical-device or multi-window evidence.
"""
import argparse
import json
from pathlib import Path
from fresh_android_xml import read_ui_xml
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (out / helper_name).write_bytes(helper.read_bytes())
package = 'staff.android.adaptive'
report = {'serial': args.serial, 'scope': 'actual emulator display/font/app-locale/IME settings', 'observations': []}

def adb(*parts, binary=False):
    return subprocess.check_output([args.adb, '-s', args.serial, *map(str, parts)], text=not binary)

def shell(*parts):
    return adb('shell', *parts).strip()

def setting(namespace, name):
    return shell('settings', 'get', namespace, name)

def restore_setting(namespace, name, value):
    if value == 'null':
        shell('settings', 'delete', namespace, name)
    else:
        shell('settings', 'put', namespace, name, value)

def tree():
    return ET.fromstring(read_ui_xml(adb, '/sdcard/c8-window.xml', {package}))

def node(tag, root=None):
    root = tree() if root is None else root
    return next((n for n in root.iter('node') if n.get('resource-id') == tag), None)

def bounds(n):
    return [int(v) for v in re.findall(r'\d+', n.get('bounds'))]

def tap(tag):
    n = node(tag)
    for attempt in range(10):
        if n is not None:
            break
        r = tree()
        region = next((v for v in r.iter('node') if v.get('scrollable') == 'true'), None)
        if region is None:
            break
        x1, y1, x2, y2 = bounds(region)
        top, bottom = y1+(y2-y1)//5, y1+(y2-y1)*4//5
        forward = attempt % 6 < 3
        shell('input', 'swipe', (x1+x2)//2, bottom if forward else top,
            (x1+x2)//2, top if forward else bottom, 180)
        n = node(tag)
    assert n is not None, f'No visible node: {tag}'
    x1, y1, x2, y2 = bounds(n)
    shell('input', 'tap', (x1+x2)//2, (y1+y2)//2)
    time.sleep(.5)

def record(label, checks):
    root = tree()
    for description, passed in checks(root):
        assert passed, f'{label}: {description}'
    (out / f'{label}.xml').write_bytes(ET.tostring(root))
    (out / f'{label}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    tags = {n.get('resource-id'): {'bounds': bounds(n), 'text': n.get('text'),
        'focused': n.get('focused'), 'enabled': n.get('enabled')}
        for n in root.iter('node') if n.get('resource-id') in
        ('one-pane', 'two-panes', 'draft', 'accepted', 'confirm', 'item-camera', 'editor-scroll', 'help-text')}
    report['observations'].append({'label': label, 'nodes': tags,
        'display': shell('wm', 'size'), 'density': shell('wm', 'density'),
        'font_scale': setting('system', 'font_scale'),
        'locales': shell('cmd', 'locale', 'get-app-locales', package, '--user', '0')})

def draft_checks(root, pane):
    d, accepted = node('draft', root), node('accepted', root)
    return [('pane policy', node(pane, root) is not None),
        ('raw draft still 3', d is not None and '3' in d.get('text', '')),
        ('accepted still 2', accepted is not None and '2' in accepted.get('text', ''))]

baseline = {'size': shell('wm', 'size'), 'density': shell('wm', 'density'),
    'font_scale': setting('system', 'font_scale'),
    'animator_duration_scale': setting('global', 'animator_duration_scale'),
    'locales': shell('cmd', 'locale', 'get-app-locales', package, '--user', '0')}
report['baseline'] = baseline
report['device'] = {key: shell('getprop', key) for key in
    ('ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.fingerprint')}
report['input_method'] = setting('secure', 'default_input_method')
try:
    assert setting('secure', 'enabled_accessibility_services') in ('null', ''), 'Run service checks separately'
    shell('am', 'force-stop', package)
    shell('cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', 'en')
    shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('wm', 'size', '800x1200')
    shell('wm', 'density', '320')
    shell('am', 'start', '-W', '-n', package + '/.MainActivity')
    time.sleep(2)
    tap('lab-controls')
    tap('reset')
    tap('item-camera')
    tap('edit')
    tap('draft')
    shell('input', 'keyevent', 'KEYCODE_MOVE_END')
    shell('input', 'keyevent', 'KEYCODE_DEL')
    shell('input', 'text', '3')
    # Hide the IME for a full-pane comparison; this is not acceptance.
    shell('input', 'keyevent', 'KEYCODE_BACK')
    record('01-compact-draft', lambda r: draft_checks(r, 'one-pane'))
    shell('wm', 'size', '2200x1400')
    time.sleep(2)
    record('02-wide-same-draft', lambda r: draft_checks(r, 'two-panes'))
    shell('wm', 'size', '800x1200')
    time.sleep(2)
    record('03-narrow-same-draft', lambda r: draft_checks(r, 'one-pane'))
    shell('settings', 'put', 'system', 'font_scale', '2.0')
    shell('cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', 'de')
    time.sleep(2)
    tap('draft')
    time.sleep(1)
    record('04-german-large-ime', lambda r: [('draft exists', node('draft', r) is not None)])
    # Scroll the actual form while the actual IME remains visible.
    for _ in range(10):
        root = tree()
        c = node('confirm', root)
        region = node('editor-scroll', root)
        assert region is not None
        x1, y1, x2, y2 = bounds(region)
        # Accessibility target bounds can extend outside a clipped viewport.
        # Require the whole target to lie inside the actually visible form.
        if c is not None and bounds(c)[3] - bounds(c)[1] >= 96 and bounds(c)[1] >= y1 and bounds(c)[3] <= y2:
            break
        shell('input', 'swipe', (x1+x2)//2, y2-25, (x1+x2)//2, y1+25, '400')
        time.sleep(.3)
    record('05-german-confirm-reachable', lambda r: [
        ('Confirm enabled and visible', node('confirm', r) is not None and node('confirm', r).get('enabled') == 'true'),
        ('Confirm target at least 48dp', bounds(node('confirm', r))[3]-bounds(node('confirm', r))[1] >= 96),
        ('Confirm lies inside visible form', bounds(node('confirm', r))[1] >= bounds(node('editor-scroll', r))[1]
            and bounds(node('confirm', r))[3] <= bounds(node('editor-scroll', r))[3])])
    tap('confirm')
    # Saved scroll position may leave Accepted above the current viewport.
    region = node('editor-scroll')
    x1, y1, x2, y2 = bounds(region)
    shell('input', 'swipe', (x1+x2)//2, y1+20, (x1+x2)//2, y2-20, '400')
    time.sleep(.5)
    record('06-explicit-confirm-accepted', lambda r: [
        ('accepted 3', node('accepted', r) is not None and '3' in node('accepted', r).get('text', '')),
        ('editor closed', node('draft', r) is None)])
    shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('wm', 'size', '2200x1400')
    shell('cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', 'ar-u-nu-arab')
    time.sleep(2)
    record('07-arabic-two-panes', lambda r: [
        ('RTL list is right of detail', bounds(node('item-camera', r))[0] > bounds(node('editor-scroll', r))[0]),
        ('accepted Arabic three', '٣' in node('accepted', r).get('text', ''))])
    shell('settings', 'put', 'global', 'animator_duration_scale', '0')
    tap('help')
    record('08-disabled-motion-help', lambda r: [('help reached', node('help-text', r) is not None)])
    report['passed'] = True
except Exception as error:
    report['passed'] = False
    report['error'] = str(error)
    raise
finally:
    for command in ('size', 'density'):
        match = re.search(r'Override .*: (.+)', baseline[command])
        shell('wm', command, match.group(1) if match else 'reset')
    restore_setting('system', 'font_scale', baseline['font_scale'])
    restore_setting('global', 'animator_duration_scale', baseline['animator_duration_scale'])
    locale = re.search(r'\[(.*?)\]', baseline['locales'])
    shell('cmd', 'locale', 'set-app-locales', package, '--user', '0',
        *(['--locales', locale.group(1)] if locale and locale.group(1) else []))
    report['restored_settings'] = {'size': shell('wm', 'size'), 'density': shell('wm', 'density'),
        'font_scale': setting('system', 'font_scale'), 'animator_duration_scale': setting('global', 'animator_duration_scale')}
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
