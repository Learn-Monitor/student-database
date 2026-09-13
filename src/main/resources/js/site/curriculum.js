/* Standard curriculum editor: all mutations are validated again by the server. */
'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    const root = document.querySelector('#curriculum');
    if (!root) return;
    let revealSemesterSetup=()=>{};
    const el = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
    const message = el('p'); message.setAttribute('role', 'status'); root.append(message);
    const permissionDenied = 'Für diese Aktion fehlt die Berechtigung.';
    const hasPM = typeof hasPermission === 'function';
    let permissionsReady = false;
    async function allowed(name) {
        if (!hasPM) return true;
        if (!permissionsReady || typeof hasPermission !== 'function') return false;
        try { return await hasPermission(name) === true; } catch { return false; }
    }
    async function post(path, data = {}) {
        if (!await allowed('curriculum_view')) throw Error(permissionDenied);
        let response;
        try {
            response = await fetch(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)});
        } catch { throw Error('Die Anfrage konnte nicht gesendet werden. Bitte erneut versuchen.'); }
        if (response.status === 401) throw Error('Bitte erneut anmelden.');
        if (response.status === 403) throw Error(permissionDenied);
        let body;
        try { body = JSON.parse(await response.text()); } catch { /* Non-JSON errors never reach the UI. */ }
        if (!response.ok) {
            const messages = {
                context_unassigned: 'Für dieses Fach und Halbjahr ist noch kein Unterrichtskontext zugewiesen.',
                context_conflict: 'Die Zuordnung oder Leistungsübernahme passt nicht zum aktuellen Stand. Bitte Vorschau aktualisieren und alle Abschlüsse gleichwertig zuordnen.',
                invalid_input: 'Bitte die Eingaben prüfen.',
                not_found: 'Der angefragte Eintrag wurde nicht gefunden.',
                conflict: 'Die Änderung steht im Konflikt mit vorhandenen Daten. Bitte Kontext, Namen und Halbjahr prüfen und aktualisieren.',
                budget_exceeded: 'Die Grenze von 105 Münzen würde überschritten.'
            };
            const contexts = response.status === 409 && Array.isArray(body?.affectedContexts)
                ? body.affectedContexts.filter(b => b && ['teacherId','classId','semesterId','centralTokens','flexibleTokens','totalTokens'].every(k => Number.isSafeInteger(b[k])))
                    .map(b => `Lehrkraft ${b.teacherId}, Klasse ${b.classId}, Halbjahr ${b.semesterId}: ${b.centralTokens} + ${b.flexibleTokens} = ${b.totalTokens}`).join('; ') : '';
            const conflicts = {
                'The name already exists in this scope or a referenced object changed.': 'Der Name ist in diesem Kontext bereits vorhanden oder ein zugehöriger Eintrag wurde geändert.',
                'Curriculum changed concurrently; please retry.': 'Das Curriculum wurde gleichzeitig geändert. Bitte aktualisieren und erneut versuchen.',
                'Class grade changed; existing semester context is historical.': 'Der Jahrgang der Klasse wurde geändert. Bitte für neue Etappen ein neues Halbjahr auswählen.',
                "Assign a semester before increasing an archived topic's budget.": 'Vor einer Erhöhung des Budgets muss dem archivierten Thema ein Halbjahr zugeordnet werden.'
            };
            const conflict = response.status === 409 && body?.error === 'conflict' && Object.hasOwn(conflicts, body?.message) ? conflicts[body.message] : null;
            const detail = conflict || (response.status < 500 && Object.hasOwn(messages, body?.error) ? messages[body.error] : 'Anfrage fehlgeschlagen. Bitte erneut versuchen.');
            throw Error(detail + (contexts ? ' ' + contexts : ''));
        }
        if (body == null) throw Error('Die Antwort konnte nicht verarbeitet werden. Bitte erneut versuchen.');
        return body;
    }
    function showError(error) { message.textContent = error.message; message.style.color = 'darkred'; }
    function select(label, items, name) {
        const wrapper = el('label', label + ' '), node = el('select'); node.name = name;
        for (const item of items) { const option = el('option', item.label || item.name); option.value = item.id; node.append(option); }
        wrapper.append(node); root.append(wrapper); return node;
    }
    function field(form, label, value, type = 'text') {
        const wrapper = el('label', label + ' '), input = el('input'); input.type = type; input.value = value; input.required = true;
        if (type === 'number') { input.min = 0; input.max = 105; input.step = 1; } else input.maxLength = 200;
        wrapper.append(input); form.append(wrapper); return input;
    }
    function button(form, text) { const b = el('button', text); b.type = 'submit'; form.append(b); return b; }
    async function mountEnrollment(catalog, currentScope) {
        const section=el('section');section.className='curriculum-enrollment';root.append(section);
        if(catalog.admin) Array.from(root.children).filter(node=>node!==section).forEach(node=>node.hidden=true);
        const status=el('p');status.setAttribute('role','status');section.append(status);
        const run=async(action)=>{try{status.textContent='';await action();}catch(e){status.textContent=e.message;}};
        const choice=(parent,label,items)=>{
            const wrap=el('label',label+' '),select=el('select');
            for(const item of items){const option=el('option',item.label||item.name);option.value=item.id;select.append(option);}
            wrap.append(select);parent.append(wrap);return select;
        };
        const action=(parent,label,fn)=>{const b=el('button',label);b.type='button';parent.append(b);b.addEventListener('click',()=>run(async()=>{b.disabled=true;try{await fn();}finally{b.disabled=false;}}));return b;};
        const call=async(path,data,permission)=>{if(!await allowed(permission))throw Error(permissionDenied);return post(path,data);};
        if(await allowed('curriculum_publish')) {
            const panel=el('details');panel.hidden=Boolean(catalog.admin);panel.append(el('summary','Themen und Etappen für die Klasse freischalten'));section.append(panel);
            panel.append(el('p','Verwendet die oben ausgewählte Klasse, das Fach und das Halbjahr. Eine Themenfreigabe gilt auch für künftig ergänzte Etappen. Einzelne Etappen können abweichend gesperrt werden. Verdiente Münzen bleiben erhalten.'));
            const content=el('div');panel.append(content);let generation=0;
            const reload=async()=>{
                const version=++generation,scope=currentScope(),snapshot=JSON.stringify(scope);
                const data=await call('/curriculum-releases',scope,'curriculum_publish');if(version!==generation)return;
                content.replaceChildren();
                const save=async(values)=>{
                    if(snapshot!==JSON.stringify(currentScope()))throw Error('Die Auswahl wurde geändert. Bitte Freischaltungen neu laden.');
                    await call('/set-curriculum-release',{...scope,...values},'curriculum_publish');await reload();
                };
                for(const topic of data.topics){
                    const group=el('fieldset');group.append(el('legend',topic.name));content.append(group);
                    action(group,'Ganzes Thema freischalten',()=>save({topicId:topic.id,active:true}));
                    action(group,'Ganzes Thema sperren',()=>save({topicId:topic.id,active:false}));
                    for(const task of data.tasks.filter(t=>t.topicId===topic.id)) {
                        const row=el('p',task.name+' — '+(task.active?'freigeschaltet':'gesperrt')+' ');group.append(row);
                        action(row,task.active?'Etappe sperren':'Etappe freischalten',()=>save({taskId:task.id,active:!task.active}));
                    }
                }
                if(!data.topics.length)content.append(el('p','Für diese Auswahl sind noch keine zentralen Themen angelegt.'));
            };
            action(panel,'Freischaltungen laden',reload);
        }
        if(!catalog.admin || !await allowed('curriculum_manage_enrollment'))return;
        const landing=el('div');landing.className='semester-landing';section.append(landing);
        const panel=el('details');panel.open=true;panel.className='semester-create-panel';panel.append(el('summary','Neues Halbjahr anlegen'));landing.append(panel);
        const semesterForm=el('form'); semesterForm.append(el('h4','Neues Schulhalbjahr anlegen'));
        semesterForm.append(el('p','Der nächste logische Nachfolger wird automatisch angelegt (HJ1 → HJ2 → nächstes Schuljahr HJ1).'));
        button(semesterForm,'Nächstes Halbjahr anlegen'); panel.append(semesterForm);
        semesterForm.addEventListener('submit',async event=>{event.preventDefault();await run(async()=>{const result=await call('/create-curriculum-semester',{},'curriculum_manage_enrollment');status.textContent=`${result.label} angelegt. Auswahlfelder werden aktualisiert.`;window.location.reload();});});
        const managePanel=el('div');managePanel.className='semester-manage-panel';managePanel.append(el('h3','Halbjahr und Klassenstufe verwalten'));const manageButton=el('button','Jetzt verwalten');manageButton.type='button';managePanel.append(manageButton);landing.append(managePanel);manageButton.addEventListener('click',()=>{revealSemesterSetup();});
        const wpfPanel=el('details');wpfPanel.open=true;wpfPanel.append(el('summary','WPF-Zuteilung nach Klasse'));section.append(wpfPanel);
        const loadCatalog=()=>call('/curriculum-enrollment-catalog',{},'curriculum_manage_enrollment');
        const data=await loadCatalog();
        const existing=el('div');existing.append(el('h4','Bereits angelegt'));const list=el('ul');for(const item of data.semesters)list.append(el('li',item.label));existing.append(list);panel.append(existing);
        const semester=choice(panel,'Schulhalbjahr',data.semesters),grade=choice(panel,'Jahrgang',Array.from(new Set(data.classes.map(c=>c.grade))).sort((a,b)=>a-b).map(id=>({id,name:String(id)})));
        const selectorGate=el('fieldset');selectorGate.hidden=true;selectorGate.append(el('legend','Halbjahr und Klassenstufe auswählen'));
        selectorGate.append(semester,grade);const confirmSelection=el('button','Auswahl bestätigen');confirmSelection.type='button';selectorGate.append(confirmSelection);managePanel.append(selectorGate);
        panel.append(el('p','Die Fachart wird vorab unter Schuldaten → Fächer verwalten festgelegt. Religion, Ethik und WPF sind standardmäßig individuell; alle übrigen Fächer gelten als verbindlich.'));
        const mappingArea=el('div');panel.append(mappingArea);let mappingVersion=0,proposal=null;
        mappingArea.hidden=true;wpfPanel.hidden=true;
        confirmSelection.addEventListener('click',()=>{mappingArea.hidden=false;wpfPanel.hidden=false;status.textContent='Auswahl bestätigt. Die Einrichtungsfunktionen sind eingeblendet.';if(typeof prepareTeachersButton?.click==='function')prepareTeachersButton.click();if(typeof loadRoster==='function')loadRoster();});
        revealSemesterSetup=()=>{selectorGate.hidden=false;section.querySelectorAll(':scope > details').forEach(node=>node.hidden=false);status.textContent='Halbjahr und Klassenstufe auswählen und bestätigen.';};
        const invalidate=()=>{mappingVersion++;proposal=null;mappingArea.replaceChildren();};
        const regularSubjects=()=>data.subjects.filter(s=>!s.wpf);semester.addEventListener('change',invalidate);grade.addEventListener('change',invalidate);
        const teacherChoice=(parent,classId,subjectId)=>{
            const candidates=data.teaching.filter(t=>t.classId===classId&&t.subjectId===subjectId).map(t=>t.teacherId);
            const teachers=data.teachers.filter(t=>candidates.includes(t.id)).map(t=>({id:t.id,name:t.first_name+' '+t.last_name}));
            const select=choice(parent,'Lehrkraft',[{id:'',name:'Lehrkraft auswählen'},...teachers]);if(teachers.length===1)select.value=teachers[0].id;return select;
        };
        const prepareTeachersButton=action(panel,'Lehrkräfte-Tabelle aufbauen',async()=>{
            const subjectIds=regularSubjects().map(s=>s.id);if(!subjectIds.length)throw Error('Keine verbindlichen Fächer vorhanden.');
            invalidate();const version=mappingVersion,selectedGrade=Number(grade.value),semesterId=Number(semester.value),mappings=[];
            const classes=data.classes.filter(c=>c.grade===selectedGrade);
            if(!classes.length)throw Error(`Für Klassenstufe ${selectedGrade} sind keine Klassen hinterlegt.`);
            mappingArea.append(el('p',`${classes.length} Klassen; alle verbindlichen Fächer werden zugeordnet.`));
            const filter=el('input');filter.placeholder='Klasse oder Fach filtern';mappingArea.append(filter);const table=el('table');table.className='curriculum-wpf-table';const h=table.insertRow();['Klasse','Fach','Lehrkraft'].forEach((x,i)=>{const th=el('th',x);th.className=i<2?'sortable':'';h.append(th)});const tableRows=[];
            for(const cls of classes)for(const subjectId of subjectIds){const tr=table.insertRow();tr.insertCell().textContent=cls.label;tr.insertCell().textContent=data.subjects.find(s=>s.id===subjectId).name;const cell=tr.insertCell();const input=teacherChoice(cell,cls.id,subjectId);tableRows.push({tr,text:(cls.label+' '+data.subjects.find(s=>s.id===subjectId).name).toLowerCase()});mappings.push({classId:cls.id,subjectId,input});}
            mappingArea.append(table);filter.addEventListener('input',()=>tableRows.forEach(r=>r.tr.hidden=!r.text.includes(filter.value.toLowerCase())));
            proposal={version,subjectIds,selectedGrade,semesterId,mappings};
            action(mappingArea,'Dem gesamten Jahrgang zuordnen',async()=>{
                if(!proposal || version!==mappingVersion)throw Error('Bitte die Zuordnung erneut vorbereiten.');
                const teaching=mappings.map(m=>({classId:m.classId,subjectId:m.subjectId,teacherId:Number(m.input.value)}));
                if(teaching.some(t=>!t.teacherId))throw Error('Bitte für jede Klasse und jedes Fach eine zuständige Lehrkraft auswählen.');
                const result=await call('/assign-grade-curriculum',{grade:selectedGrade,semesterId,subjectIds,teaching},'curriculum_manage_enrollment');
                invalidate();status.textContent=`Gespeichert: ${result.students} Kinder, ${result.assignments} Fachzuordnungen. Zentrale Inhalte erscheinen nach Freischaltung durch die Lehrkraft.`;
            });
        });
        const wpfSemester=choice(wpfPanel,'WPF-Halbjahr',data.semesters),wpfClass=choice(wpfPanel,'WPF-Klasse',data.classes);
        wpfPanel.append(el('p','Pro Kind und Halbjahr genau ein WPF: Kinder auf ein WPF-Feld ziehen oder WPF auswählen und speichern. Die Lehrkraft wird pro WPF ausgewählt.'));
        const board=el('div');board.className='curriculum-wpf-board';board.style.cssText='display:flex;flex-wrap:wrap;gap:1rem';wpfPanel.append(board);let rosterVersion=0;
        const clearBoard=()=>{rosterVersion++;board.replaceChildren();};wpfClass.addEventListener('change',clearBoard);wpfSemester.addEventListener('change',clearBoard);
        const loadRoster=async()=>{
            clearBoard();const version=rosterVersion,classId=Number(wpfClass.value),semesterId=Number(wpfSemester.value);
            const fresh=await loadCatalog();data.subjects=fresh.subjects;data.teaching=fresh.teaching;data.teachers=fresh.teachers;
            const pupils=await call('/curriculum-wpf-roster',{classId,semesterId},'curriculum_manage_enrollment');if(version!==rosterVersion)return;
            const controls=el('div');controls.className='curriculum-table-filters';const search=el('input');search.placeholder='Schüler filtern';const subjectFilter=el('select');[{id:'',name:'Alle Fächer'},...subjects].forEach(s=>{const o=el('option',s.name);o.value=s.id;subjectFilter.append(o)});controls.append(search,subjectFilter);board.append(controls);
            const table=document.createElement('table');table.className='curriculum-wpf-table';const head=table.insertRow();['Schüler','Individuelles Fach','Lehrkraft'].forEach((t,i)=>{const th=document.createElement('th');th.textContent=t;if(i<2){th.className='sortable';th.title='Zum Sortieren klicken';th.addEventListener('click',()=>{const key=i===0?'name':'subject';rows.sort((a,b)=>{const av=key==='name'?a.student.last_name+' '+a.student.first_name:(subjects.find(s=>s.id===Number(a.wpf.value))?.name||'');const bv=key==='name'?b.student.last_name+' '+b.student.first_name:(subjects.find(s=>s.id===Number(b.wpf.value))?.name||'');return av.localeCompare(bv,'de')});rows.forEach(r=>table.append(r.tr));});}head.append(th)});const rows=[];
            for(const student of pupils){const tr=table.insertRow();tr.insertCell().textContent=student.first_name+' '+student.last_name;const wpf=choice(tr.insertCell(),'WPF',[{id:0,name:'Noch nicht zugeordnet'},...subjects]);wpf.value=String(student.subjectId||0);const teacherCell=tr.insertCell();const teacher=teacherChoice(teacherCell,classId,Number(wpf.value));rows.push({student,tr,wpf,teacherCell,teacher});wpf.addEventListener('change',()=>{teacherCell.replaceChildren();rows.find(r=>r.student.id===student.id).teacher=teacherChoice(teacherCell,classId,Number(wpf.value));});}
            const filter=()=>rows.forEach(r=>{const text=(r.student.first_name+' '+r.student.last_name).toLowerCase();r.tr.hidden=!!(search.value&& !text.includes(search.value.toLowerCase()) || subjectFilter.value && r.wpf.value!==subjectFilter.value);});search.addEventListener('input',filter);subjectFilter.addEventListener('change',filter);
            board.append(table);if(!pupils.length)board.append(el('p','In dieser Klasse sind noch keine Kinder eingetragen.'));
            action(board,'Alle WPF-Zuordnungen speichern',async()=>{for(const row of rows){const subjectId=Number(row.wpf.value);if(!subjectId)continue;const teacherId=Number(row.teacher.value);if(!teacherId)throw Error('Bitte für jedes gewählte WPF eine Lehrkraft auswählen.');await call('/assign-curriculum-wpf',{studentId:row.student.id,subjectId,classId,semesterId,teacherId,expectedSubjectId:row.student.subjectId??null},'curriculum_manage_enrollment');}status.textContent='WPF- und Lehrkräfte-Zuordnung gespeichert.';});
        };
        action(wpfPanel,'WPF-Liste laden',loadRoster);
    }
    try {
        if (hasPM) {
            try {
                if (typeof permissionsLoaded !== 'undefined') await permissionsLoaded;
                else if (typeof loadCurrentPermissions === 'function') await loadCurrentPermissions();
                permissionsReady = true;
            } catch { permissionsReady = false; }
        }
        if (!await allowed('curriculum_view')) { message.textContent = 'Das Curriculum ist derzeit nicht verfügbar.'; return; }
        const catalog = await post('/curriculum-catalog');
        const subject = select('Fach', catalog.subjects, 'subjectId');
        const semester = select('Halbjahr', catalog.semesters, 'semesterId');
        const schoolClass = select('Klasse/Lerngruppe', catalog.classes, 'classId');
        const teacher = catalog.admin ? select('Lehrkraft (flexible Etappen)', catalog.teachers.map(t => ({id:t.id,name:t.first_name+' '+t.last_name})), 'teacherId') : null;
        const grade = catalog.admin ? select('Jahrgang (zentral)', Array.from({length:13},(_,i)=>({id:i+1,name:String(i+1)})), 'grade') : null;
        if (grade) grade.value = String(catalog.classes[0]?.grade || 5);
        const load = el('button', 'Anzeigen / Aktualisieren'); load.type = 'button'; root.append(load);
        const summary = el('p'), central = el('section'), flexible = el('section'), assignments = el('section'); root.append(summary, central, flexible, assignments);
        const genericControls=[subject,semester,schoolClass,teacher,grade,load,summary,central,flexible,assignments].filter(Boolean);
        if(catalog.admin) genericControls.forEach(node=>node.hidden=true);
        const previousReveal=revealSemesterSetup; revealSemesterSetup=()=>{previousReveal();genericControls.forEach(node=>node.hidden=false);};
        let version = 0;
        async function refresh() {
            const current = ++version;
            message.textContent = '';
            central.replaceChildren(); flexible.replaceChildren(); assignments.replaceChildren(); summary.textContent = '';
            if (!await allowed('curriculum_view')) throw Error(permissionDenied);
            const manageAssignments = catalog.admin === true && await allowed('curriculum_assign_context');
            const manageFlexible = await allowed('curriculum_manage_flexible');
            const manageCentral = catalog.admin === true && await allowed('curriculum_manage_central');
            const scope = {subjectId:Number(subject.value),semesterId:Number(semester.value),classId:Number(schoolClass.value),teacherId:teacher?Number(teacher.value):catalog.teacherId};
            const selectedClass = catalog.classes.find(c => c.id === scope.classId);
            const g = grade ? Number(grade.value) : selectedClass?.grade;
            if (!scope.subjectId || !scope.semesterId || !g) { message.textContent='Noch keine passenden Fächer, Klassen oder Halbjahre vorhanden.'; return; }
            const structure = await post('/curriculum-structure', {...scope,grade:g});
            if (current !== version) return;
            central.replaceChildren(el('h3','Zentrale Themen und Etappen'));
            summary.textContent = `Zentrale Summe Jahrgang ${g}: ${structure.centralTokens} / 100 Münzen (absolute Grenze 105).`;
            summary.style.color = structure.centralTokens > 100 ? 'darkred' : '';
            async function save(path, data) {
                const isAssignment = path === '/assign-curriculum-context' || path === '/transfer-curriculum-context';
                const isFlexible = ['/add-flexible-task','/edit-flexible-task','/add-flexible-topic','/rename-flexible-topic'].includes(path);
                if (current !== version) throw Error('Die Auswahl wurde geändert. Bitte das aktuelle Formular verwenden.');
                if ((!isFlexible && catalog.admin !== true) || !await allowed('curriculum_view') ||
                    !await allowed(isAssignment ? 'curriculum_assign_context' : isFlexible ? 'curriculum_manage_flexible' : 'curriculum_manage_central')) {
                    await refresh(); throw Error(permissionDenied);
                }
                await post(path, data); await refresh();
            }
            function editTask(parent, task, isFlexible) {
                const form = el('form'), name = field(form,'Name',task.name), tokens = field(form,'Münzen',task.tokens,'number');
                const topic = isFlexible ? topicField(form, task.topicId) : null;
                form.append(el('small','Geänderte Münzwerte gelten auch für bereits abgeschlossene Etappen.'));
                button(form,'Speichern');
                form.addEventListener('submit', async event => {event.preventDefault();try {
                    const base = isFlexible ? budget.totalTokens : structure.centralTokens;
                    if (base-task.tokens+Number(tokens.value)>105) throw Error('Die Summe darf 105 Münzen nicht überschreiten.');
                    await save(isFlexible?'/edit-flexible-task':'/edit-task',{taskId:task.id,name:name.value,tokens:Number(tokens.value),...(isFlexible?{topicId:topic.value?Number(topic.value):null}:{})});
                } catch(error){showError(error);} });parent.append(form);
            }
            let budget, flexibleTopics = [];
            function topicField(form, selected) {
                const label=el('label','Thema '), choice=el('select');choice.name='topicId';
                for(const item of [{id:'',name:'Noch keinem Thema zugeordnet'},...flexibleTopics]) {
                    const option=el('option',item.name);option.value=String(item.id);choice.append(option);
                }
                choice.value=selected==null?'':String(selected);label.append(choice);form.append(label);return choice;
            }
            for (const topic of structure.topics) {
                const section=el('details'), title=el('summary',topic.name+(manageCentral?' — Etappen anzeigen / bearbeiten':' — Etappen anzeigen'));section.append(title);central.append(section);
                if(manageCentral) {
                    const form=el('form'), name=field(form,'Themenname',topic.name);button(form,'Thema umbenennen');section.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/rename-topic',{topicId:topic.id,name:name.value});}catch(error){showError(error);}});
                }
                for(const task of structure.tasks.filter(t=>t.topic===topic.id)) {
                    if(manageCentral) editTask(section,task,false);else section.append(el('p',task.name+': '+task.tokens+' Münzen'));
                }
                if(manageCentral) {
                    const form=el('form'), name=field(form,'Neue Etappe',''), tokens=field(form,'Münzen',0,'number'), level=field(form,'Niveau',1,'number');level.min=1;level.max=3;button(form,'Etappe anlegen');section.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{
                        if(structure.centralTokens+Number(tokens.value)>105) throw Error('Zentrale Summe über 105.');
                        await save('/add-curriculum-task',{topicId:topic.id,name:name.value,tokens:Number(tokens.value),level:Number(level.value)});
                    }catch(error){showError(error);}});
                }
            }
            if(manageCentral) {
                const form=el('form'), name=field(form,'Neues Thema',''), number=field(form,'Nummer',Math.max(0,...structure.topics.map(t=>t.number))+1,'number');number.min=1;number.removeAttribute('max');button(form,'Thema anlegen');central.append(form);
                form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/add-curriculum-topic',{...scope,grade:g,number:Number(number.value),name:name.value});}catch(error){showError(error);}});
            }
            flexible.replaceChildren(el('h3','Flexible Lehrer-Etappen'));
            if(!scope.classId || !scope.teacherId){flexible.append(el('p','Für flexible Etappen Klasse und Lehrkraft auswählen.'));return;}
            budget=await post('/curriculum-budget',scope);
            const flexibleStructure=await post('/flexible-curriculum-structure',scope);if(current!==version)return;
            const tasks=flexibleStructure.tasks;flexibleTopics=flexibleStructure.topics;
            const status=el('p',`Kontext Jahrgang ${budget.grade}: zentral ${budget.centralTokens} + eigene ${budget.flexibleTokens} = ${budget.totalTokens}. Rest regulär: ${budget.remainingRegular}; Rest bis 105: ${budget.remainingHard}.`);
            status.style.color=budget.totalTokens>100?'darkred':'';flexible.append(status);
            if (!manageFlexible) flexible.append(el('p','Nur lesbar.'));
            flexible.append(el('p','Diese Münzen sind geplant. Verdient werden sie erst durch bestätigte Abschlüsse.'));
            for(const topic of [...flexibleTopics,{id:null,name:'Noch keinem Thema zugeordnet'}]) {
                const grouped=tasks.filter(task=>(task.topicId??null)===topic.id);
                if(topic.id===null && !grouped.length) continue;
                const group=el('details');group.open=true;group.append(el('summary',topic.name));flexible.append(group);
                if(manageFlexible && topic.id!==null) {
                    const form=el('form'),name=field(form,'Themenname',topic.name);button(form,'Flexibles Thema umbenennen');group.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/rename-flexible-topic',{topicId:topic.id,name:name.value});}catch(error){showError(error);}});
                }
                for(const task of grouped) {
                    if(manageFlexible) editTask(group,task,true);
                    else group.append(el('p',task.name+': '+task.tokens+' Münzen'));
                }
            }
            if (manageAssignments) {
                const students = await post('/curriculum-students',scope); if(current!==version)return;
                assignments.append(el('h3','Schüler einem Unterrichtskontext zuweisen'),el('p','Die Auswahl oben bestimmt Lehrkraft, Fach, Klasse und Halbjahr. Bestehende flexible Abschlüsse erfordern eine ausdrückliche Leistungsübernahme.'));
                for (const student of students) {
                    const row=el('div'); assignments.append(row);
                    row.append(el('p',student.first_name+' '+student.last_name+' — '+(student.teacherId ? 'zugewiesene Lehrkraft: '+student.teacherId : 'noch nicht zugewiesen')));
                    const form=el('form');button(form,'Unterrichtskontext zuweisen');row.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/assign-curriculum-context',{...scope,studentId:student.id});}catch(error){showError(error);}});
                    if (student.teacherId===scope.teacherId && student.classId===scope.classId) continue;
                    const preview=el('button','Wechsel mit Leistungsübernahme vorbereiten');preview.type='button';row.append(preview);
                    const transferArea=el('div');row.append(transferArea);
                    preview.addEventListener('click',async()=>{try{
                        if(!await allowed('curriculum_assign_context'))throw Error(permissionDenied);
                        const proposal=await post('/curriculum-transfer-preview',{...scope,studentId:student.id});if(current!==version)return;
                        transferArea.replaceChildren();
                        const transferForm=el('form'), mappings=[];
                        transferForm.append(el('p','Jeden bisherigen Abschluss einer gleichwertigen Zieletappe zuordnen. Die Historie bleibt erhalten; künftig gilt der aktuelle Münzwert der Zieletappe.'));
                        for(const task of proposal.completions) {
                            const label=el('label',task.name+' ('+task.tokens+' Münzen) → '), target=el('select');target.required=true;
                            const placeholder=el('option','Zieletappe auswählen');placeholder.value='';target.append(placeholder);
                            for(const candidate of proposal.targets.filter(t=>t.tokens===task.tokens)) {const option=el('option',candidate.name+' ('+candidate.tokens+' Münzen)');option.value=candidate.id;target.append(option);}
                            label.append(target);transferForm.append(label);mappings.push({task,target});
                        }
                        button(transferForm,'Wechsel und Leistungsübernahme bestätigen');transferArea.append(transferForm);
                        transferForm.addEventListener('submit',async e=>{e.preventDefault();try{
                            const transfers=mappings.map(({task,target})=>({sourceTaskId:task.id,targetTaskId:Number(target.value),tokens:task.tokens}));
                            if(transfers.some(t=>!t.targetTaskId) || new Set(transfers.map(t=>t.targetTaskId)).size!==transfers.length)throw Error('Bitte für jeden Abschluss eine eigene gleichwertige Zieletappe auswählen.');
                            await save('/transfer-curriculum-context',{...scope,studentId:student.id,sourceTeacherId:proposal.source.teacherId,sourceClassId:proposal.source.classId,transfers});
                        }catch(error){showError(error);}});
                    }catch(error){showError(error);}});
                }
            }
            if (!manageFlexible) return;
            const topicForm=el('form'),topicName=field(topicForm,'Neues flexibles Thema','');button(topicForm,'Flexibles Thema anlegen');flexible.append(topicForm);
            topicForm.addEventListener('submit',async e=>{e.preventDefault();try{await save('/add-flexible-topic',{...scope,name:topicName.value});}catch(error){showError(error);}});
            const form=el('form'), name=field(form,'Neue flexible Etappe',''), tokens=field(form,'Münzen',0,'number'), topic=topicField(form,null);button(form,'Flexible Etappe anlegen');flexible.append(form);
            form.addEventListener('submit',async e=>{e.preventDefault();try{
                if(budget.totalTokens+Number(tokens.value)>105)throw Error('Dieses Unterrichtsbudget würde 105 überschreiten.');
                await save('/add-flexible-task',{...scope,name:name.value,tokens:Number(tokens.value),topicId:topic.value?Number(topic.value):null});
            }catch(error){showError(error);}});
        }
        for(const node of [subject,semester,schoolClass,teacher,grade].filter(Boolean))node.addEventListener('change',()=>refresh().catch(showError));
        load.addEventListener('click',()=>refresh().catch(showError));await refresh().catch(showError);
        if(catalog.enrollmentEnabled)await mountEnrollment(catalog,()=>({subjectId:Number(subject.value),semesterId:Number(semester.value),classId:Number(schoolClass.value),teacherId:teacher?Number(teacher.value):catalog.teacherId}));
    } catch(error) { showError(error); }
});
