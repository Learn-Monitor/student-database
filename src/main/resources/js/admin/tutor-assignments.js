(() => {
  const root = document.querySelector('#admin-tutors');
  if (!root) return;
  const el = (tag, textValue) => { const node=document.createElement(tag); if(textValue!==undefined) node.textContent=textValue; return node; };
  const json = (path, options) => fetch(path, options).then(response => { if(!response.ok) throw Error(path); return response.json(); });
  const post = (path, body) => json(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
  const list = value => Array.isArray(value) ? value : [];
  const teacherName = teacher => `${teacher.first_name} ${teacher.last_name}`;
  const option = (label, value) => { const node=el('option',label); node.value=value; return node; };
  function select(teachers, selected) {
    const node=el('select');
    node.append(option('Keine Zuordnung',''));
    teachers.forEach(teacher => node.append(option(teacherName(teacher),String(teacher.id))));
    node.value=selected==null?'':String(selected); return node;
  }
  async function init() {
    root.replaceChildren(el('h3','Tutor:innen je Klasse'));
    const catalog=await post('/curriculum-enrollment-catalog',{});
    const semesters=list(catalog.semesters), classes=list(catalog.classes).filter(item => Number(item.id)!==0 && item.active!==false && item.active!==0), teachers=list(catalog.teachers);
    if(!semesters.length || !classes.length) { root.append(el('p','Für Tutorenzuordnungen werden ein Halbjahr und normale Klassen benötigt.')); return; }
    const semester=el('select'); semesters.forEach(item=>semester.append(option(item.label,String(item.id))));
    const table=el('table'); table.className='admin-tutor-table';
    const head=el('tr'); ['Klasse','Tutor:in 1','Tutor:in 2','Aktion'].forEach(value=>head.append(el('th',value))); const thead=el('thead'); thead.append(head); table.append(thead); const body=el('tbody'); table.append(body);
    const render=async()=>{
      body.replaceChildren();
      const assignments=await post('/curriculum-tutor-assignments',{semesterId:Number(semester.value)});
      const byClass=new Map(); list(assignments).forEach(item=>{ if(!byClass.has(item.classId)) byClass.set(item.classId,{}); byClass.get(item.classId)[item.tutorSlot]=item.teacherId; });
      classes.forEach(schoolClass=>{ const current=byClass.get(Number(schoolClass.id))||{}; const row=el('tr'); row.append(el('td',`${schoolClass.label} (Jg. ${schoolClass.grade})`)); const one=select(teachers,current[1]), two=select(teachers,current[2]); const oneCell=el('td'), twoCell=el('td'); oneCell.append(one); twoCell.append(two); row.append(oneCell,twoCell); const button=el('button','Speichern'); button.type='button'; button.addEventListener('click',async()=>{button.disabled=true; try { await post('/assign-class-tutors',{semesterId:Number(semester.value),classId:Number(schoolClass.id),tutor1Id:one.value?Number(one.value):null,tutor2Id:two.value?Number(two.value):null}); button.textContent='Gespeichert'; setTimeout(()=>{button.textContent='Speichern';button.disabled=false;},800);} catch {button.textContent='Fehler';button.disabled=false;}}); const actionCell=el('td'); actionCell.append(button); row.append(actionCell); body.append(row); });
    };
    semester.addEventListener('change',()=>render().catch(()=>{})); root.append(el('label','Halbjahr '),semester,table); await render();
  }
  init().catch(()=>{root.replaceChildren(el('p','Tutorenzuordnungen konnten nicht geladen werden.'));});
})();
