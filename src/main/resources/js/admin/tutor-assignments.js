(() => {
  const root=document.querySelector('#admin-tutors'); if(!root)return;
  const content=root.querySelector('[data-tutor-content]'),status=root.querySelector('[data-tutor-status]'),semester=document.querySelector('[data-global-semester]');
  const el=(tag,value)=>{const node=document.createElement(tag);if(value!==undefined)node.textContent=value;return node;};
  const post=(path,body)=>fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(async response=>{if(!response.ok)throw Error(path);return response.json();});
  const rows=value=>Array.isArray(value)?value:[], option=(label,value)=>{const node=el('option',label);node.value=value;return node;};
  let catalog,classes,teachers,tableBody;
  const tutorSelect=(selected)=>{const select=el('select');select.append(option('Keine Zuordnung',''));teachers.forEach(item=>select.append(option(`${item.first_name} ${item.last_name}`,String(item.id))));select.value=selected==null?'':String(selected);return select;};
  async function render(){
    tableBody.replaceChildren(); if(!semester.value){status.textContent='Bitte zuerst ein Halbjahr anlegen.';return;}
    const assignments=rows(await post('/curriculum-tutor-assignments',{semesterId:Number(semester.value)})),byClass=new Map();
    assignments.forEach(item=>{const key=Number(item.classId);if(!byClass.has(key))byClass.set(key,{});byClass.get(key)[item.tutorSlot]=item.teacherId;});
    let full=0,partial=0,none=0;
    const pending=[];
    classes.forEach(schoolClass=>{const current=byClass.get(Number(schoolClass.id))||{};if(current[1]!=null&&current[2]!=null)full++;else if(current[1]!=null||current[2]!=null)partial++;else none++;
      const row=el('tr');row.append(el('th',`${schoolClass.label} · Jahrgang ${schoolClass.grade}`));row.cells[0].scope='row';const one=tutorSelect(current[1]),two=tutorSelect(current[2]);
      [one,two].forEach((select,index)=>{select.setAttribute('aria-label',`${schoolClass.label}, Tutor:in ${index+1}`);const cell=el('td');cell.append(select);row.append(cell);});
      pending.push({schoolClass,one,two});tableBody.append(row);
    });
    const saveAll=el('button','Gesamte Jahrgangsfestlegung speichern');saveAll.type='button';saveAll.className='admin-section-save';content.prepend(saveAll);
    saveAll.addEventListener('click',async()=>{if(pending.some(item=>item.one.value&&item.one.value===item.two.value)){status.textContent='Eine Lehrkraft kann nicht beide Tutorplätze derselben Klasse belegen.';return;}saveAll.disabled=true;status.textContent='Tutorzuordnungen werden gespeichert …';try{for(const item of pending)await post('/assign-class-tutors',{semesterId:Number(semester.value),classId:Number(item.schoolClass.id),tutor1Id:item.one.value?Number(item.one.value):null,tutor2Id:item.two.value?Number(item.two.value):null});status.textContent='Tutorzuordnungen gespeichert.';await render();}catch{status.textContent='Tutorzuordnungen konnten nicht gespeichert werden. Bitte Auswahl prüfen.';saveAll.disabled=false;}});
    status.textContent=`${classes.length} Klassen · ${full} vollständig mit zwei Tutor:innen · ${partial} mit einer Tutor:in · ${none} ohne Tutor:in.`;
  }
  async function init(){status.textContent='Tutorzuordnungen werden geladen …';catalog=await post('/curriculum-enrollment-catalog',{});classes=rows(catalog.classes).filter(item=>Number(item.id)!==0&&item.active!==false&&item.active!==0);teachers=rows(catalog.teachers);
    if(!semester.options.length){rows(catalog.semesters).forEach(item=>semester.append(option(item.label+(item.active?' · AKTIV':''),String(item.id))));const active=rows(catalog.semesters).find(item=>item.active);if(active)semester.value=String(active.id);}
    if(!classes.length){status.textContent='Für Tutorzuordnungen sind keine aktiven normalen Klassen vorhanden.';return;}
    const table=el('table');table.className='admin-tutor-table';const head=el('thead'),tr=el('tr');['Klasse','Tutor:in 1','Tutor:in 2'].forEach(label=>{const th=el('th',label);th.scope='col';tr.append(th);});head.append(tr);table.append(head);tableBody=el('tbody');table.append(tableBody);content.replaceChildren(table);semester.addEventListener('change',()=>render().catch(()=>{status.textContent='Tutorzuordnungen konnten nicht geladen werden.';}));await render();
  }
  init().catch(()=>{status.textContent='Tutorzuordnungen konnten nicht geladen werden.';});
})();
