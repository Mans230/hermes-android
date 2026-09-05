#!/usr/bin/env python3
"""Read-only compatibility report. Never reads .env or config, and prints no keys."""
from pathlib import Path
import json
import urllib.request
import urllib.error

root=Path('/usr/local/lib/hermes-agent')
source=root/'gateway'/'platforms'
files=sorted(source.glob('api_server*.py')) if source.is_dir() else []
if (source/'api_server').is_dir():files.extend((source/'api_server').glob('*.py'))
text='\n'.join(p.read_text(errors='replace') for p in files if p.is_file())
result={
    'reported_version':'0.21.0',
    'reported_upstream':'9dd6634c',
    'api_module_present':bool(files),
    'chat_completions_marker_found':'chat/completions' in text or 'chat_completions' in text,
    'sse_marker_found':'text/event-stream' in text or 'text/event-stream' in text.lower(),
}
# No proxy inheritance and no redirects: only probe the local Hermes port.
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect)
try:
    with opener.open('http://127.0.0.1:8642/health',timeout=4) as response:
        result['local_health_http']=response.status
        body=response.read(4096)
        try:
            health=json.loads(body)
            result['local_health_ok']=health.get('status')=='ok' if isinstance(health,dict) else False
        except Exception:result['local_health_ok']=False
except urllib.error.HTTPError as error:result['local_health_http']=error.code
except Exception:result['local_health_http']='unreachable'
print(json.dumps(result,indent=2))
print('Static markers are hints, not a live API compatibility test.')
