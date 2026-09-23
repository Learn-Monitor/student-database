/* Standard curriculum editor: all mutations are validated again by the server. */
'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    const adminEnrollmentRoot = document.querySelector('#admin-enrollment');
    const adminCentralRoot = document.querySelector('#admin-central-curriculum');
    const overviewRoot = document.querySelector('#overview');
    const progressRoot = document.querySelector('#student-progress');
    const separatedAdmin = Boolean(adminEnrollmentRoot && adminCentralRoot);
    const root = adminCentralRoot || document.querySelector('#curriculum');
    if (!root && !adminEnrollmentRoot) return;
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
    let selectMount = root;
    function select(label, items, name) {
        const wrapper = el('label', label + ' '), node = el('select'); node.name = name;
        for (const item of items) { const option = el('option', item.label || item.name); option.value = item.id; node.append(option); }
        wrapper.append(node); selectMount.append(wrapper); return node;
    }
    const uniqueBy = (items, key) => {
        const seen = new Set(), out = [];
        for (const item of items) { const value = key(item); if (seen.has(value)) continue; seen.add(value); out.push(item); }
        return out;
    };
    function renderTeacherOverview(activeContexts) {
        if (!overviewRoot) return;
        const contexts = Array.isArray(activeContexts) ? activeContexts : [];
        const classes = uniqueBy(contexts, context => context.classId);
        const subjects = uniqueBy(contexts, context => context.subjectId);
        const semesters = uniqueBy(contexts, context => context.semesterId);
        const value = (title, content, detail) => {
            const card = el('article'); card.className = 'teacher-status-card';
            card.append(el('h3', title), el('div', content, 'teacher-status-value'));
            if (detail) card.append(el('div', detail, 'teacher-status-detail'));
            return card;
        };
        const grid = el('div'); grid.className = 'teacher-status-grid';
        const semesterLabel = semesters.map(context => context.semesterLabel).filter(Boolean).join(', ') || 'Nicht verfügbar';
        grid.append(
            value('Aktives Halbjahr', semesterLabel),
            value('Lerngruppen / Klassen', String(classes.length)),
            value('Fächer', String(subjects.length)),
            value('Unterrichtskontexte', String(contexts.length))
        );
        const contextPanel = el('section'); contextPanel.className = 'teacher-context-panel';
        contextPanel.append(el('h3', 'Meine Unterrichtskontexte'));
        if (contexts.length) {
            const list = el('ul'); list.className = 'teacher-context-list';
            for (const context of contexts) list.append(el('li', `${context.classLabel || 'Lerngruppe'} · ${context.subjectName || 'Fach'}`));
            contextPanel.append(list);
        } else contextPanel.append(el('p', 'Für das aktive Halbjahr sind keine Unterrichtskontexte zugewiesen.'));
        const quickLinks = el('nav'); quickLinks.className = 'teacher-quick-links'; quickLinks.setAttribute('aria-label', 'Schnellzugriffe');
        for (const [label, href] of [
            ['Themen & Etappen öffnen', '/dashboard#curriculum'],
            ['Schülerfortschritt öffnen', '/dashboard#student-progress'],
            ['Anwesenheit öffnen', '/attendance']
        ]) {
            const link = el('a', label); link.href = href; quickLinks.append(link);
        }
        overviewRoot.replaceChildren(el('h2', 'Übersicht'), grid, contextPanel, quickLinks);
    }
    async function mountStudentProgress(catalog, activeContexts) {
        if (!progressRoot || catalog.admin === true) return;
        const panel = el('div'), status = el('p'), rosterArea = el('div'), detailArea = el('div');
        panel.className = 'teacher-filter-bar';
        rosterArea.className = 'teacher-roster-panel';
        detailArea.className = 'teacher-detail-panel';
        const workspace = el('div'); workspace.className = 'teacher-progress-layout'; workspace.append(rosterArea, detailArea);
        status.setAttribute('role', 'status');
        progressRoot.append(panel, status, workspace);
        const classes = uniqueBy(activeContexts, context => context.classId).map(context => ({id:context.classId,name:context.classLabel}));
        if (!classes.length) {
            status.textContent = 'Für das aktive Halbjahr sind keine Lerngruppen oder Fächer zugewiesen.';
            return;
        }
        const progressSelect = (label, items, name) => {
            const wrapper = el('label', label + ' '), node = el('select'); node.name = name;
            for (const item of items) { const option = el('option', item.label || item.name); option.value = item.id; node.append(option); }
            wrapper.append(node); panel.append(wrapper); return node;
        };
        const classSelect = progressSelect('Klasse/Lerngruppe', classes, 'classId');
        const subjectSelect = progressSelect('Fach', [], 'subjectId');
        const reload = el('button', 'Aktualisieren'); reload.type = 'button'; panel.append(reload);
        let generation = 0, detailGeneration = 0, selectedStudentId = null;
        function contextsForClass() {
            return activeContexts.filter(context => context.classId === Number(classSelect.value));
        }
        function rebuildSubjects() {
            const subjects = uniqueBy(contextsForClass(), context => context.subjectId).map(context => ({id:context.subjectId,name:context.subjectName}));
            subjectSelect.replaceChildren();
            for (const subject of subjects) { const option = el('option', subject.name); option.value = subject.id; subjectSelect.append(option); }
        }
        function selectedContext() {
            return activeContexts.find(context => context.classId === Number(classSelect.value) && context.subjectId === Number(subjectSelect.value)) || null;
        }
        function stageText(stage) {
            if (!stage) return 'Keine Etappe in Bearbeitung';
            if (stage.type === 'CENTRAL') return `${stage.name || ''} · Zentral`;
            if (stage.type === 'FLEXIBLE') return `${stage.name || ''} · Flexibel`;
            return stage.name || 'Unbekannte Etappe';
        }
        function signalText(value) { return value === true ? 'Ja' : '—'; }
        const assessmentText = status => ({
            PASSED:'Bestanden', FAILED_ONCE:'1× nicht bestanden', FAILED_TWICE:'2× nicht bestanden', LOCKED:'Gesperrt'
        })[status] || 'Noch nicht bewertet';
        function resetDetail() {
            selectedStudentId = null;
            detailGeneration++;
            detailArea.replaceChildren();
        }
        const assessmentOptions = [
            ['PASSED','Bestanden'], ['FAILED_ONCE','1× nicht bestanden'],
            ['FAILED_TWICE','2× nicht bestanden'], ['LOCKED','Gesperrt']
        ];
        const assessmentWrites = new Set();
        function renderDetail(detail, canAssess) {
            detailArea.replaceChildren();
            const detailStatus = el('p'); detailStatus.setAttribute('role','status');
            detailArea.append(el('h3', detail.studentName || 'Schülerdetail'),detailStatus);
            const table = document.createElement('table'), head = table.insertRow();
            ['Thema','Etappe','Typ','Münzen','Bewertung','Verdient','In Bearbeitung']
                .forEach(text => head.append(el('th', text)));
            for (const stage of Array.isArray(detail.stages) ? detail.stages : []) {
                const tr = table.insertRow();
                tr.insertCell().textContent = stage.topicName || 'Ohne Thema';
                tr.insertCell().textContent = stage.name || '';
                tr.insertCell().textContent = stage.type === 'CENTRAL' ? 'Zentral' : stage.type === 'FLEXIBLE' ? 'Flexibel' : '';
                tr.insertCell().textContent = String(stage.tokens ?? 0);
                const assessmentCell=tr.insertCell();assessmentCell.append(el('span',assessmentText(stage.status)));
                if(canAssess && (stage.type==='CENTRAL'||stage.type==='FLEXIBLE')) {
                    const control=el('select');control.className='assessment-control';control.dataset.stageType=stage.type;control.dataset.stageId=stage.stageId;
                    const placeholder=el('option','Bewertung wählen');placeholder.value='';placeholder.disabled=true;control.append(placeholder);
                    for(const [value,label] of assessmentOptions){const option=el('option',label);option.value=value;control.append(option);}
                    control.value=stage.status||'';
                    const save=el('button','Speichern');save.type='button';save.className='assessment-save';
                    save.addEventListener('click',async()=>{
                        const key=`${stage.type}:${stage.stageId}`;
                        if(assessmentWrites.has(key)||!control.value)return;
                        assessmentWrites.add(key);control.disabled=true;save.disabled=true;detailStatus.textContent='Bewertung wird gespeichert.';
                        try {
                            if(!await allowed('curriculum_assess_students'))throw Error(permissionDenied);
                            const context=selectedContext();
                            if(!context||selectedStudentId!==detail.studentId)throw Error('Die Auswahl wurde geändert. Bitte Details neu öffnen.');
                            await post('/set-curriculum-stage-assessment',{
                                studentId:detail.studentId,subjectId:context.subjectId,classId:context.classId,semesterId:context.semesterId,
                                stageType:stage.type,stageId:stage.stageId,status:control.value
                            });
                            detailStatus.textContent='Bewertung gespeichert.';
                            await loadRoster();
                        } catch(error) {
                            detailStatus.textContent=error.message;detailStatus.style.color='darkred';
                        } finally {
                            assessmentWrites.delete(key);control.disabled=false;save.disabled=false;
                        }
                    });
                    assessmentCell.append(document.createElement('br'),control,save);
                }
                tr.insertCell().textContent = stage.earned === true ? 'Ja' : '—';
                tr.insertCell().textContent = stage.inProgress === true ? 'In Bearbeitung' : '—';
            }
            const tableWrap = el('div'); tableWrap.className = 'teacher-table-scroll'; tableWrap.append(table);
            detailArea.append(tableWrap);
        }
        async function loadDetail(studentId) {
            const context = selectedContext();
            if (!context) return;
            selectedStudentId = studentId;
            const current = ++detailGeneration;
            const snapshot = `${context.classId}:${context.subjectId}:${context.semesterId}:${studentId}`;
            detailArea.replaceChildren(el('p', 'Details werden geladen.'));
            try {
                const detail = await post('/curriculum-student-progress-detail', {
                    studentId,
                    subjectId: context.subjectId,
                    classId: context.classId,
                    semesterId: context.semesterId
                });
                const selected = selectedContext();
                const latest = selected ? `${selected.classId}:${selected.subjectId}:${selected.semesterId}:${selectedStudentId}` : '';
                if (current !== detailGeneration || snapshot !== latest) return;
                const canAssess=await allowed('curriculum_assess_students');
                if(current!==detailGeneration)return;
                renderDetail(detail,canAssess);
            } catch (error) {
                if (current !== detailGeneration) return;
                detailArea.replaceChildren(el('p', error.message));
            }
        }
        function renderRoster(rows) {
            rosterArea.replaceChildren();
            if (!Array.isArray(rows) || !rows.length) {
                rosterArea.append(el('p', 'Für diesen Unterrichtskontext sind keine Schüler zugeordnet.'));
                return;
            }
            const table = document.createElement('table'), head = table.insertRow();
            ['Schüler','In Bearbeitung','Braucht Hilfe','Sucht Partner','Will experimentieren','Bereit für Gelingensnachweis','Details']
                .forEach(text => head.append(el('th', text)));
            for (const row of rows) {
                const tr = table.insertRow(), signals = row.signals || {};
                tr.insertCell().textContent = row.name || `${row.firstName || ''} ${row.lastName || ''}`.trim();
                tr.insertCell().textContent = stageText(row.activeStage);
                tr.insertCell().textContent = signalText(signals.help);
                tr.insertCell().textContent = signalText(signals.partner);
                tr.insertCell().textContent = signalText(signals.experiment);
                tr.insertCell().textContent = signalText(signals.exam);
                const detailButton = el('button', 'Details'); detailButton.type = 'button';
                detailButton.addEventListener('click', () => loadDetail(row.id));
                tr.insertCell().append(detailButton);
            }
            const tableWrap = el('div'); tableWrap.className = 'teacher-table-scroll'; tableWrap.append(table);
            rosterArea.append(tableWrap);
        }
        async function loadRoster() {
            const current = ++generation, context = selectedContext();
            status.textContent = ''; status.style.color = ''; rosterArea.replaceChildren();
            if (!context) {
                status.textContent = 'Für diese Auswahl ist kein Unterrichtskontext zugewiesen.';
                return;
            }
            try {
                const rows = await post('/curriculum-teacher-roster', {
                    subjectId: context.subjectId,
                    semesterId: context.semesterId,
                    classId: context.classId,
                    teacherId: catalog.teacherId
                });
                if (current !== generation) return;
                renderRoster(rows);
                if (selectedStudentId != null && rows.some(row => row.id === selectedStudentId)) await loadDetail(selectedStudentId);
                else if (selectedStudentId != null) resetDetail();
            } catch (error) {
                if (current !== generation) return;
                rosterArea.replaceChildren();
                status.textContent = error.message;
                status.style.color = 'darkred';
            }
        }
        classSelect.addEventListener('change', () => { resetDetail(); rebuildSubjects(); loadRoster(); });
        subjectSelect.addEventListener('change', () => { resetDetail(); loadRoster(); });
        reload.addEventListener('click', loadRoster);
        rebuildSubjects();
        await loadRoster();
    }
    function field(form, label, value, type = 'text') {
        const wrapper = el('label', label + ' '), input = el('input'); input.type = type; input.value = value; input.required = true;
        if (type === 'number') { input.min = 0; input.max = 105; input.step = 1; } else input.maxLength = 200;
        wrapper.append(input); form.append(wrapper); return input;
    }
    function button(form, text) { const b = el('button', text); b.type = 'submit'; form.append(b); return b; }
    async function mountEnrollment(catalog, currentScope, mount=root) {
        const section=el('section');section.className='curriculum-enrollment';mount.append(section);
        const status=el('p');status.setAttribute('role','status');section.append(status);
        const run=async(action)=>{try{status.textContent='';await action();}catch(e){status.textContent=e.message;}};
        const choice=(parent,label,items)=>{
            const wrap=el('label',label+' '),select=el('select');
            for(const item of items){const option=el('option',item.label||item.name);option.value=item.id;select.append(option);}
            wrap.append(select);parent.append(wrap);return select;
        };
        const action=(parent,label,fn)=>{const b=el('button',label);b.type='button';parent.append(b);b.addEventListener('click',()=>run(async()=>{b.disabled=true;try{await fn();}finally{b.disabled=false;}}));return b;};
        const call=async(path,data,permission)=>{if(!await allowed(permission))throw Error(permissionDenied);return post(path,data);};
        if(catalog.admin && await allowed('curriculum_publish')) {
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
        const wpfPanel=el('details');wpfPanel.open=true;wpfPanel.append(el('summary','Individuelle Fächer'));section.append(wpfPanel);
        const loadCatalog=()=>call('/curriculum-enrollment-catalog',{},'curriculum_manage_enrollment');
        const data=await loadCatalog();
        const existing=el('div');existing.append(el('h4','Bereits angelegt'));const list=el('ul');for(const item of data.semesters){const li=el('li',item.label+(item.active?' · aktiv':' '));if(!item.active){const activate=el('button','Als aktives Halbjahr setzen');activate.type='button';activate.addEventListener('click',()=>run(async()=>{if(!confirm('Dieses Halbjahr als aktives Halbjahr verwenden?'))return;await call('/activate-curriculum-semester',{semesterId:item.id},'curriculum_manage_enrollment');status.textContent='Aktives Halbjahr gesetzt.';window.location.reload();}));li.append(' ',activate);}list.append(li);}existing.append(list);panel.append(existing);
        const semester=choice(panel,'Schulhalbjahr',data.semesters),grade=choice(panel,'Jahrgang',Array.from(new Set(data.classes.map(c=>c.grade))).sort((a,b)=>a-b).map(id=>({id,name:String(id)})));
        const selectorGate=el('fieldset');selectorGate.hidden=true;selectorGate.append(el('legend','Halbjahr und Klassenstufe auswählen'));
        selectorGate.append(semester,grade);const confirmSelection=el('button','Auswahl bestätigen');confirmSelection.type='button';selectorGate.append(confirmSelection);managePanel.append(selectorGate);
        panel.append(el('p','Die Fachart wird vorab unter Schuldaten → Fächer verwalten festgelegt. Religion, Ethik und WPF sind standardmäßig individuell; alle übrigen Fächer gelten als verbindlich.'));
        const mappingArea=el('div');panel.append(mappingArea);let mappingVersion=0,proposal=null;
        mappingArea.hidden=true;wpfPanel.hidden=true;
        confirmSelection.addEventListener('click',()=>{mappingArea.hidden=false;wpfPanel.hidden=false;status.textContent='Auswahl bestätigt. Die Einrichtungsfunktionen sind eingeblendet.';if(typeof prepareTeachersButton?.click==='function')prepareTeachersButton.click();if(typeof loadRoster==='function')loadRoster();});
        revealSemesterSetup=()=>{selectorGate.hidden=false;section.querySelectorAll(':scope > details').forEach(node=>node.hidden=false);status.textContent='Halbjahr und Klassenstufe auswählen und bestätigen.';};
        const invalidate=()=>{mappingVersion++;proposal=null;mappingArea.replaceChildren();};
        const regularSubjects=()=>data.subjects.filter(s=>(s.mode||'REGULAR')!=='INDIVIDUAL'&&!s.wpf);semester.addEventListener('change',invalidate);grade.addEventListener('change',invalidate);
        const teacherChoice=(parent,classId,subjectId)=>{
            const teachers=data.teachers.map(t=>({id:t.id,name:t.first_name+' '+t.last_name}));
            const select=choice(parent,'Lehrkraft',[{id:'',name:'Lehrkraft auswählen'},...teachers]);
            const existing=data.teaching.find(t=>t.classId===classId&&t.subjectId===subjectId&&t.semesterId===Number(semester.value));
            if(existing)select.value=String(existing.teacherId);
            return select;
        };
        const prepareTeachersButton=action(panel,'Lehrkräfte-Tabelle aufbauen',async()=>{
            const subjectIds=regularSubjects().map(s=>s.id);if(!subjectIds.length)throw Error('Keine verbindlichen Fächer vorhanden.');
            invalidate();const version=mappingVersion,selectedGrade=Number(grade.value),semesterId=Number(semester.value),mappings=[];
            const classes=data.classes.filter(c=>c.grade===selectedGrade);
            if(!classes.length)throw Error(`Für Klassenstufe ${selectedGrade} sind keine Klassen hinterlegt.`);
            mappingArea.append(el('p',`${classes.length} Klassen; alle verbindlichen Fächer werden angezeigt.`));
            const filter=el('input');filter.placeholder='Klasse oder Fach filtern';mappingArea.append(filter);const table=el('table');table.className='curriculum-wpf-table';const h=table.insertRow();['Klasse','Fach','Lehrkraft'].forEach((x,i)=>{const th=el('th',x);th.className=i<2?'sortable':'';h.append(th)});const tableRows=[];
            for(const cls of classes)for(const subjectId of subjectIds){const tr=table.insertRow();tr.insertCell().textContent=cls.label;tr.insertCell().textContent=data.subjects.find(s=>s.id===subjectId).name;const cell=tr.insertCell();const input=teacherChoice(cell,cls.id,subjectId);tableRows.push({tr,text:(cls.label+' '+data.subjects.find(s=>s.id===subjectId).name).toLowerCase()});mappings.push({classId:cls.id,subjectId,input});}
            mappingArea.append(table);filter.addEventListener('input',()=>tableRows.forEach(r=>r.tr.hidden=!r.text.includes(filter.value.toLowerCase())));
            proposal={version,subjectIds,selectedGrade,semesterId,mappings};
            action(mappingArea,'Ausgewählte Lehrkräfte-Zuordnungen speichern',async()=>{
                if(!proposal || version!==mappingVersion)throw Error('Bitte die Zuordnung erneut vorbereiten.');
                const teaching=mappings.filter(m=>m.input.value).map(m=>({classId:m.classId,subjectId:m.subjectId,teacherId:Number(m.input.value)}));
                if(!teaching.length)throw Error('Bitte mindestens eine Lehrkraft auswählen.');
                const result=await call('/assign-grade-curriculum',{grade:selectedGrade,semesterId,subjectIds,teaching},'curriculum_manage_enrollment');
                invalidate();status.textContent=`Gespeichert: ${result.students} Kinder, ${result.assignments} Fachzuordnungen. Zentrale Inhalte erscheinen nach Freischaltung durch die Lehrkraft.`;
            });
        });
        const groups=()=>Array.from(new Set(data.subjects.filter(s=>(s.mode==='INDIVIDUAL'||s.wpf)&&s.assignmentGroup).map(s=>s.assignmentGroup))).sort();
        const wpfSemester=choice(wpfPanel,'Halbjahr',data.semesters),wpfClass=choice(wpfPanel,'Klasse',data.classes),groupSelect=choice(wpfPanel,'Zuordnungsgruppe',groups().map(g=>({id:g,name:g==='RELIGION_ETHIK'?'Religion / Ethik':g})));
        wpfPanel.append(el('p','Pro Kind und Halbjahr genau ein Fach je Zuordnungsgruppe. WPF und Religion/Ethik koennen parallel gespeichert werden.'));
        const board=el('div');board.className='curriculum-wpf-board';board.style.cssText='display:flex;flex-wrap:wrap;gap:1rem';wpfPanel.append(board);let rosterVersion=0;
        const clearBoard=()=>{rosterVersion++;board.replaceChildren();};wpfClass.addEventListener('change',clearBoard);wpfSemester.addEventListener('change',clearBoard);groupSelect.addEventListener('change',clearBoard);
        const loadRoster=async()=>{
            clearBoard();const version=rosterVersion,classId=Number(wpfClass.value),semesterId=Number(wpfSemester.value);
            const fresh=await loadCatalog();data.subjects=fresh.subjects;data.teaching=fresh.teaching;data.teachers=fresh.teachers;
            const selectedGroup=groupSelect.value||groups()[0]||'WPF';
            const subjects=data.subjects.filter(s=>(s.mode==='INDIVIDUAL'||s.wpf)&&(s.assignmentGroup||'WPF')===selectedGroup);
            const pupils=await call('/curriculum-wpf-roster',{classId,semesterId,assignmentGroup:selectedGroup},'curriculum_manage_enrollment');if(version!==rosterVersion)return;
            const controls=el('div');controls.className='curriculum-table-filters';const search=el('input');search.placeholder='Schüler filtern';const subjectFilter=el('select');[{id:'',name:'Alle Fächer'},...subjects].forEach(s=>{const o=el('option',s.name);o.value=s.id;subjectFilter.append(o)});controls.append(search,subjectFilter);board.append(controls);
            const table=document.createElement("table");table.className="curriculum-wpf-table";const head=table.insertRow();["Schüler","Individuelles Fach"].forEach((t,i)=>{const th=document.createElement("th");th.textContent=t;th.className="sortable";head.append(th)});const rows=[];
            for(const student of pupils){const tr=table.insertRow();tr.insertCell().textContent=student.first_name+" "+student.last_name;const wpf=choice(tr.insertCell(),selectedGroup,[{id:0,name:"Noch nicht zugeordnet"},...subjects]);wpf.value=String(student.subjectId||0);rows.push({student,tr,wpf});}
            const filter=()=>rows.forEach(r=>{const text=(r.student.first_name+' '+r.student.last_name).toLowerCase();r.tr.hidden=!!(search.value&& !text.includes(search.value.toLowerCase()) || subjectFilter.value && r.wpf.value!==subjectFilter.value);});search.addEventListener('input',filter);subjectFilter.addEventListener('change',filter);
            board.append(table);if(!pupils.length)board.append(el("p","In dieser Klasse sind noch keine Kinder eingetragen."));
            action(board,"Zuordnungen speichern",async()=>{for(const row of rows){const subjectId=Number(row.wpf.value);if(!subjectId)continue;await call("/assign-curriculum-wpf",{studentId:row.student.id,subjectId,classId,semesterId,assignmentGroup:selectedGroup,expectedSubjectId:row.student.subjectId??null},"curriculum_manage_enrollment");}status.textContent="Individuelle Fach-Zuordnung gespeichert.";});
        };
        action(wpfPanel,'Zuordnungen laden',loadRoster);
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
        const teacherMode = catalog.admin !== true;
        const activeContexts = teacherMode ? (catalog.contexts || []).filter(context => context.activeSemester === true) : [];
        if (teacherMode) renderTeacherOverview(activeContexts);
        await mountStudentProgress(catalog, activeContexts);
        if (teacherMode && !activeContexts.length) {
            message.textContent = 'Für das aktive Halbjahr sind keine Lerngruppen oder Fächer zugewiesen.';
            return;
        }
        if (teacherMode) {
            selectMount = el('div');
            selectMount.className = 'teacher-filter-bar';
            root.append(selectMount);
        }
        const teacherClasses = teacherMode ? uniqueBy(activeContexts, context => context.classId).map(context => ({id:context.classId,label:context.classLabel,grade:context.grade})) : [];
        const subject = teacherMode ? select('Fach', [], 'subjectId') : select('Fach', catalog.subjects, 'subjectId');
        const semester = teacherMode ? null : select('Halbjahr', catalog.semesters, 'semesterId');
        const schoolClass = select('Klasse/Lerngruppe', teacherMode ? teacherClasses : catalog.classes, 'classId');
        const teacher = catalog.admin ? select('Lehrkraft (flexible Etappen)', catalog.teachers.map(t => ({id:t.id,name:t.first_name+' '+t.last_name})), 'teacherId') : null;
        const grade = catalog.admin ? select('Jahrgang (zentral)', Array.from({length:13},(_,i)=>({id:i+1,name:String(i+1)})), 'grade') : null;
        if (catalog.admin && separatedAdmin) [schoolClass, teacher].filter(Boolean).forEach(node => node.closest('label').remove());
        if (grade) grade.value = String(catalog.classes[0]?.grade || 5);
        function contextsForClass() {
            return activeContexts.filter(context => context.classId === Number(schoolClass.value));
        }
        function rebuildTeacherSubjects() {
            if (!teacherMode) return;
            const subjects = uniqueBy(contextsForClass(), context => context.subjectId).map(context => ({id:context.subjectId,name:context.subjectName}));
            subject.replaceChildren();
            for (const item of subjects) { const option = el('option', item.name); option.value = item.id; subject.append(option); }
        }
        function selectedTeacherContext() {
            if (!teacherMode) return null;
            return activeContexts.find(context => context.classId === Number(schoolClass.value) && context.subjectId === Number(subject.value)) || null;
        }
        rebuildTeacherSubjects();
        const load = el('button', 'Anzeigen / Aktualisieren'); load.type = 'button'; selectMount.append(load);
        const summary = el('p'), central = el('section'), flexible = el('section'), assignments = el('section'); root.append(summary, central, flexible, assignments);
        if (teacherMode) central.className = 'teacher-curriculum-content';
        const genericControls=[subject,semester,schoolClass,teacher,grade,load,summary,central,flexible,assignments].filter(Boolean);
        if(catalog.admin && !separatedAdmin) genericControls.forEach(node=>node.hidden=true);
        const previousReveal=revealSemesterSetup; revealSemesterSetup=()=>{previousReveal();genericControls.forEach(node=>node.hidden=false);};
        let version = 0;
        const openTopicKeys = new Set();
        let hasRememberedOpenTopics = false;
        function rememberOpenTopics() {
            const sections = central.querySelectorAll('details[data-curriculum-topic-key]');
            if (sections.length) hasRememberedOpenTopics = true;
            sections.forEach(section => {
                if (section.open) openTopicKeys.add(section.dataset.curriculumTopicKey);
                else openTopicKeys.delete(section.dataset.curriculumTopicKey);
            });
        }
        function topicKey(type, id) { return `${type}:${id}`; }
        async function refresh() {
            const current = ++version;
            rememberOpenTopics();
            message.textContent = '';
            central.replaceChildren(); flexible.replaceChildren(); assignments.replaceChildren(); summary.textContent = '';
            if (!await allowed('curriculum_view')) throw Error(permissionDenied);
            const manageAssignments = catalog.admin === true && await allowed('curriculum_assign_context');
            const manageFlexible = await allowed('curriculum_manage_flexible');
            const manageCentral = catalog.admin === true && await allowed('curriculum_manage_central');
            const context = selectedTeacherContext();
            if (teacherMode && !context) { message.textContent='Für diese Auswahl ist kein Unterrichtskontext zugewiesen.'; return; }
            const scope = teacherMode
                ? {subjectId:context.subjectId,semesterId:context.semesterId,classId:context.classId,teacherId:catalog.teacherId}
                : {subjectId:Number(subject.value),semesterId:Number(semester.value),classId:Number(schoolClass.value),teacherId:teacher?Number(teacher.value):catalog.teacherId};
            const selectedClass = teacherMode ? null : catalog.classes.find(c => c.id === scope.classId);
            const g = teacherMode ? context.grade : grade ? Number(grade.value) : selectedClass?.grade;
            if (!scope.subjectId || !scope.semesterId || !g) { message.textContent='Noch keine passenden Fächer, Klassen oder Halbjahre vorhanden.'; return; }
            const structure = await post('/curriculum-structure', {...scope,grade:g});
            if (current !== version) return;
            async function saveRelease(values) {
                if (current !== version) throw Error('Die Auswahl wurde geändert. Bitte das aktuelle Formular verwenden.');
                if (!teacherMode || !await allowed('curriculum_publish')) throw Error(permissionDenied);
                await post('/set-curriculum-release',{...scope,...values}); await refresh();
            }
            function releaseTask(releases, task) {
                return (releases.tasks || []).find(item => item.id === task.id || item.taskId === task.id);
            }
            function releaseTopic(releases, topic) {
                return (releases.topics || []).find(item => item.id === topic.id || item.topicId === topic.id);
            }
            function flexibleTaskRelease(releases, task) {
                return (releases.flexibleTasks || []).find(item => item.id === task.id || item.flexibleTaskId === task.id);
            }
            function flexibleTopicRelease(releases, topic) {
                return (releases.flexibleTopics || []).find(item => item.id === topic.id || item.flexibleTopicId === topic.id);
            }
            function releaseStatus(topicRelease, tasks, taskRelease) {
                if (!tasks.length) return topicRelease?.active ? 'freigegeben' : 'gesperrt';
                const active = tasks.filter(task => taskRelease(task)?.active === true).length;
                if (active === tasks.length) return 'freigegeben';
                if (active === 0) return 'gesperrt';
                return 'teilweise freigegeben';
            }
            function topicStatus(topic, tasks, releases) {
                return releaseStatus(releaseTopic(releases, topic), tasks, task => releaseTask(releases, task));
            }
            function flexibleTopicStatus(topic, tasks, releases) {
                return releaseStatus(flexibleTopicRelease(releases, topic), tasks, task => flexibleTaskRelease(releases, task));
            }
            function appendReleaseButton(parent, label, values) {
                const b=el('button',label);b.type='button';parent.append(b);b.addEventListener('click',()=>saveRelease(values).catch(showError));return b;
            }
            central.replaceChildren(el('h3','Zentrale Themen und Etappen'));
            summary.textContent = `Zentrale Summe Jahrgang ${g}: ${structure.centralTokens} / 100 Münzen (absolute Grenze 105).`;
            summary.style.color = structure.centralTokens > 100 ? 'darkred' : '';
            async function save(path, data) {
                const scrollTop = window.scrollY;
                const isAssignment = path === '/assign-curriculum-context' || path === '/transfer-curriculum-context';
                const isFlexible = ['/add-flexible-task','/edit-flexible-task','/add-flexible-topic','/rename-flexible-topic'].includes(path);
                if (current !== version) throw Error('Die Auswahl wurde geändert. Bitte das aktuelle Formular verwenden.');
                if ((!isFlexible && catalog.admin !== true) || !await allowed('curriculum_view') ||
                    !await allowed(isAssignment ? 'curriculum_assign_context' : isFlexible ? 'curriculum_manage_flexible' : 'curriculum_manage_central')) {
                    await refresh(); throw Error(permissionDenied);
                }
                const result = await post(path, data);
                if (path === '/add-curriculum-topic' && result?.id != null) openTopicKeys.add(topicKey('central', result.id));
                if (path === '/add-curriculum-task' && data.topicId != null) openTopicKeys.add(topicKey('central', data.topicId));
                await refresh();
                if (window.scrollY !== scrollTop && typeof window.scrollTo === 'function') window.scrollTo(0, scrollTop);
            }
            function editTask(parent, task, isFlexible) {
                const form = el('form'), name = field(form,'Name',task.name), tokens = field(form,'Münzen',task.tokens,'number');
                const topic = isFlexible ? topicField(form, task.topicId) : null;
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
            if (teacherMode) {
                central.replaceChildren(el('h3','Themen & Etappen')); flexible.replaceChildren();
                if(!scope.classId || !scope.teacherId){central.append(el('p','Für diese Auswahl ist kein Unterrichtskontext zugewiesen.'));return;}
                budget=await post('/curriculum-budget',scope);
                const flexibleStructure=await post('/flexible-curriculum-structure',scope);if(current!==version)return;
                const canPublish=await allowed('curriculum_publish');
                const releases=canPublish ? await post('/curriculum-releases',scope) : {topics:[],tasks:[]};if(current!==version)return;
                const tasks=flexibleStructure.tasks;flexibleTopics=flexibleStructure.topics;
                summary.textContent=`Geplant: zentral ${budget.centralTokens} + flexibel ${budget.flexibleTokens} = ${budget.totalTokens} Münzen · regulärer Rahmen 100 · absolute Grenze 105`;
                summary.style.color=budget.totalTokens>100?'darkred':'';
                const manageFlexible=await allowed('curriculum_manage_flexible');
                for (const topic of structure.topics) {
                    const topicTasks=structure.tasks.filter(t=>t.topic===topic.id);
                    const section=el('details');section.open=!hasRememberedOpenTopics || openTopicKeys.has(topicKey('central',topic.id));section.dataset.curriculumTopicKey=topicKey('central',topic.id);section.className='teacher-topic-panel teacher-topic-central';
                    section.append(el('summary',`Thema ${topic.number} - ${topic.name} · Zentral · ${topicStatus(topic, topicTasks, releases)}`));
                    central.append(section);
                    if(canPublish) {
                        appendReleaseButton(section,'Alle freigeben',{topicId:topic.id,active:true});
                        appendReleaseButton(section,'Alle sperren',{topicId:topic.id,active:false});
                    }
                    for(const task of topicTasks) {
                        const active=releaseTask(releases,task)?.active === true;
                        const row=el('p',`Etappe ${task.stageNumber ?? ''} - ${task.name}: ${task.tokens} Münzen · ${active?'freigegeben':'gesperrt'} `);row.className='teacher-stage-row';
                        section.append(row);
                        if(canPublish) appendReleaseButton(row,active?'Sperren':'Freigeben',{taskId:task.id,active:!active});
                    }
                }
                for(const topic of flexibleTopics) {
                    const topicTasks=tasks.filter(task=>(task.topicId??null)===topic.id);
                    const group=el('details');group.open=!hasRememberedOpenTopics || openTopicKeys.has(topicKey('flexible',topic.id));group.dataset.curriculumTopicKey=topicKey('flexible',topic.id);group.className='teacher-topic-panel teacher-topic-flexible';group.append(el('summary',`${topic.name} · Flexibel · ${flexibleTopicStatus(topic, topicTasks, releases)}`));central.append(group);
                    if(canPublish) {
                        appendReleaseButton(group,'Alle freigeben',{flexibleTopicId:topic.id,active:true});
                        appendReleaseButton(group,'Alle sperren',{flexibleTopicId:topic.id,active:false});
                    }
                    if(manageFlexible) {
                        const form=el('form'),name=field(form,'Themenname',topic.name);button(form,'Flexibles Thema umbenennen');group.append(form);
                        form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/rename-flexible-topic',{topicId:topic.id,name:name.value});}catch(error){showError(error);}});
                    }
                    for(const task of topicTasks) {
                        const active=flexibleTaskRelease(releases,task)?.active === true;
                        const row=el('p',`${task.name}: ${task.tokens} Münzen · ${active?'freigegeben':'gesperrt'} `);row.className='teacher-stage-row';
                        group.append(row);
                        if(canPublish) appendReleaseButton(row,active?'Sperren':'Freigeben',{flexibleTaskId:task.id,active:!active});
                        if(manageFlexible) editTask(group,task,true);
                    }
                }
                const unassigned=tasks.filter(task=>(task.topicId??null)===null);
                if(unassigned.length) {
                    const group=el('details');group.open=!hasRememberedOpenTopics || openTopicKeys.has(topicKey('flexible', 'unassigned'));group.dataset.curriculumTopicKey=topicKey('flexible', 'unassigned');group.className='teacher-topic-panel teacher-topic-flexible';group.append(el('summary','Ohne Thema · Flexibel'));central.append(group);
                    for(const task of unassigned) {
                        const active=flexibleTaskRelease(releases,task)?.active === true;
                        const row=el('p',`${task.name}: ${task.tokens} Münzen · ${active?'freigegeben':'gesperrt'} `);row.className='teacher-stage-row';
                        group.append(row);
                        if(canPublish) appendReleaseButton(row,active?'Sperren':'Freigeben',{flexibleTaskId:task.id,active:!active});
                        if(manageFlexible) editTask(group,task,true);
                    }
                }
                if (!manageFlexible) { central.append(el('p','Flexible Inhalte sind nur lesbar.')); return; }
                central.append(el('p','Flexible Münzen sind geplant. Verdient werden sie erst durch bestätigte Abschlüsse.'));
                const topicForm=el('form'),topicName=field(topicForm,'Neues flexibles Thema','');button(topicForm,'Flexibles Thema anlegen');central.append(topicForm);
                topicForm.addEventListener('submit',async e=>{e.preventDefault();try{await save('/add-flexible-topic',{...scope,name:topicName.value});}catch(error){showError(error);}});
                const form=el('form'), name=field(form,'Neue flexible Etappe',''), tokens=field(form,'Münzen',0,'number'), topic=topicField(form,null);button(form,'Flexible Etappe anlegen');central.append(form);
                form.addEventListener('submit',async e=>{e.preventDefault();try{
                    if(budget.totalTokens+Number(tokens.value)>105)throw Error('Dieses Unterrichtsbudget würde 105 überschreiten.');
                    await save('/add-flexible-task',{...scope,name:name.value,tokens:Number(tokens.value),topicId:topic.value?Number(topic.value):null});
                }catch(error){showError(error);}});
                return;
            }
            for (const topic of structure.topics) {
                const topicTasks=structure.tasks.filter(t=>t.topic===topic.id);
                const topicTokens=topicTasks.reduce((sum,task)=>sum+Number(task.tokens||0),0);
                const section=el('details');section.open=openTopicKeys.has(topicKey('central',topic.id));section.dataset.curriculumTopicKey=topicKey('central',topic.id);section.className='curriculum-topic-card';
                const title=el('summary',`Thema ${topic.number} – ${topic.name} · ${topicTasks.length} ${topicTasks.length===1?'Etappe':'Etappen'} · ${topicTokens} Münzen`);title.className='curriculum-topic-summary';section.append(title);central.append(section);
                if(manageCentral) {
                    const form=el('form'), name=field(form,'Themenname',topic.name);form.className='curriculum-topic-edit';button(form,'Thema umbenennen');section.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/rename-topic',{topicId:topic.id,name:name.value});}catch(error){showError(error);}});
                }
                for(const task of topicTasks) {
                    if(manageCentral) {
                        const stageCard=el('article');stageCard.className='curriculum-stage-card';
                        stageCard.append(el('h4',`Etappe ${task.stageNumber ?? ''} · ${task.name} · ${task.tokens} Münzen`));
                        editTask(stageCard,task,false);section.append(stageCard);
                    } else section.append(el('p',`Etappe ${task.stageNumber ?? ''} - ${task.name}: ${task.tokens} Münzen`));
                }
                if(manageCentral) {
                    const addStage=el('div');addStage.className='curriculum-add-stage';addStage.append(el('h4','Neue Etappe hinzufügen'));
                    const form=el('form'), stage=field(form,'Etappennummer',Math.max(0,...topicTasks.map(t=>t.stageNumber||0))+1,'number'), name=field(form,'Neue Etappe',''), tokens=field(form,'Münzen',0,'number');stage.min=1;stage.removeAttribute('max');button(form,'Etappe anlegen');addStage.append(form);section.append(addStage);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{
                        if(structure.centralTokens+Number(tokens.value)>105) throw Error('Zentrale Summe über 105.');
                        await save('/add-curriculum-task',{topicId:topic.id,stageNumber:Number(stage.value),name:name.value,tokens:Number(tokens.value)});
                    }catch(error){showError(error);}});
                }
            }
            if(manageCentral) {
                const addTopic=el('div');addTopic.className='curriculum-add-topic';addTopic.append(el('h3','Neues Thema hinzufügen'));
                const form=el('form'), name=field(form,'Themenname',''), number=field(form,'Nummer',Math.max(0,...structure.topics.map(t=>t.number))+1,'number');number.min=1;number.removeAttribute('max');button(form,'Thema anlegen');addTopic.append(form);central.append(addTopic);
                form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/add-curriculum-topic',{...scope,grade:g,number:Number(number.value),name:name.value});}catch(error){showError(error);}});
            }
            if (catalog.admin && separatedAdmin) return;
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
        async function mountCentralImport() {
            if(!catalog.admin || !await allowed('curriculum_manage_central'))return;
            const panel=el('section');panel.className='central-curriculum-import';root.append(panel);
            panel.append(el('h3','Zentrales Curriculum importieren'));
            const controls=el('div');panel.append(controls);
            const importSemester=el('select'), importGrade=el('select'), file=el('input');file.type='file';file.accept='.csv,text/csv';
            for(const item of catalog.semesters){const option=el('option',item.label||item.name);option.value=item.id;importSemester.append(option);}
            for(let i=1;i<=13;i++){const option=el('option',String(i));option.value=String(i);importGrade.append(option);}
            controls.append(el('label','Halbjahr '),importSemester,el('label',' Jahrgang '),importGrade,el('label',' CSV-Datei '),file);
            const previewButton=el('button','Vorschau prüfen');previewButton.type='button';const importButton=el('button','Import bestätigen');importButton.type='button';importButton.disabled=true;controls.append(previewButton,importButton);
            panel.append(el('p','Format: Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen'));
            const filterBox=el('div'), subjectFilter=el('select'), textFilter=el('input');textFilter.placeholder='Themen-/Etappenname filtern';filterBox.append(subjectFilter,textFilter);panel.append(filterBox);
            const sums=el('div'), previewArea=el('div'), overviewArea=el('div');panel.append(sums,previewArea,overviewArea);
            let currentCsv='',currentPreview=null;
            const selected=()=>({grade:Number(importGrade.value),semesterId:Number(importSemester.value)});
            async function readCsv(){const selectedFile=file.files&&file.files[0];if(!selectedFile)throw Error('Bitte eine CSV-Datei auswählen.');if(typeof selectedFile.text==='function')return selectedFile.text();return await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result||''));reader.onerror=()=>reject(Error('CSV-Datei konnte nicht gelesen werden.'));reader.readAsText(selectedFile,'UTF-8');});}
            function renderSubjects(subjects=[]){sums.replaceChildren();subjectFilter.replaceChildren(el('option','Alle Fächer'));subjectFilter.firstChild.value='';for(const subject of subjects){const option=el('option',subject.subjectName);option.value=String(subject.subjectId);subjectFilter.append(option);const line=el('p',`${subject.subjectName}: ${subject.centralTokens} / 100 zentrale Münzen. Rest regulär: ${subject.remainingRegular}; Rest bis absolute Grenze: ${subject.remainingHard}.`);if(subject.warning)line.style.color='darkred';sums.append(line);}}
            function table(headers, rows, area) {
                const table=document.createElement('table'), head=table.insertRow();headers.forEach(h=>head.append(el('th',h)));
                for(const row of rows){const tr=table.insertRow();for(const value of row){tr.insertCell().textContent=value == null ? '' : String(value);}}
                area.append(table);
            }
            function renderPreview(preview){previewArea.replaceChildren();renderSubjects(preview.subjects||[]);importButton.disabled=preview.canImport!==true;const errors=preview.errors||[];for(const error of errors){const p=el('p',error);p.style.color='darkred';previewArea.append(p);}for(const warning of preview.warnings||[]){const p=el('p',`${warning.subjectName}: ${warning.centralTokens} Münzen überschreiten den regulären Rahmen.`);p.style.color='darkred';previewArea.append(p);}table(['Zeile','Fach','Thema Nr.','Thema','Etappe Nr.','Etappe','Münzen','Aktion','Hinweis'],(preview.rows||[]).map(r=>[r.sourceLine,r.subjectName,r.topicNumber,r.topicName,r.stageNumber,r.stageName,r.tokens,r.action,r.message]),previewArea);}
            function renderOverview(overview){overviewArea.replaceChildren();renderSubjects(overview.subjects||[]);let rows=overview.rows||[];const apply=()=>{const subject=subjectFilter.value,text=textFilter.value.toLowerCase();const filtered=rows.filter(r=>(!subject||String(r.subjectId)===subject)&&(!text||String(r.topicName).toLowerCase().includes(text)||String(r.stageName).toLowerCase().includes(text)));overviewArea.replaceChildren();table(['Fach','Thema Nr.','Thema','Etappe Nr.','Etappe','Münzen'],filtered.map(r=>[r.subjectName,r.topicNumber,r.topicName,r.stageNumber,r.stageName,r.tokens]),overviewArea);};subjectFilter.onchange=apply;textFilter.oninput=apply;apply();}
            async function reloadOverview(){const overview=await post('/central-curriculum-overview',selected());renderOverview(overview);}
            previewButton.addEventListener('click',async()=>{try{currentCsv=await readCsv();currentPreview=await post('/preview-central-curriculum-import',{...selected(),csv:currentCsv});renderPreview(currentPreview);}catch(error){showError(error);}});
            importButton.addEventListener('click',async()=>{try{if(!currentPreview?.canImport)return;if(!confirm('Die angezeigten Themen und Etappen jetzt übernehmen?'))return;await post('/import-central-curriculum',{...selected(),csv:currentCsv});message.textContent='Import erfolgreich.';message.style.color='';importButton.disabled=true;await reloadOverview();}catch(error){showError(error);}});
            importSemester.addEventListener('change',()=>reloadOverview().catch(showError));importGrade.addEventListener('change',()=>reloadOverview().catch(showError));await reloadOverview().catch(showError);
        }
        schoolClass.addEventListener('change',()=>{rebuildTeacherSubjects();refresh().catch(showError);});
        for(const node of [subject,semester,teacher,grade].filter(Boolean))node.addEventListener('change',()=>refresh().catch(showError));
        load.addEventListener('click',()=>refresh().catch(showError));await refresh().catch(showError);
        await mountCentralImport();
        if(catalog.enrollmentEnabled)await mountEnrollment(catalog,()=>{
            const context = selectedTeacherContext();
            return teacherMode && context ? {subjectId:context.subjectId,semesterId:context.semesterId,classId:context.classId,teacherId:catalog.teacherId}
                : {subjectId:Number(subject.value),semesterId:Number(semester.value),classId:Number(schoolClass.value),teacherId:teacher?Number(teacher.value):catalog.teacherId};
        },adminEnrollmentRoot||root);
    } catch(error) { showError(error); }
});
