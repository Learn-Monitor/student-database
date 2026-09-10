/* Standard curriculum editor: all mutations are validated again by the server. */
'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    const root = document.querySelector('#curriculum');
    if (!root) return;
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
                const isFlexible = path === '/add-flexible-task' || path === '/edit-flexible-task';
                if ((!isFlexible && catalog.admin !== true) || !await allowed('curriculum_view') ||
                    !await allowed(isAssignment ? 'curriculum_assign_context' : isFlexible ? 'curriculum_manage_flexible' : 'curriculum_manage_central')) {
                    await refresh(); throw Error(permissionDenied);
                }
                await post(path, data); await refresh();
            }
            function editTask(parent, task, isFlexible) {
                const form = el('form'), name = field(form,'Name',task.name), tokens = field(form,'Münzen',task.tokens,'number');
                button(form,'Speichern');
                form.addEventListener('submit', async event => {event.preventDefault();try {
                    const base = isFlexible ? budget.totalTokens : structure.centralTokens;
                    if (base-task.tokens+Number(tokens.value)>105) throw Error('Die Summe darf 105 Münzen nicht überschreiten.');
                    await save(isFlexible?'/edit-flexible-task':'/edit-task',{taskId:task.id,name:name.value,tokens:Number(tokens.value)});
                } catch(error){showError(error);} });parent.append(form);
            }
            let budget;
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
            const tasks=await post('/flexible-tasks',scope);if(current!==version)return;
            const status=el('p',`Kontext Jahrgang ${budget.grade}: zentral ${budget.centralTokens} + eigene ${budget.flexibleTokens} = ${budget.totalTokens}. Rest regulär: ${budget.remainingRegular}; Rest bis 105: ${budget.remainingHard}.`);
            status.style.color=budget.totalTokens>100?'darkred':'';flexible.append(status);
            if (!manageFlexible) flexible.append(el('p','Nur lesbar.'));
            for(const task of tasks) {
                if(manageFlexible) editTask(flexible,task,true);
                else flexible.append(el('p',task.name+': '+task.tokens+' Münzen'));
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
            const form=el('form'), name=field(form,'Neue flexible Etappe',''), tokens=field(form,'Münzen',0,'number');button(form,'Flexible Etappe anlegen');flexible.append(form);
            form.addEventListener('submit',async e=>{e.preventDefault();try{
                if(budget.totalTokens+Number(tokens.value)>105)throw Error('Dieses Unterrichtsbudget würde 105 überschreiten.');
                await save('/add-flexible-task',{...scope,name:name.value,tokens:Number(tokens.value)});
            }catch(error){showError(error);}});
        }
        for(const node of [subject,semester,schoolClass,teacher,grade].filter(Boolean))node.addEventListener('change',()=>refresh().catch(showError));
        load.addEventListener('click',()=>refresh().catch(showError));await refresh();
    } catch(error) { showError(error); }
});
