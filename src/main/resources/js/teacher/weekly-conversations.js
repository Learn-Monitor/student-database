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
        const top50Button = el('button', 'Top 50 anzeigen'); top50Button.type = 'button'; top50Button.className = 'teacher-top50-button'; controls.append(top50Button);
        const status = el('p'); status.setAttribute('role','status'); const output = el('div');
        panel.append(controls, status, output); mount.append(panel);
        const markPrintState = () => panel.classList.add('teacher-weekly-printing');
        const clearPrintState = () => panel.classList.remove('teacher-weekly-printing');
        window.addEventListener('beforeprint', markPrintState);
        window.addEventListener('afterprint', clearPrintState);
        const top50Dialog = document.createElement('dialog'); top50Dialog.className = 'teacher-top50-dialog';
        const top50Title = el('h3', 'Top 50 anzeigen'); top50Title.id = 'teacher-top50-title';
        const top50Status = el('p'); top50Status.setAttribute('role','status');
        const top50Close = el('button', 'Schließen'); top50Close.type = 'button'; top50Close.addEventListener('click', () => top50Dialog.close());
        const top50Body = el('div'); top50Body.className = 'teacher-top50-scroll';
        top50Dialog.append(top50Title, top50Status, top50Body, top50Close); mount.append(top50Dialog);
        const renderTop50 = data => {
            top50Body.replaceChildren();
            const table = document.createElement('table'); table.className = 'teacher-top50-table';
            const head = table.insertRow(); ['Platz','Schüler:in','Klasse','Münzen'].forEach(label => head.append(el('th',label)));
            for (const entry of (data.entries || [])) {
                const row = table.insertRow(); row.append(el('td', String(entry.rank)), el('td', `${entry.firstName || ''} ${entry.lastName || ''}`.trim()), el('td', entry.classLabel || '—'), el('td', `${entry.totalCoins ?? 0} 🪙`));
            }
            if (!(data.entries || []).length) top50Body.append(el('p','Für diesen Jahrgang gibt es noch keine Teilnehmer:innen.'));
            else top50Body.append(table);
            top50Body.append(el('p', data.tieRule || 'Gleiche Münzzahl erhält denselben Rang.'));
        };
        top50Button.addEventListener('click', async () => {
            const selected = tutorClasses.find(item => `${item.semesterId}:${item.classId}` === select.value);
            top50Dialog.showModal(); top50Status.textContent = 'Rangliste wird aktualisiert …'; top50Body.replaceChildren(); top50Button.disabled = true;
            try { renderTop50(await post('/ranking-top50', {grade:Number(selected?.grade)})); top50Status.textContent = ''; }
            catch (error) { top50Status.textContent = error.message; }
            finally { top50Button.disabled = false; }
        });
        const render = data => {
            output.replaceChildren();
            const heading = el('h4', `${data.classLabel || 'Klasse'} · ${data.semesterLabel || 'Halbjahr'}`); output.append(heading, progressLegend());
            const wrap = el('div'); wrap.className = 'teacher-table-scroll teacher-weekly-scroll'; const table = document.createElement('table'); table.className='teacher-weekly-table';
            const subjects = data.subjects || [];
            const thead = document.createElement('thead');
            const head = thead.insertRow(); head.className = 'teacher-weekly-header';
            head.append(el('th','Schüler'));
            for (const subject of subjects) {
                const th = el('th', subject.name); th.scope = 'col'; head.append(th);
            }
            table.append(thead);
            const body = document.createElement('tbody');
            for (const student of (data.students || [])) {
                const row = body.insertRow(); const nameCell=el('td'); nameCell.append(el('div',student.name || 'Schüler'));
                const graduation=el('div',`Graduierung: ${student.graduationLabel || '—'}`); graduation.className='teacher-graduation'; nameCell.append(graduation);
                const select=document.createElement('select'); for(const [value,label] of [[0,'Neustarter'],[1,'Starter'],[2,'Durchstarter']]) { const option=el('option',label); option.value=value; option.selected=Number(student.graduationLevel)===value; select.append(option); }
                const edit=el('button','ändern'); edit.type='button'; const save=el('button','Speichern'); save.type='button'; save.hidden=true; select.hidden=true;
                if (Number(student.graduationLevel)>2) edit.disabled=true;
                edit.addEventListener('click',()=>{select.hidden=false;save.hidden=false;edit.hidden=true;});
                save.addEventListener('click',async()=>{save.disabled=true; try { const result=await post('/change-tutor-graduation',{studentId:student.id,semesterId:data.semesterId,graduationLevel:Number(select.value)}); student.graduationLevel=result.graduationLevel; student.graduationLabel=result.graduationLabel; student.graduationHistory=result.history||student.graduationHistory; graduation.textContent=`Graduierung: ${result.graduationLabel}`; select.hidden=true;save.hidden=true;edit.hidden=false; status.textContent=result.changed?'Graduierung gespeichert.':'Keine Änderung gespeichert.'; } catch(error){status.textContent=error.message;} finally{save.disabled=false;} });
                const history=el('button','Verlauf anzeigen'); history.type='button'; const historyOutput=el('div'); historyOutput.hidden=true; history.addEventListener('click',()=>{historyOutput.hidden=!historyOutput.hidden; historyOutput.replaceChildren(...(student.graduationHistory||[]).map(item=>el('small',`${item.changedAt}: ${item.oldLabel} → ${item.newLabel} (${item.teacherName})`)));});
                nameCell.append(select,edit,save,history,historyOutput); row.append(nameCell);
                const cells = new Map((student.subjects || []).map(cell => [cell.subjectId, cell]));
                for (const subject of subjects) {
                    const cell = cells.get(subject.id) || {totalTokens:0,note:6,stages:[]};
                    const td = row.insertCell();
                    if (cell.assigned === false || cell.totalTokens == null) td.append(el('div','—'));
                    else {
                        td.append(progressSummary(cell.progress, cell.totalTokens));
                        const gn = cell.lastGnDate ? formatGnDate(cell.lastGnDate) : 'noch kein GN';
                        td.append(el('small', gn === 'noch kein GN' ? gn : `letzter GN: ${gn}`));
                    }
                }
            }
            table.append(body); wrap.append(table); output.append(wrap);
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

// Same wording as the subject teacher's roster ("X von Y Münzen").
function progressSummary(progress, totalTokens) {
    const wrap = document.createElement('div'); wrap.className = 'teacher-roster-progress';
    const count = value => Number.isSafeInteger(value) ? value : 0;
    if (!progress) { wrap.textContent = `${count(totalTokens)} Münzen`; return wrap; }
    if (count(progress.releasedTokens) === 0) { wrap.textContent = 'Noch keine Etappe freigegeben'; return wrap; }
    const earned = count(progress.earnedTokens), released = count(progress.releasedTokens);
    const tokens = document.createElement('span'); tokens.className = 'teacher-roster-progress-tokens'; tokens.textContent = `${earned} von ${released} Münzen`;
    // Segmented bar: earned coins, then stages with open failed GN attempts (orange 1×, red 2×/gesperrt).
    const bar = document.createElement('div'); bar.className = 'teacher-progress-bar'; bar.setAttribute('aria-hidden', 'true');
    const segment = (className, value) => { if (value <= 0) return; const part = document.createElement('span'); part.className = className; part.style.flexBasis = `${Math.min(100, value / released * 100)}%`; bar.append(part); };
    segment('teacher-progress-earned', earned);
    segment('teacher-progress-failed-once', count(progress.failedOnceTokens));
    segment('teacher-progress-failed-twice', count(progress.failedTwiceOrLockedTokens));
    wrap.append(tokens, bar);
    const failures = [];
    if (count(progress.failedOnceStages) > 0) failures.push(`${stagesLabel(progress.failedOnceStages)} 1× nicht bestanden`);
    if (count(progress.failedTwiceOrLockedStages) > 0) failures.push(`${stagesLabel(progress.failedTwiceOrLockedStages)} 2× nicht bestanden oder gesperrt`);
    if (failures.length) { wrap.title = failures.join(', '); const hint = document.createElement('span'); hint.className = 'teacher-visually-hidden'; hint.textContent = failures.join(', '); wrap.append(hint); }
    return wrap;
}

function stagesLabel(count) { return count === 1 ? '1 Etappe' : `${count} Etappen`; }

function progressLegend() {
    const legend = document.createElement('p'); legend.className = 'teacher-progress-legend';
    for (const [className, text] of [['teacher-progress-earned','Münzen erreicht'],['teacher-progress-failed-once','GN 1× nicht bestanden'],['teacher-progress-failed-twice','GN 2× nicht bestanden / gesperrt']]) {
        const item = document.createElement('span'); const swatch = document.createElement('span'); swatch.className = className; item.append(swatch, ` ${text}`); legend.append(item);
    }
    return legend;
}

function formatGnDate(value) {
    const text = String(value || '').slice(0, 10);
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
    return match ? `${match[3]}.${match[2]}.${match[1]}` : text || 'unbekannt';
}
