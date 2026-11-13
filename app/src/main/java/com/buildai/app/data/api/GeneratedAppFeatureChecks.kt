package com.buildai.app.data.api

import com.buildai.app.data.model.ProjectPlan

/** Only declared, supported workflows get a behavioral check; this is not an arbitrary-app verifier. */
object GeneratedAppFeatureChecks {
    fun forPlan(plan: ProjectPlan): String? {
        val requirements = (listOf(plan.appName, plan.description) + plan.features).joinToString(" ").lowercase()
        if (!Regex("\\b(two|2)\\s+(numbers?|numeric|number inputs?)\\b").containsMatchIn(requirements) ||
            !Regex("\\b(add|adds|addition|sum)\\b").containsMatchIn(requirements)) return null
        return """
            (()=>{try {
                const fields=[...document.querySelectorAll('input')].filter(x=>x.type==='number'||x.type==='text');
                const button=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/calculate|add|sum/i.test(x.textContent||x.value));
                if(fields.length<2||!button)return 'Addition needs two inputs and a Calculate/Add/Sum button.';
                for(const script of document.scripts){
                    for(const ref of script.textContent.matchAll(/getElementById\(\s*['"]([^'"]+)['"]\s*\)/g)){
                        if(!document.getElementById(ref[1]))return 'Missing HTML element with id="'+ref[1]+'". Your JavaScript references it. Add the matching output/control to html. Read number inputs inside the click handler with Number() so decimal addition also works.';
                    }
                }
                for(const [a,b,total] of [[2,3,5],[7,4,11],[1.5,2.25,3.75]]){
                    fields[0].value=String(a);fields[1].value=String(b);
                    fields.forEach(x=>{x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}))});
                    button.click();
                    if(window.__buildaiErrorDetail)return window.__buildaiErrorDetail+' Read numeric input values inside the Calculate click handler. Variables declared inside another handler are out of scope.';
                    const values=(document.body.innerText.match(/-?\d+(?:\.\d+)?/g)||[]).map(Number);
                    if(!values.includes(total)){
                        for(const script of document.scripts){
                            const called=script.textContent.match(/\.addEventListener\(\s*['"](?:click|submit|input|change)['"]\s*,\s*([a-zA-Z_${'$'}][\w${'$'}]*)\(\s*\)\s*\)/);
                            if(called)return 'The event registration calls '+called[1]+'() at startup instead of registering a handler. Use addEventListener(\'click\', '+called[1]+') WITHOUT parentheses, or onclick=()=>{...}. It must run again after typing numbers.';
                        }
                        return 'Adding '+a+' and '+b+' must display '+total+'. Read each input.value INSIDE the click handler and convert it with Number(); do not cache values at page startup or truncate decimals with parseInt().';
                    }
                }
                return true;
            }catch(e){return String(e)}})()
        """.trimIndent()
    }
}
