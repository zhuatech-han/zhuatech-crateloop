#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Real HTTP/MySQL acceptance with synthetic TEST data; private credentials never printed."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8127').rstrip('/');checks=0

def check(ok,message):
    """Verify invariant without emitting a credential or response body."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Isolated cookie session with real CSRF on writes."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def command(who,kind,id,action,extra=None,status=200,code=None,body=None):
    """Apply one versioned state command with an explicit evidence note."""
    value=body or {'requestKey':key(),'version':admin.request(f'/{kind}/{id}')['record']['version'],'note':'TEST independent physical evidence'}
    value.update(extra or {});return who.request(f'/{kind}/{id}/commands/{action}','POST',value,status,code)

def capture(state):
    """Persist only private acceptance snapshots for restart and restore comparison."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard']
    for kind in ['pools','partners','movements','adjustments','statements']:
        paths.append('/'+kind+'?size=100&sort=reference')
        if kind!='partners':paths.extend(f'/{kind}/{r["id"]}' for r in admin.request('/'+kind+'?size=100')['items'])
    return {p:admin.request(p) for p in paths}

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    state=json.loads(STATE.read_text());current=capture(state)
    if args.capture:
        state['responses']=current;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(current),'result':'PASS'}));raise SystemExit
    for p,expected in state['responses'].items():check(current[p]==expected,'Persistence mismatch: '+p)
    for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(current),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated test database.')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};userIds={}
roles={r['name']:r['id'] for r in admin.request('/admin/roles')}
dep=admin.request('/admin/departments','POST',{'name':'TEST 周转团队 '+suffix})['id'];outsideDep=admin.request('/admin/departments','POST',{'name':'TEST 外部团队 '+suffix})['id']
def master(name,department=dep,kind='CRATE'):
    return {'requestKey':key(),'reference':'TEST-'+key()[:8],'name':name,'departmentId':department,'kind':kind,'enabled':True}
partnerId=admin.request('/partners','POST',master('TEST 配送伙伴 '+suffix))['id'];otherPartner=admin.request('/partners','POST',master('TEST 另一伙伴 '+suffix))['id']
selfRole=admin.request('/admin/roles','POST',{'name':'TEST 本人范围 '+suffix,'scope':'SELF','permissions':['pool.read','catalog.write','movement.read','movement.write','dashboard','export','audit']})['id']
for name,role,department,binding in [('ops',roles['周转协调'],dep,None),('review',roles['独立复核'],dep,None),('warehouse',roles['仓库交接'],dep,None),('receiver',roles['仓库交接'],dep,None),('partner',roles['合作伙伴'],dep,partnerId),('other',roles['合作伙伴'],dep,otherPartner),('outside',roles['周转协调'],outsideDep,None),('bound',roles['管理员'],dep,otherPartner),('self',selfRole,dep,None)]:
    username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+{'ops':'周转协调','review':'独立复核','warehouse':'发运人员','receiver':'收货人员','partner':'合作伙伴','other':'另一伙伴','outside':'外部部门','bound':'绑定管理员','self':'本人范围'}[name],'password':password,'roleId':role,'departmentId':department,'partnerId':binding,'enabled':True})['id'];clients[name]=Client(username,password)
ops=clients['ops'];review=clients['review'];warehouse=clients['warehouse'];receiver=clients['receiver'];owner=clients['partner'];poolId=ops.request('/pools','POST',master('TEST 上海周转箱 '+suffix))['id'];moves=[];adjustments=[];statements=[]
def pool():return admin.request(f'/pools/{poolId}')['pool']
def conserved():
    p=pool();b=['available','reserved','outTransit','held','returnReserved','returnTransit','inspection','repair','lost','retired'];check(p['total']==sum(p[k] for k in b) and all(p[k]>=0 for k in b),'Quantity conservation');detail=admin.request(f'/pools/{poolId}');check(sum(x['held'] for x in detail['balances'])==p['held'],'Partner custody subledger');check(sum(x['returnReserved'] for x in detail['balances'])==p['returnReserved'],'Partner return reservation subledger')
