"""One manual, exact-inventory reversible Hub release quarantine. Never deletes."""
import os,json,urllib.request,pathlib,hashlib
REPO='prayagi-store-and-services/netra-hub'
assert os.environ['GITHUB_REPOSITORY']==REPO
assert os.environ['CONFIRM']=='QUARANTINE_ALL_77_HUB_APK_RELEASES'
root=pathlib.Path('quarantine-evidence');root.mkdir(exist_ok=True)
expected=json.load(open('scripts/hub-quarantine-inventory.json'))
assert os.environ.get('GITHUB_REF')=='refs/heads/main'
assert len(expected)==77 and len({r['id'] for r in expected})==77
headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'}
def request(path,body=None):
 data=None if body is None else json.dumps(body).encode()
 req=urllib.request.Request('https://api.github.com/repos/'+REPO+path,data=data,headers=headers,method='GET' if body is None else 'PATCH')
 with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
def inventory():
 out=[]
 for page in range(1,5):
  batch=request('/releases?per_page=100&page='+str(page));out+=batch
  if len(batch)<100:return out
 raise RuntimeError('Unexpected inventory size')
def shape(r):
 return {k:r.get(k) for k in ['id','tag_name','name','body','target_commitish','prerelease']}|{'assets':[{k:a.get(k) for k in ['id','name','size','digest']} for a in r['assets']]}
def snapshot(name,x):
 p=root/name;p.write_text(json.dumps(x,indent=2));print(name,hashlib.sha256(p.read_bytes()).hexdigest(),flush=True)
before=inventory();snapshot('before.json',before)
# Preserve resolved tag refs independently. Never mutate or recreate a tag.
tags={e['tag_name']:request('/git/ref/tags/'+e['tag_name']) for e in expected}
snapshot('tag-refs-before.json',tags)
actual={r['id']:r for r in before};allowed={r['id'] for r in expected}
apks={r['id'] for r in before if any(a['name'].lower().endswith('.apk') for a in r['assets'])}
assert apks==allowed,'APK release inventory changed; stop before mutation'
for r in expected:
 assert r['id'] in actual and shape(actual[r['id']])==r,'Source or asset inventory changed'
 assert not actual[r['id']].get('immutable',False),'Immutable release cannot be drafted'
progress=[]
try:
 for e in expected:
  current=request('/releases/'+str(e['id']))
  assert shape(current)==e,'Concurrent modification: stop'
  if not current['draft']:current=request('/releases/'+str(e['id']),{'draft':True})
  assert current['draft'] and shape(current)==e,'Draft or preservation check failed'
  progress.append({'id':e['id'],'tag_name':e['tag_name'],'draft':True})
  snapshot('progress.json',progress)
finally:
 after=inventory();snapshot('after.json',after)
 tags_after={e['tag_name']:request('/git/ref/tags/'+e['tag_name']) for e in expected}
 snapshot('tag-refs-after.json',tags_after)
 assert tags_after==tags,'Tag refs changed during quarantine'
 final={r['id']:r for r in after}
 covered=[e for e in expected if e['id'] in final and final[e['id']]['draft'] and shape(final[e['id']])==e]
 summary={'expected':77,'verified_draft_preserved':len(covered),'remaining':[e['tag_name'] for e in expected if e not in covered]}
 snapshot('summary.json',summary)
assert len(covered)==77,'Partial only; inspect evidence, do not claim all hidden'
