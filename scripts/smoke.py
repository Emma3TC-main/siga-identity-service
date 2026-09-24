"""Local demo HTTP verification. Never prints tokens, passwords or TOTP secrets."""
import base64, hashlib, hmac, json, struct, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT=Path(__file__).resolve().parent.parent
BASE='http://localhost:8081'
report=[]
def call(method,path,data=None,token=None,expected=200):
    print(f'HTTP {method} {path} -> {expected}', flush=True)
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try:
        with urlopen(req,timeout=15) as res: status=res.status;body=res.read();response_headers=res.headers
    except HTTPError as exc:
        status=exc.code;body=exc.read();response_headers=exc.headers
    assert status==expected, f'{method} {path}: expected {expected}, received {status}'
    assert response_headers.get('X-Correlation-ID'), 'Missing correlation header'
    if status>=400:
        assert 'application/problem+json' in response_headers.get('Content-Type','')
    report.append({'method':method,'path':path,'status':status,'result':'PASS'})
    return json.loads(body) if body else None

def otp(secret):
    counter=int(time.time())//30
    digest=hmac.new(base64.b32decode(secret),struct.pack('>Q',counter),hashlib.sha1).digest()
    offset=digest[-1]&15
    return f'{(int.from_bytes(digest[offset:offset+4],"big")&0x7fffffff)%1000000:06}'

def decode(segment):return base64.urlsafe_b64decode(segment+'='*(-len(segment)%4))
def verify_signature(token,jwk):
    header,payload,signature=token.split('.')
    n=int.from_bytes(decode(jwk['n']),'big');e=int.from_bytes(decode(jwk['e']),'big')
    size=(n.bit_length()+7)//8
    value=pow(int.from_bytes(decode(signature),'big'),e,n).to_bytes(size,'big')
    digest_info=bytes.fromhex('3031300d060960864801650304020105000420')+hashlib.sha256((header+'.'+payload).encode()).digest()
    expected=b'\x00\x01'+b'\xff'*(size-len(digest_info)-3)+b'\x00'+digest_info
    assert hmac.compare_digest(value,expected), 'Invalid RS256 signature'
    claims=json.loads(decode(payload));assert claims['iss']=='siga-identity' and 'siga-api' in claims['aud']
    assert claims['exp']-claims['iat']==1800

def main():
    config=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
    local=ROOT/'.local';local.mkdir(exist_ok=True)
    secret_file=local/'demo-mfa.json'
    call('GET','/actuator/health')
    challenge=call('POST','/api/v1/auth/login',{'username':'admin.demo','password':config['IAM_BOOTSTRAP_PASSWORD']})['challengeId']
    if secret_file.exists(): secret=json.loads(secret_file.read_text())['secret']
    else:
        enrolled=call('POST','/api/v1/auth/mfa/enroll',{'challengeId':challenge})
        secret=enrolled['secret'];secret_file.write_text(json.dumps({'secret':secret}),encoding='utf-8')
    admin=call('POST','/api/v1/auth/mfa/verify',{'challengeId':challenge,'otp':otp(secret)})
    access=admin['accessToken']
    jwks=call('GET','/.well-known/jwks.json');verify_signature(access,jwks['keys'][0])
    suffix=uuid.uuid4().hex[:8]
    user=call('POST','/api/v1/users',{'username':'smoke.'+suffix,'email':suffix+'@siga.test','password':'Local test passphrase 123!'},access,201)
    user_id=user['id']
    try:
        call('GET','/api/v1/users?page=0&size=20',token=access)
        call('GET','/api/v1/users/'+user_id,token=access)
        call('GET','/api/v1/permissions',token=access)
        call('GET','/api/v1/roles',token=access)
        role=call('POST','/api/v1/roles',{'code':'SMOKE_'+suffix.upper(),'name':'Smoke test','permissionIds':['10000000-0000-0000-0000-000000000001']},access,201)
        call('PUT','/api/v1/roles/'+role['id']+'/permissions',{'permissionIds':['10000000-0000-0000-0000-000000000001']},access)
        call('PUT','/api/v1/users/'+user_id+'/roles',{'roleIds':[role['id']]},access)
        call('PATCH','/api/v1/users/'+user_id,{'email':'changed.'+suffix+'@siga.test'},access)
        worker=call('POST','/api/v1/auth/login',{'username':user['username'],'password':'Local test passphrase 123!'})['tokens']
        call('GET','/api/v1/users',token=worker['accessToken'],expected=403)
        call('GET','/api/v1/users',expected=401)
        next_tokens=call('POST','/api/v1/auth/refresh',{'refreshToken':worker['refreshToken']})
        call('POST','/api/v1/auth/refresh',{'refreshToken':worker['refreshToken']},expected=401)
        call('POST','/api/v1/auth/refresh',{'refreshToken':next_tokens['refreshToken']},expected=401)
        call('POST','/api/v1/users',{'username':user['username'],'email':suffix+'@siga.test','password':'Local test passphrase 123!'},access,409)
        call('POST','/api/v1/users',{'username':'invalid','email':'invalid','password':'short'},access,400)
    finally:
        call('PATCH','/api/v1/users/'+user_id,{'active':False},access)
        call('POST','/api/v1/auth/logout',token=access,expected=204)
    (local/'http-results.json').write_text(json.dumps({'timestamp':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'jwtSignature':'RS256 verified','checks':report},indent=2),encoding='utf-8')
    print(f'PASS: {len(report)} HTTP checks; RS256 signature verified. Report: .local/http-results.json')

if __name__=='__main__':main()