def proposal(kind,n,good=0,retired=0):
    a=ops.request('/adjustments','POST',{'requestKey':key(),'reference':'TEST-'+kind+'-'+key()[:8],'kind':kind,'poolId':poolId,'quantity':n,'goodQuantity':good,'retireQuantity':retired})['record'];adjustments.append(a['id']);command(ops,'adjustments',a['id'],'submit');command(review,'adjustments',a['id'],'approve');conserved();return a['id']
def movement(kind,n,who=ops,party=partnerId):
    value={'requestKey':key(),'reference':'TEST-'+kind+'-'+key()[:8],'kind':kind,'poolId':poolId,'partnerId':party,'quantity':n};m=who.request('/movements','POST',value)['record'];moves.append(m['id']);return m['id']
def issued(n):
    i=movement('ISSUE',n);command(ops,'movements',i,'submit');command(review,'movements',i,'approve');command(warehouse,'movements',i,'dispatch');command(owner,'movements',i,'receive',{'quantity':n});conserved();return i
proposal('ADD',100)
a=issued(30);r=movement('RETURN',20,owner);command(owner,'movements',r,'submit');command(warehouse,'movements',r,'dispatch');command(receiver,'movements',r,'receive',{'quantity':20});command(review,'movements',r,'inspect',{'goodQuantity':12,'repairQuantity':5,'retireQuantity':3});conserved();proposal('REPAIR',5,4,1);check(pool()['available']==86 and pool()['held']==10 and pool()['retired']==4,'Issue return and repair result')
# Outbound shortage keeps the missing quantity in transit until independent evidence resolution.
b=movement('ISSUE',10);command(ops,'movements',b,'submit');command(review,'movements',b,'approve');command(warehouse,'movements',b,'dispatch');command(owner,'movements',b,'receive',{'quantity':7});conserved();check(pool()['outTransit']==3,'Pending shortage transit');command(review,'movements',b,'resolve',{'recoveredQuantity':2,'lostQuantity':1});conserved()
# Return shortage and quality classification are separate evidence steps.
c=movement('RETURN',10,owner);command(owner,'movements',c,'submit');command(warehouse,'movements',c,'dispatch');command(receiver,'movements',c,'receive',{'quantity':7});command(review,'movements',c,'inspect',{'goodQuantity':7,'repairQuantity':0,'retireQuantity':0},409,'INVALID_STATE');command(review,'movements',c,'resolve',{'backToPartner':2,'lostQuantity':1});command(review,'movements',c,'inspect',{'goodQuantity':5,'repairQuantity':2,'retireQuantity':0});conserved()
# Draft editing and cancellation do not erase evidence.
d=movement('ISSUE',4);old=admin.request(f'/movements/{d}')['record'];ops.request(f'/movements/{d}','PUT',{'requestKey':key(),'version':old['version'],'reference':old['reference'],'kind':'ISSUE','poolId':poolId,'partnerId':partnerId,'quantity':5});command(ops,'movements',d,'submit');command(review,'movements',d,'approve');command(ops,'movements',d,'cancel');conserved()
# Two concurrent approvals compete for the same physical units.
e=movement('ISSUE',60);f=movement('ISSUE',60);command(ops,'movements',e,'submit');command(ops,'movements',f,'submit')
def competing(id):return command(Client(users['review'],password),'movements',id,'approve',status=(200,409))
with concurrent.futures.ThreadPoolExecutor(2) as ex:
    one=ex.submit(competing,e);two=ex.submit(competing,f);results=[one.result(),two.result()]
