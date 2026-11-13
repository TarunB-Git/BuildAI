package com.buildai.app.data.api

import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import com.buildai.app.data.model.QuickTemplate
import org.json.JSONObject

/** Explicitly selected offline features; never a silent replacement for custom generation. */
object QuickTemplateApp {
    fun build(plan: ProjectPlan, design: DesignSpec): String {
        if (plan.quickTemplate == QuickTemplate.NOTES || plan.quickTemplate == QuickTemplate.TASKS)
            return OfflineStarterApp.build(plan, design)
        val config = JSONObject().put("name", plan.appName).put("description", plan.description)
            .put("kind", plan.quickTemplate.name).toString().replace("<", "\\u003c").replace(">", "\\u003e")
        val primary = "#%06X".format(DesignDefaults.palette(design.paletteId).primary and 0xFFFFFF)
        val body = when (plan.quickTemplate) {
            QuickTemplate.CALCULATOR -> """<form id="form"><label>First number<input id="a" type="number" step="any" required></label><label>Operation<select id="op"><option value="+">Add</option><option value="-">Subtract</option><option value="*">Multiply</option><option value="/">Divide</option></select></label><label>Second number<input id="b" type="number" step="any" required></label><button>Calculate</button></form><p id="result" role="status">Enter two numbers.</p>"""
            QuickTemplate.TIMER -> """<label>Duration in minutes<input id="minutes" type="number" min="1" max="180" value="5"></label><p id="clock" role="timer">05:00</p><div class="actions"><button id="start">Start</button><button id="pause">Pause</button><button id="reset">Reset</button></div><p id="result" role="status">The timer runs while this app is open.</p>"""
            QuickTemplate.EXPENSES -> """<h2 id="total">Total: 0.00</h2><form id="form"><label>Description<input id="title" required maxlength="120"></label><label>Amount<input id="amount" type="number" min="0.01" step="0.01" required></label><label>Category<input id="category" maxlength="80" value="Other"></label><button id="save">Add expense</button><button id="cancel" type="button" hidden>Cancel edit</button></form><div id="items"></div>"""
            QuickTemplate.HABITS -> """<form id="form"><label>Habit name<input id="title" required maxlength="120"></label><button>Add habit</button></form><div id="items"></div><h2>Completion history</h2><div id="history"></div>"""
            else -> error("Unsupported template")
        }
        val program = when (plan.quickTemplate) {
            QuickTemplate.CALCULATOR -> """
byId('form').onsubmit=e=>{e.preventDefault();let a=Number(byId('a').value),b=Number(byId('b').value),op=byId('op').value;
if(!Number.isFinite(a)||!Number.isFinite(b)){byId('result').textContent='Enter valid numbers.';return}
if(op==='/'&&b===0){byId('result').textContent='Cannot divide by zero.';return}
let r=op==='+'?a+b:op==='-'?a-b:op==='*'?a*b:a/b;byId('result').textContent='Result: '+r;};
"""
            QuickTemplate.TIMER -> """
let remaining=300,deadline=0,running=false;
function render(){if(running){remaining=Math.max(0,Math.ceil((deadline-Date.now())/1000));if(!remaining){running=false;byId('result').textContent='Time is up!'}}byId('clock').textContent=String(Math.floor(remaining/60)).padStart(2,'0')+':'+String(remaining%60).padStart(2,'0');}
byId('start').onclick=()=>{if(running)return;if(!remaining){byId('reset').click()}deadline=Date.now()+remaining*1000;running=true;byId('result').textContent='Running';render()};
byId('pause').onclick=()=>{render();running=false;byId('result').textContent='Paused'};
byId('reset').onclick=()=>{running=false;remaining=Math.max(1,Math.min(180,Number(byId('minutes').value)||5))*60;byId('result').textContent='Ready';render()};
byId('minutes').onchange=()=>byId('reset').click();setInterval(render,250);render();
"""
            QuickTemplate.EXPENSES -> """
let editing=null;
function reset(){editing=null;byId('form').reset();byId('save').textContent='Add expense';byId('cancel').hidden=true}
function render(){byId('total').textContent='Total: '+items.reduce((sum,x)=>sum+x.amount,0).toFixed(2);byId('items').replaceChildren();items.forEach(x=>{let row=document.createElement('div');row.className='item';let title=document.createElement('p');title.textContent=x.title+' · '+x.category+' · '+x.amount.toFixed(2);row.append(title,button('Edit',()=>{editing=x.id;byId('title').value=x.title;byId('amount').value=x.amount;byId('category').value=x.category;byId('save').textContent='Save changes';byId('cancel').hidden=false}),button('Delete',()=>{items=items.filter(y=>y.id!==x.id);if(editing===x.id)reset();save();render()}));byId('items').append(row)})}
byId('cancel').onclick=reset;byId('form').onsubmit=e=>{e.preventDefault();const amount=Number(byId('amount').value),title=byId('title').value.trim();if(!title||!Number.isFinite(amount)||amount<=0)return;const value={id:editing||String(Date.now())+'-'+Math.random(),title,amount,category:byId('category').value.trim()||'Other'};if(editing)items=items.map(x=>x.id===editing?value:x);else items.push(value);save();reset();render()};render();
"""
            QuickTemplate.HABITS -> """
function today(){let d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')}
function render(){byId('items').replaceChildren();byId('history').replaceChildren();items.forEach(x=>{const row=document.createElement('div');row.className='item';const title=document.createElement('p');title.textContent=x.title;row.append(title,button(x.days.includes(today())?'Undo today':'Done today',()=>{x.days=x.days.includes(today())?x.days.filter(d=>d!==today()):[...x.days,today()];save();render()}),button('Edit',()=>{const value=prompt('Habit name',x.title);if(value&&value.trim()){x.title=value.trim().slice(0,120);save();render()}}),button('Delete',()=>{items=items.filter(y=>y.id!==x.id);save();render()}));byId('items').append(row);let entry=document.createElement('p');entry.textContent=x.title+': '+(x.days.slice().sort().reverse().join(', ')||'No completed days');byId('history').append(entry)})}
byId('form').onsubmit=e=>{e.preventDefault();let title=byId('title').value.trim();if(!title)return;items.push({id:String(Date.now())+'-'+Math.random(),title,days:[]});save();byId('form').reset();render()};render();
"""
            else -> ""
        }
        return """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(plan.appName)}</title><style>
*{box-sizing:border-box}body{margin:0;background:#0d1117;color:#f4f6fb;font:16px system-ui}main{max-width:480px;margin:auto;padding:20px}label{display:block;margin:14px 0}input,select{display:block;width:100%;padding:12px;background:#161b22;color:white;border:1px solid #64748b;border-radius:8px;font:inherit}button{background:$primary;color:white;border:0;border-radius:8px;padding:12px;margin:6px 6px 6px 0;font:inherit;min-height:44px}.item{padding:12px 0;border-bottom:1px solid #64748b}#clock{font-size:3rem;text-align:center}.notice{color:#adb6c4;font-size:.85rem}p{overflow-wrap:anywhere}</style></head><body><main><h1 id="name"></h1><p id="description"></p><p class="notice">Quick template: ${plan.quickTemplate.label}. Saves data on this device; no accounts or internet needed.</p>$body</main><script>(function(){'use strict';const config=$config;const byId=id=>document.getElementById(id);byId('name').textContent=config.name;byId('description').textContent=config.description;const key='buildai.template.'+config.kind+'.v1';let items=[];try{const value=JSON.parse(localStorage.getItem(key)||'[]');if(Array.isArray(value))items=value}catch(e){}function save(){localStorage.setItem(key,JSON.stringify(items))}function button(text,fn){let b=document.createElement('button');b.type='button';b.textContent=text;b.onclick=fn;return b}$program})();</script></body></html>"""
    }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
