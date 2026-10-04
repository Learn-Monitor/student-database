document.addEventListener('DOMContentLoaded', () => {
    teacherDashboardLoadEvent();
    const mount = document.getElementById('teacher-schoolwide-stage-overview');
    if (!mount) return;
    const columns = [['firstName','Vorname'],['lastName','Nachname'],['className','Klasse'],['stage','Etappe'],['entry','Eintrag']];
    let rows = [], sortKey = 'lastName', ascending = true; const filterValues = new Map();
    const value = raw => String(raw ?? '');
    const render = () => {
        mount.replaceChildren(); const controls = document.createElement('div'); controls.className = 'teacher-stage-overview-filters'; const filters = new Map();
        for (const [key,label] of columns) { const field=document.createElement('label'); field.textContent=`${label} filtern`; const input=document.createElement('input'); input.type='search'; input.placeholder=label; input.value=filterValues.get(key)||''; input.setAttribute('aria-label',`${label} filtern`); input.addEventListener('input',()=>{filterValues.set(key,input.value);render();}); field.append(input);controls.append(field);filters.set(key,input); }
        mount.append(controls);
        const visible=rows.filter(row=>columns.every(([key])=>!filters.get(key).value.trim()||value(row[key]).toLocaleLowerCase().includes(filters.get(key).value.trim().toLocaleLowerCase()))).sort((a,b)=>value(a[sortKey]).localeCompare(value(b[sortKey]),'de',{numeric:true,sensitivity:'base'})*(ascending?1:-1));
        const table=document.createElement('table'); table.className='teacher-stage-overview-table'; const head=table.createTHead().insertRow();
        for(const [key,label] of columns){const cell=document.createElement('th');cell.scope='col';const button=document.createElement('button');button.type='button';button.textContent=label;button.addEventListener('click',()=>{if(sortKey===key)ascending=!ascending;else{sortKey=key;ascending=true;}render();});cell.append(button);head.append(cell);}
        const body=table.createTBody(); for(const row of visible){const tr=body.insertRow();for(const [key] of columns)tr.insertCell().textContent=value(row[key]);} mount.append(table);
        if(!visible.length) mount.append(Object.assign(document.createElement('p'),{textContent:'Keine passenden Schüler-Etappen-Einträge vorhanden.'}));
    };
    fetch('/curriculum-teacher-roster',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}).then(response=>response.ok?response.json():response.json().then(body=>Promise.reject(new Error(body.message||'Übersicht konnte nicht geladen werden.')))).then(data=>{rows=Array.isArray(data)?data:[];render();}).catch(error=>{mount.replaceChildren(Object.assign(document.createElement('p'),{textContent:error.message||'Übersicht konnte nicht geladen werden.'}));});
});