check(sum('record' in x for x in results)==1,'Concurrent reservations both committed')
for id in [e,f]:command(ops,'movements',id,'cancel')
conserved()
# Exact UUID retry does not repeat reservation; changed actor or payload is rejected.
g=movement('ISSUE',3);command(ops,'movements',g,'submit');body={'requestKey':key(),'version':admin.request(f'/movements/{g}')['record']['version'],'note':'TEST exact retry'};command(review,'movements',g,'approve',body=body.copy());before=pool();command(review,'movements',g,'approve',body=body.copy());check(pool()==before,'Retry changed balance');command(review,'movements',g,'approve',body={**body,'note':'TEST changed'},status=409,code='REQUEST_KEY_REUSED');command(admin,'movements',g,'approve',body=body.copy(),status=409,code='REQUEST_KEY_REUSED');command(ops,'movements',g,'cancel')
# Reject over-return and excessive/fractional quantities.
h=movement('RETURN',pool()['held']+1,owner);command(owner,'movements',h,'submit',status=409,code='INSUFFICIENT_QUANTITY');command(owner,'movements',h,'cancel')
for quantity,code in [(1.5,'INVALID_INPUT'),(-1,'INVALID_QUANTITY'),(1000001,'INVALID_QUANTITY')]:ops.request('/movements','POST',{'requestKey':key(),'reference':'TEST invalid '+key(),'kind':'ISSUE','poolId':poolId,'partnerId':partnerId,'quantity':quantity},400,code)
# Partner binding overrides an administrator's ALL scope and inventory totals stay private.
for name in ['other','outside','self','bound']:
    clients[name].request(f'/movements/{a}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/movements/{a}/report.json',status=403,code='OUT_OF_SCOPE')
for name in ['partner','bound']:
    clients[name].request(f'/pools/{poolId}',status=403,code='STAFF_ONLY');clients[name].request('/admin/users',status=403,code='STAFF_ONLY' if name=='bound' else 'FORBIDDEN');check('total' not in clients[name].request('/dashboard'),'Global partner statistics leak');check(all('available' not in p and 'total' not in p for p in clients[name].request('/options')['pools']),'Pool inventory in options')
check(clients['other'].request('/movements')['total']==0,'Another party list leak');check(clients['self'].request('/pools')['total']==0,'SELF master list leak');check(clients['outside'].request('/dashboard')['movements']==0,'Department statistics leak');clients['bound'].request('/audit',status=403,code='STAFF_ONLY');check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash leak');check('zhuatech2' not in json.dumps(owner.request(f'/movements/{a}/report.json')),'Advertising in export')
# Current statements reject incomplete handoffs and a ledger that changed after freezing.
def freeze():
    s=ops.request('/statements','POST',{'requestKey':key(),'reference':'TEST-ST-'+key()[:8],'poolId':poolId,'partnerId':partnerId})['record'];statements.append(s['id']);return s['id']
s=freeze();issued(1);command(owner,'statements',s,'confirm',status=409,code='STALE_STATEMENT');command(owner,'statements',s,'dispute');command(ops,'statements',s,'cancel');s2=freeze();command(owner,'statements',s2,'confirm');check(admin.request(f'/statements/{s2}')['record']['held']==pool()['held'],'Frozen balance mismatch')
i=movement('ISSUE',2);command(ops,'movements',i,'submit');command(review,'movements',i,'approve');ops.request('/statements','POST',{'requestKey':key(),'reference':'TEST blocked '+key(),'poolId':poolId,'partnerId':partnerId},409,'PENDING_HANDOFF');command(ops,'movements',i,'cancel')
# An unreferenced master can be deleted; referenced pool history cannot be deleted.
temp=ops.request('/partners','POST',master('TEST disposable'))['id'];ops.request(f'/partners/{temp}','DELETE',{});ops.request(f'/pools/{poolId}','DELETE',{},409,'CONFLICT')
ops.request('/movements?size=101',status=400,code='INVALID_PAGE');ops.request('/movements?sort=sql',status=400,code='INVALID_PAGE');check(owner.request('/movements?kind=RETURN&size=1')['total']>=3,'Direction filter');check(owner.request('/movements?status=CLOSED')['total']>=4,'State filter');ops.request('/movements','POST',{},403,csrf=False)
# Session authorization is rechecked after permission changes and account disablement.
user=next(x for x in admin.request('/admin/users') if x['id']==userIds['outside']);admin.request('/admin/users/'+str(user['id']),'PUT',{**user,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(user['id']),'PUT',{**user,'enabled':True})
# Separate secondary pool for packaging type choices; no cross-pool balancing.
second=ops.request('/pools','POST',master('TEST 上海托盘 '+suffix,kind='PALLET'))['id'];conserved()
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'poolId':poolId,'secondPool':second,'partnerId':partnerId,'departmentId':dep,'movementIds':moves,'adjustmentIds':adjustments,'statementIds':statements};state['responses']=capture(state)
fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'movements':len(moves),'adjustments':len(adjustments),'statements':len(statements),'result':'PASS'}))
