# Checks emsdeps.json against the proposed layered EMS module target; prints violations.
# Usage (same cwd, after ems-module-deps.py wrote <dir>/emsdeps.json): python3 ems-module-target.py <dir>
import json,sys,re,os,collections
S=sys.argv[1]; d=json.load(open(f'{S}/emsdeps.json')); cls=dict(d['classes'])
def re_(names,mod):
    for n in list(cls):
        if any(re.fullmatch(p,n) for p in names): cls[n]=mod
re_([r'Publish.*',r'Publishing.*',r'PublicEvent.*',r'PublicSession.*',r'CdnInvalidationService',r'SpeakersPhasePublishedEvent'],'publishing')
re_([r'SlotAssignment.*',r'ConflictDetection.*',r'PreferenceMatching.*',r'SchedulingConflict',r'Conflict(Severity|Type)',r'AssignmentStrategy',r'SessionTiming.*',r'SpeakerSlotPreference.*'],'planning')
re_([r'Ai.*',r'AI.*',r'OpenAi.*',r'.*Prompt.*'],'ai')
re_([r'StalenessScoreService'],'topics_ai')
re_([r'SpeakerAssignmentNotFoundException'],'sessions')
re_([r'AttendanceSummaryProjection'],'registrations')
re_([r'MailPipeline',r'MailVariables?',r'MailContent',r'RenderedMail',r'Rendering',r'DeliveredMail',r'SkippedRecipient',r'Recipients',r'EmailService',r'EmailTemplate.*',r'AlreadySentException',r'UnknownPlaceholderException',r'ModeratorReplyAddress',r'AudienceAdapter',r'Compose(Context|Recipient|Request)',r'MailDeliveryStatusService'],'mail_core')
re_([r'Notification',r'NotificationService',r'NotificationRequest',r'NotificationRepository',r'NotificationController'],'notif_core')
for n,m in list(cls.items()):
    if m=='mail': cls[n]='mail_center'
    if m=='notification': cls[n]='org_notify'
L={'platform':0,'mail_core':1,'notif_core':1,'event_core':2,'sessions':3,'registrations':3,'topics_ai':3,'tasks':3,'media':3,
   'speakers':4,'planning':5,'publishing':5,'mail_center':6,'analytics':6,'watch':6,'usermerge':6,'org_notify':6,'ai':6}
tok=re.compile(r'\b[A-Z][A-Za-z0-9]+\b')
paths={}
for dd,_,fs in os.walk('.'):
    for f in fs:
        if f.endswith('.java'): paths[f[:-5]]=os.path.join(dd,f)
viol=collections.defaultdict(set); ok=collections.Counter()
for n,p in paths.items():
    src=re.sub(r'//.*|/\*.*?\*/','',open(p).read(),flags=re.S); a=cls[n]
    for t in set(tok.findall(src)):
        if t in cls and t!=n and cls[t]!=a:
            b=cls[t]
            if L[b]<L[a]: ok[(a,b)]+=1
            else: viol[(a,b)].add(f'{n}->{t}')
sz=collections.Counter(cls.values())
print('SIZES',dict(sorted(sz.items(),key=lambda x:L[x[0]])))
print('ALLOWED EDGES',len(ok),' VIOLATING EDGES',len(viol),' violating refs',sum(len(v) for v in viol.values()))
for (a,b),v in sorted(viol.items(),key=lambda x:-len(x[1])):
    srcs=collections.Counter(e.split('->')[0] for e in v)
    print(f'{a:12}->{b:12} {len(v):3}  by {dict(srcs.most_common(4))}')
json.dump({'ok':{f'{a}|{b}':c for (a,b),c in ok.items()},'viol':{f'{a}|{b}':sorted(v) for (a,b),v in viol.items()},'sizes':sz},open(f'{S}/emstarget.json','w'),indent=1)
