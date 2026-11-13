package com.buildai.app.data.api

import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import org.json.JSONArray
import org.json.JSONObject

/** A working offline organizer when a small local model cannot produce valid custom code. */
object OfflineStarterApp {
    fun build(plan: ProjectPlan, design: DesignSpec): String {
        val palette = DesignDefaults.palette(design.paletteId)
        val primary = "#%06X".format(palette.primary and 0xFFFFFF)
        val accent = "#%06X".format(palette.accent and 0xFFFFFF)
        val sections = (if (DesignDefaults.template(design.templateId).hasBottomNav)
            design.tabs.map { it.name } else plan.screens).filter { it.isNotBlank() }.distinct().take(5)
            .ifEmpty { listOf("Home") }
        val config = JSONObject()
            .put("name", plan.appName.take(80))
            .put("description", plan.description.take(300))
            .put("sections", JSONArray(sections))
            .put("features", JSONArray(plan.features.take(12)))
            .toString().replace("<", "\\u003c").replace(">", "\\u003e")
        return """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(plan.appName)}</title>
<style>
:root{color-scheme:dark;--primary:$primary;--accent:$accent;--bg:#0d1117;--card:#161b22;--text:#f4f6fb;--muted:#adb6c4}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px system-ui,sans-serif}main{max-width:460px;margin:auto;min-height:100vh;padding:20px 16px 90px}h1{font-size:1.5rem;margin:0 0 6px}h2{font-size:1.1rem;margin:0 0 12px}p{line-height:1.5}.muted{color:var(--muted)}.card{background:var(--card);padding:16px;border-radius:12px;margin:14px 0;box-shadow:0 5px 15px #0003}.notice{border-left:4px solid var(--accent)}label{display:block;font-weight:600;margin:10px 0 5px}input,textarea{width:100%;padding:12px;background:#0b111b;color:var(--text);border:1px solid #64748b;border-radius:8px;font:inherit}textarea{min-height:80px}button{border:0;border-radius:9px;background:var(--primary);color:white;padding:10px 13px;font:inherit;font-weight:600;min-height:44px;cursor:pointer}button.secondary{background:#334155}button.danger{background:#9f293b}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}.item{border-top:1px solid #384255;padding:14px 0}.item:first-child{border-top:0}.item-title{font-weight:700;overflow-wrap:anywhere}.done{text-decoration:line-through;color:var(--muted)}.error{color:#fca5a5;min-height:1.2em}nav{display:flex;gap:6px;overflow:auto;padding:10px 0}nav button{white-space:nowrap;background:#334155}nav button.active{background:var(--primary)}.small{font-size:.86rem}.check{width:auto;margin-right:8px}details summary{cursor:pointer}
</style></head><body><main>
<header><h1 id="app-name"></h1><p class="muted" id="app-description"></p></header>
<div class="card notice" role="note"><strong>Offline starter app</strong><p class="small">Lists, notes and checkboxes work and save on this device. Custom features from your plan still need review.</p><details><summary>See requested features</summary><ul id="features"></ul></details></div>
<nav id="sections" aria-label="App sections"></nav>
<section class="card"><h2 id="section-title"></h2><label for="search">Find an item</label><input id="search" type="search" placeholder="Search this section"><div id="items" aria-live="polite"></div></section>
<section class="card"><h2 id="form-heading">Add an item</h2><form id="item-form"><label for="title">Title</label><input id="title" maxlength="120" required placeholder="What do you want to remember?"><label for="details">Details</label><textarea id="details" maxlength="4000" placeholder="Add a note (optional)"></textarea><p id="error" class="error" role="alert"></p><div class="actions"><button id="save" type="submit">Add item</button><button id="cancel" class="secondary" type="button" hidden>Cancel edit</button></div></form></section>
</main><script>
(function(){
'use strict';
const config=$config;
const storageKey='buildai.offline-starter.v1';
const byId=id=>document.getElementById(id);
let items=[];let section=config.sections[0];let editing=null;
try {const saved=JSON.parse(localStorage.getItem(storageKey)||'[]');items=Array.isArray(saved)?saved:[]} catch(e){items=[]}
function syncWidget(){if(window.BuildAIWidget&&typeof window.BuildAIWidget.updateSummary==='function'){window.BuildAIWidget.updateSummary(JSON.stringify({total:items.length,done:items.filter(x=>x.done).length}),window.__buildaiProjectId||'')}}
function save(){localStorage.setItem(storageKey,JSON.stringify(items));syncWidget()}
function resetForm(){editing=null;byId('item-form').reset();byId('form-heading').textContent='Add an item';byId('save').textContent='Add item';byId('cancel').hidden=true;byId('error').textContent=''}
function control(label,kind,action){const b=document.createElement('button');b.type='button';b.textContent=label;b.className=kind;b.addEventListener('click',action);return b}
function render(){
 byId('app-name').textContent=config.name;byId('app-description').textContent=config.description;
 byId('section-title').textContent=section;
 const nav=byId('sections');nav.replaceChildren();config.sections.forEach(name=>{let b=control(name,name===section?'active':'secondary',()=>{section=name;resetForm();render()});nav.appendChild(b)});
 const features=byId('features');features.replaceChildren();config.features.forEach(feature=>{const li=document.createElement('li');li.textContent=feature;features.appendChild(li)});
 const container=byId('items');container.replaceChildren();const q=byId('search').value.trim().toLowerCase();const visible=items.filter(item=>item.section===section&&(item.title+' '+item.details).toLowerCase().includes(q));
 if(!visible.length){const empty=document.createElement('p');empty.className='muted';empty.textContent=q?'No matching items.':'No items yet — add your first one!';container.appendChild(empty)}
 visible.forEach(item=>{const row=document.createElement('div');row.className='item';const line=document.createElement('label');const check=document.createElement('input');check.type='checkbox';check.className='check';check.checked=!!item.done;check.addEventListener('change',()=>{item.done=check.checked;save();render()});line.appendChild(check);const name=document.createElement('span');name.className='item-title'+(item.done?' done':'');name.textContent=item.title;line.appendChild(name);row.appendChild(line);if(item.details){const note=document.createElement('p');note.textContent=item.details;row.appendChild(note)}const actions=document.createElement('div');actions.className='actions';actions.appendChild(control('Edit','secondary',()=>{editing=item.id;byId('title').value=item.title;byId('details').value=item.details;byId('form-heading').textContent='Edit item';byId('save').textContent='Save changes';byId('cancel').hidden=false;byId('title').focus()}));actions.appendChild(control('Delete','danger',()=>{if(confirm('Delete this item?')){items=items.filter(other=>other.id!==item.id);if(editing===item.id)resetForm();save();render()}}));row.appendChild(actions);container.appendChild(row)})
}
byId('search').addEventListener('input',render);byId('cancel').addEventListener('click',resetForm);
byId('item-form').addEventListener('submit',event=>{event.preventDefault();const title=byId('title').value.trim();if(!title){byId('error').textContent='Please enter a title.';return}const details=byId('details').value.trim();if(editing){const item=items.find(x=>x.id===editing);if(item){item.title=title;item.details=details}}else{items.push({id:String(Date.now())+'-'+Math.random(),section:section,title:title,details:details,done:false})}save();resetForm();render()});
render();syncWidget();
})();
</script></body></html>""".trimIndent()
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
