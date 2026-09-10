/* Standard curriculum editor: all mutations are validated again by the server. */
'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    const root = document.querySelector('#curriculum');
    if (!root) return;
    const el = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
    const message = el('p'); message.setAttribute('role', 'status'); root.append(message);
    async function post(path, data = {}) {
        const response = await fetch(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)});
        const body = await response.json();
        if (!response.ok) {
            const contexts = (body.affectedContexts || []).map(b => `Lehrkraft ${b.teacherId}, Klasse ${b.classId}, Halbjahr ${b.semesterId}: ${b.centralTokens} + ${b.flexibleTokens} = ${b.totalTokens}`).join('; ');
            throw Error((body.message || 'Anfrage fehlgeschlagen') + (contexts ? ' ' + contexts : ''));
        }
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
        const catalog = await post('/curriculum-catalog');
        const subject = select('Fach', catalog.subjects, 'subjectId');
        const semester = select('Halbjahr', catalog.semesters, 'semesterId');
        const schoolClass = select('Klasse/Lerngruppe', catalog.classes, 'classId');
        const teacher = catalog.admin ? select('Lehrkraft (flexible Etappen)', catalog.teachers.map(t => ({id:t.id,name:t.first_name+' '+t.last_name})), 'teacherId') : null;
        const grade = catalog.admin ? select('Jahrgang (zentral)', Array.from({length:13},(_,i)=>({id:i+1,name:String(i+1)})), 'grade') : null;
        if (grade) grade.value = String(catalog.classes[0]?.grade || 5);
        const load = el('button', 'Anzeigen / Aktualisieren'); load.type = 'button'; root.append(load);
        const summary = el('p'), central = el('section'), flexible = el('section'); root.append(summary, central, flexible);
        let version = 0;
        async function refresh() {
            const current = ++version;
            message.textContent = '';
            const scope = {subjectId:Number(subject.value),semesterId:Number(semester.value),classId:Number(schoolClass.value),teacherId:teacher?Number(teacher.value):catalog.teacherId};
            const selectedClass = catalog.classes.find(c => c.id === scope.classId);
            const g = grade ? Number(grade.value) : selectedClass?.grade;
            if (!scope.subjectId || !scope.semesterId || !g) { message.textContent='Noch keine passenden Fächer, Klassen oder Halbjahre vorhanden.'; return; }
            const structure = await post('/curriculum-structure', {...scope,grade:g});
            if (current !== version) return;
            central.replaceChildren(el('h3','Zentrale Themen und Etappen'));
            summary.textContent = `Zentrale Summe Jahrgang ${g}: ${structure.centralTokens} / 100 Münzen (absolute Grenze 105).`;
            summary.style.color = structure.centralTokens > 100 ? 'darkred' : '';
            async function save(path, data) { await post(path, data); await refresh(); }
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
                const section=el('details'), title=el('summary',topic.name+' — Etappen anzeigen / bearbeiten');section.append(title);central.append(section);
                if(catalog.admin) {
                    const form=el('form'), name=field(form,'Themenname',topic.name);button(form,'Thema umbenennen');section.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/rename-topic',{topicId:topic.id,name:name.value});}catch(error){showError(error);}});
                }
                for(const task of structure.tasks.filter(t=>t.topic===topic.id)) {
                    if(catalog.admin) editTask(section,task,false);else section.append(el('p',task.name+': '+task.tokens+' Münzen'));
                }
                if(catalog.admin) {
                    const form=el('form'), name=field(form,'Neue Etappe',''), tokens=field(form,'Münzen',0,'number'), level=field(form,'Niveau',1,'number');level.min=1;level.max=3;button(form,'Etappe anlegen');section.append(form);
                    form.addEventListener('submit',async e=>{e.preventDefault();try{
                        if(structure.centralTokens+Number(tokens.value)>105) throw Error('Zentrale Summe über 105.');
                        await save('/add-curriculum-task',{topicId:topic.id,name:name.value,tokens:Number(tokens.value),level:Number(level.value)});
                    }catch(error){showError(error);}});
                }
            }
            if(catalog.admin) {
                const form=el('form'), name=field(form,'Neues Thema',''), number=field(form,'Nummer',Math.max(0,...structure.topics.map(t=>t.number))+1,'number');number.min=1;number.removeAttribute('max');button(form,'Thema anlegen');central.append(form);
                form.addEventListener('submit',async e=>{e.preventDefault();try{await save('/add-curriculum-topic',{...scope,grade:g,number:Number(number.value),name:name.value});}catch(error){showError(error);}});
            }
            flexible.replaceChildren(el('h3','Flexible Lehrer-Etappen'));
            if(!scope.classId || !scope.teacherId){flexible.append(el('p','Für flexible Etappen Klasse und Lehrkraft auswählen.'));return;}
            budget=await post('/curriculum-budget',scope);
            const tasks=await post('/flexible-tasks',scope);if(current!==version)return;
            const status=el('p',`Kontext Jahrgang ${budget.grade}: zentral ${budget.centralTokens} + eigene ${budget.flexibleTokens} = ${budget.totalTokens}. Rest regulär: ${budget.remainingRegular}; Rest bis 105: ${budget.remainingHard}.`);
            status.style.color=budget.totalTokens>100?'darkred':'';flexible.append(status);
            for(const task of tasks)editTask(flexible,task,true);
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
