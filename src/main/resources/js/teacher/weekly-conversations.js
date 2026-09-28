'use strict';

document.addEventListener('DOMContentLoaded', async () => {
    const mount = document.querySelector('#weekly-conversations-mount');
    if (!mount) return;
    if (mount.dataset.weeklyLoaded === 'true') return;
    mount.dataset.weeklyLoaded = 'true';
    const el = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
    const post = async (path, body) => {
        const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
        let data = null; try { data = await response.json(); } catch {}
        if (!response.ok) { const error = Error(data?.message || (response.status === 403 ? 'Zugriff auf die Tutor-Funktion verweigert.' : 'Anfrage fehlgeschlagen.')); error.status = response.status; throw error; }
        return data;
    };
    try {
        const tutorClasses = await post('/my-tutor-classes', {});
        if (!Array.isArray(tutorClasses) || !tutorClasses.length) return;
        const panel = el('section'); panel.className = 'teacher-weekly-conversations';
        panel.append(el('h3', 'Übersicht für Wochengespräche'));
        const controls = el('div'); controls.className = 'teacher-filter-bar';
        const label = el('label', 'Tutor-Klasse '), select = el('select');
        for (const item of tutorClasses) { const option = el('option', `${item.label} (Jg. ${item.grade})`); option.value = `${item.semesterId}:${item.classId}`; select.append(option); }
        label.append(select); controls.append(label);
        const load = el('button', 'Übersicht laden'); load.type = 'button'; controls.append(load);
        const print = el('button', 'Drucken / als PDF speichern'); print.type = 'button'; print.addEventListener('click', () => window.print()); controls.append(print);
        const status = el('p'); status.setAttribute('role','status'); const output = el('div');
        panel.append(controls, status, output); mount.append(panel);
        const render = data => {
            output.replaceChildren();
            const heading = el('h4', `${data.classLabel || 'Klasse'} · ${data.semesterLabel || 'Halbjahr'}`); output.append(heading);
            const wrap = el('div'); wrap.className = 'teacher-table-scroll'; const table = document.createElement('table');
            const head = table.insertRow(); head.append(el('th','Schüler'));
            for (const subject of (data.subjects || [])) head.append(el('th', subject.name));
            for (const student of (data.students || [])) {
                const row = table.insertRow(); const nameCell=el('td'); nameCell.append(el('div',student.name || 'Schüler'));
                const graduation=el('div',`Graduierung: ${student.graduationLabel || '—'}`); graduation.className='teacher-graduation'; nameCell.append(graduation);
                const select=document.createElement('select'); for(const [value,label] of [[0,'Neustarter'],[1,'Starter'],[2,'Durchstarter']]) { const option=el('option',label); option.value=value; option.selected=Number(student.graduationLevel)===value; select.append(option); }
                const edit=el('button','ändern'); edit.type='button'; const save=el('button','Speichern'); save.type='button'; save.hidden=true; select.hidden=true;
                if (Number(student.graduationLevel)>2) edit.disabled=true;
                edit.addEventListener('click',()=>{select.hidden=false;save.hidden=false;edit.hidden=true;});
                save.addEventListener('click',async()=>{save.disabled=true; try { const result=await post('/change-tutor-graduation',{studentId:student.id,semesterId:data.semesterId,graduationLevel:Number(select.value)}); student.graduationLevel=result.graduationLevel; student.graduationLabel=result.graduationLabel; student.graduationHistory=result.history||student.graduationHistory; graduation.textContent=`Graduierung: ${result.graduationLabel}`; select.hidden=true;save.hidden=true;edit.hidden=false; status.textContent=result.changed?'Graduierung gespeichert.':'Keine Änderung gespeichert.'; } catch(error){status.textContent=error.message;} finally{save.disabled=false;} });
                const history=el('button','Verlauf anzeigen'); history.type='button'; const historyOutput=el('div'); historyOutput.hidden=true; history.addEventListener('click',()=>{historyOutput.hidden=!historyOutput.hidden; historyOutput.replaceChildren(...(student.graduationHistory||[]).map(item=>el('small',`${item.changedAt}: ${item.oldLabel} → ${item.newLabel} (${item.teacherName})`)));});
                nameCell.append(select,edit,save,history,historyOutput); row.append(nameCell);
                const cells = new Map((student.subjects || []).map(cell => [cell.subjectId, cell]));
                for (const subject of (data.subjects || [])) {
                    const cell = cells.get(subject.id) || {totalTokens:0,note:6,stages:[]};
                    const td = row.insertCell(); td.append(el('div', `${cell.totalTokens ?? 0} 🪙 · Note ${cell.note ?? 6}`));
                    const stages = (cell.stages || []).map(stage => stage.niveau ? `${stage.niveau} ✓` : `${stage.name || 'Etappe'} ✓`).join(' ');
                    if (stages) { const small = el('small', `Etappen: ${stages}`); td.append(small); }
                }
            }
            wrap.append(table); output.append(wrap);
        };
        const refresh = async () => {
            const [semesterId,classId] = select.value.split(':').map(Number); load.disabled = true; status.textContent = 'Wird geladen.';
            try { render(await post('/curriculum-weekly-conversations', {semesterId,classId})); status.textContent = ''; }
            catch (error) { output.replaceChildren(); status.textContent = error.message; }
            finally { load.disabled = false; }
        };
        load.addEventListener('click', refresh); await refresh();
    } catch (error) { if (error?.status === 403) mount.append(el('p', 'Die Tutor-Funktion ist für dieses Konto nicht freigeschaltet.')); else if (error?.status) mount.append(el('p', 'Tutor-Klassen konnten nicht geladen werden.')); }
});
