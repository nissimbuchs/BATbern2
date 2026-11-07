# EMS module dependency scan (architecture review 2026-11). Assigns each EMS class to a module and counts cross-module references.
# Usage (from services/event-management-service/src/main/java/ch/batbern/events): python3 ems-module-deps.py . /tmp/x/emsdeps.json
import os,re,sys,collections,json
root=sys.argv[1]
RULES=[
 ('watch',     lambda p,n: p.startswith('watch') or 'LiveTiming' in n or 'Watch' in n),
 ('usermerge', lambda p,n: p.startswith('usermerge') or 'UserMerge' in n or 'MergeRequest' in n),
 ('notification',lambda p,n: p.startswith('notification') or n.startswith('Notification') or 'InAppNotification' in n),
 ('tasks',     lambda p,n: 'Task' in n),
 ('analytics', lambda p,n: 'Analytics' in n or 'Attendance' in n),
 ('speakers',  lambda p,n: any(k in n for k in ['Speaker','Outreach','Invitation','ContentSubmission','SelfNomination','SessionProposal','QualityReview'])),
 ('registrations',lambda p,n: any(k in n for k in ['Registration','Registrant','Deregistration','Participant','Waitlist','Badge','Attendee','Enroll','Stakeholder','DistributionList'])),
 ('sessions',  lambda p,n: any(k in n for k in ['Session','Slot','Timetable','Agenda','Qna','QnA','Timing','Material'])),
 ('topics_ai', lambda p,n: any(k in n for k in ['Topic','Ai','AI','Prompt','Similarity','Cluster','OpenAi'])),
 ('media',     lambda p,n: any(k in n for k in ['Photo','Teaser','Image'])),
 ('mail',      lambda p,n: p.startswith('mailcenter') or p.startswith('forwarding') or any(k in n for k in ['Newsletter','Email','Mail','SlidesOnline','Notice','Venue','Inbound','Bounce','Unsubscribe','Subscriber','Ses','Conversation'])),
 ('event_core',lambda p,n: any(k in n for k in ['Event','Workflow','Publish','Thanks','Setting','Admin','Presentation','Feature'])),
]
OVR={'CalendarDeclineService':'mail','CalendarInviteUid':'mail','ConflictDetectionService':'sessions','ConflictSeverity':'sessions','ConflictType':'sessions','SchedulingConflict':'sessions','AssignmentStrategy':'sessions','PreferenceMatchingAlgorithm':'sessions','ContentStatusDeriver':'speakers','InvalidPromotionStateException':'speakers','ReadyRequiresPromoteException':'speakers','AlreadyRespondedException':'speakers','TransitionPayload':'speakers','TransitionResult':'speakers','StalenessScoreService':'speakers','ReminderProperties':'speakers','ConfirmationTokenService':'registrations','OrganizerNotificationService':'notification','DefaultOrganizerNotificationService':'notification','CdnInvalidationService':'event_core','ArchivalBestEffortSteps':'event_core','GenericLogoService':'media','Logo':'media','LogoRepository':'media','LogoStatus':'media','IncompleteProfileException':'speakers'}
cls={}
files={}
for d,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.java'): continue
        rel=os.path.relpath(os.path.join(d,f),root); pkg=rel.split('/')[0] if '/' in rel else ''
        n=f[:-5]; mod='platform'
        for m,fn in RULES:
            if fn(pkg,n): mod=m; break
        mod=OVR.get(n,mod); cls[n]=mod; files[n]=os.path.join(d,f)
tok=re.compile(r'\b[A-Z][A-Za-z0-9]+\b')
edges=collections.Counter(); ex=collections.defaultdict(set)
for n,path in files.items():
    src=open(path).read()
    src=re.sub(r'//.*|/\*.*?\*/','',src,flags=re.S)
    m=cls[n]
    for t in set(tok.findall(src)):
        if t in cls and t!=n and cls[t]!=m:
            edges[(m,cls[t])]+=1; ex[(m,cls[t])].add(f"{n}->{t}")
cnt=collections.Counter(cls.values())
print("MODULE SIZES",dict(cnt))
for (a,b),c in sorted(edges.items(),key=lambda x:(x[0][0],-x[1])):
    print(f"{a:13} -> {b:13} {c:4}  e.g. {sorted(ex[(a,b)])[:3]}")
json.dump({'sizes':cnt,'edges':{f'{a}|{b}':c for (a,b),c in edges.items()},'examples':{f'{a}|{b}':sorted(v) for (a,b),v in ex.items()},'classes':cls},open(sys.argv[2],'w'),indent=1)
