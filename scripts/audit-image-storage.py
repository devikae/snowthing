"""Read-only S3/DB reference comparison. Does not delete or tag any object."""
import json
import pathlib
import re
import subprocess
import urllib.parse

remote = r'''
import os,subprocess,urllib.parse,json
v=dict(x.split('=',1) for x in subprocess.check_output(['docker','exec','snowthing-backend','printenv'],text=True).splitlines() if '=' in x)
u=urllib.parse.urlparse('mysql://'+v['SNOWTHING_PROD_DB_URL'].split('//',1)[1])
user=v['SNOWTHING_DB_USERNAME'];port=str(u.port or 3306)
env=os.environ.copy()
env['MYSQL_PWD']=subprocess.check_output(['aws','rds','generate-db-auth-token','--hostname',u.hostname,'--port',port,'--region','ap-northeast-2','--username',user],text=True).strip()
base=['mysql','--connect-timeout=8','--ssl-mode=REQUIRED','--enable-cleartext-plugin','-h',u.hostname,'-P',port,'-u',user,u.path.lstrip('/'),'-N','-B','-r','-e']
import re
refs=[]
for sql in ['SELECT image_url FROM post_image','SELECT content FROM post']:
    data=subprocess.check_output(base+[sql],env=env,text=True)
    refs.extend(re.findall(r'public/posts/[^\s<>"\x27)\]]+',data))
print(json.dumps(sorted(set(refs))))
'''
key=pathlib.Path.home()/'Downloads/key/snowthing-ec2-key.pem'
result=subprocess.run(['ssh','-i',str(key),'-o','BatchMode=yes','ubuntu@13.124.57.166','sudo python3 -'],input=remote,text=True,capture_output=True,check=True)
refs={urllib.parse.unquote(x).split('?')[0] for x in json.loads(result.stdout)}
objects=json.loads(subprocess.check_output(['aws','s3api','list-objects-v2','--bucket','snowthing-media-00001','--prefix','public/posts/','--query','Contents','--output','json'],encoding='utf-8')) or []
referenced_stems={pathlib.PurePosixPath(x).stem for x in refs}
used=[x for x in objects if pathlib.PurePosixPath(x['Key']).stem in referenced_stems]
candidates=[x for x in objects if pathlib.PurePosixPath(x['Key']).stem not in referenced_stems]
report={'readOnly':True,'includesSoftDeletedPostReferences':True,'objects':len(objects),'bytes':sum(x['Size'] for x in objects),
        'referencedObjects':len(used),'candidateObjects':len(candidates),'candidateBytes':sum(x['Size'] for x in candidates),
        'candidates':[{'key':x['Key'],'bytes':x['Size'],'modified':x['LastModified']} for x in candidates],
        'note':'Candidates require review and a fresh reference check before deletion; upload activity can race this snapshot.'}
output=pathlib.Path('docs/project/image-storage-audit-20261102.json')
output.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='candidates'},indent=2))
