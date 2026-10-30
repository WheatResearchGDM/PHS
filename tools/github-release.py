"""Inspect CI or publish a verified local APK, using the existing Git credential helper."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.request
import urllib.parse
import re

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        redirected = super().redirect_request(request, response, code, message, headers, newurl)
        if redirected and urllib.parse.urlparse(request.full_url).hostname != urllib.parse.urlparse(newurl).hostname:
            redirected.remove_header('Authorization')
        return redirected

OPENER = urllib.request.build_opener(SafeRedirect())

ROOT = Path(__file__).resolve().parent.parent
REPO = 'WheatResearchGDM/PHS'
env = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')
credential = subprocess.run(['git', '-c', 'credential.interactive=never', 'credential', 'fill'],
    input='protocol=https\nhost=github.com\n\n', text=True, capture_output=True, cwd=ROOT, env=env)
fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
token = fields.get('password')
if not token:
    raise SystemExit('GitHub authentication unavailable in the existing credential helper.')

def api(path, method='GET', payload=None, data=None, content_type=None):
    url = path if path.startswith('https://') else 'https://api.github.com/repos/' + REPO + path
    headers = {'Accept': 'application/vnd.github+json', 'Authorization': 'Bearer ' + token,
        'User-Agent': 'PHS-Reader-build', 'X-GitHub-Api-Version': '2022-11-28'}
    if payload is not None:
        data = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with OPENER.open(request, timeout=180) as response:
        return json.load(response)

def diagnose():
    runs = api('/actions/runs?per_page=5')['workflow_runs']
    for run in runs[:3]:
        print(json.dumps({'run': run['id'], 'status': run['status'], 'conclusion': run['conclusion'],
            'url': run['html_url'], 'sha': run['head_sha']}, ensure_ascii=False))
        if run['conclusion'] != 'failure':
            continue
        for job in api('/actions/runs/' + str(run['id']) + '/jobs')['jobs']:
            print(json.dumps({'job': job['name'], 'conclusion': job['conclusion'],
                'steps': [{'name': s['name'], 'conclusion': s['conclusion']} for s in job['steps']]}, ensure_ascii=False))
            if job['check_run_url']:
                for annotation in api(job['check_run_url'] + '/annotations'):
                    print(json.dumps({'level': annotation['annotation_level'], 'message': annotation['message']}, ensure_ascii=False))
            if job['conclusion'] == 'failure':
                request = urllib.request.Request('https://api.github.com/repos/' + REPO + '/actions/jobs/' + str(job['id']) + '/logs',
                    headers={'Authorization': 'Bearer ' + token, 'User-Agent': 'PHS-Reader-build'})
                with OPENER.open(request, timeout=60) as response:
                    lines = response.read().decode('utf-8', errors='replace').splitlines()
                    relevant = [i for i, line in enumerate(lines) if '##[error]' in line or 'Error:' in line]
                    for index in relevant:
                        print('\n'.join(lines[max(0, index-5):index+5]))

def publish():
    version = sys.argv[2] if len(sys.argv) > 2 else re.search(r"versionName\s+'([^']+)'", (ROOT / 'app/build.gradle').read_text(encoding='utf-8')).group(1)
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise SystemExit('Invalid version.')
    apk = ROOT / ('dist/PHS-Reader-' + version + '.apk')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    tag = 'v' + version
    try:
        release = api('/releases/tags/' + tag)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        notes_path = ROOT / 'docs/releases' / (version + '.md')
        notes = notes_path.read_text(encoding='utf-8') if notes_path.exists() else 'Versão de testes para Android 8.0 ou superior.'
        release = api('/releases', 'POST', payload={
            'tag_name': tag, 'target_commitish': sha, 'name': 'PHS Reader ' + version + ' — versão de testes',
            'prerelease': True, 'draft': False,
            'body': notes + '\n\nBaixe `' + apk.name + '` na seção Assets. APK assinado com a chave local de testes.\n\nSHA-256: `' + digest + '`'
        })
    existing = {a['name']: a for a in api('/releases/' + str(release['id']) + '/assets')}
    if apk.name in existing:
        asset = existing[apk.name]
        if asset.get('digest') != 'sha256:' + digest:
            raise SystemExit('An asset with this name already exists. Its digest must be checked before replacement.')
    else:
        upload = release['upload_url'].split('{')[0] + '?name=' + apk.name
        asset = api(upload, 'POST', data=apk.read_bytes(), content_type='application/vnd.android.package-archive')
    print(json.dumps({'release': release['html_url'], 'download': asset['browser_download_url'],
        'size': asset['size'], 'sha256': digest}, ensure_ascii=False))

if __name__ == '__main__':
    try:
        {'diagnose': diagnose, 'publish': publish}[sys.argv[1]]()
    except urllib.error.HTTPError as error:
        try:
            message = json.load(error).get('message', 'API request failed')
        except Exception:
            message = 'API request failed'
        raise SystemExit('GitHub API HTTP ' + str(error.code) + ': ' + message)
