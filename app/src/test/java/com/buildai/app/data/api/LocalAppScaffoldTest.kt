package com.buildai.app.data.api

import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.DesignSpec
import org.junit.Assert.*
import org.junit.Test

class LocalAppScaffoldTest {
    @Test fun wrapsStructuredCodeAndKeepsScriptClosingTextInsideTheScript() {
        val code = org.json.JSONObject()
            .put("html", "<input id='a'><button id='sum'>Calculate</button><output id='result'></output>")
            .put("js", "document.getElementById('sum').onclick=()=>document.getElementById('result').textContent=5;const text='</script><img src=x>';" )
        val html = LocalAppScaffold.wrap(code.toString(), QuickPlan.fromIdea("Add two numbers"), DesignSpec())
        assertTrue(html.contains("<output id='result'>"))
        assertTrue(html.contains(".textContent=5"))
        assertTrue(html.contains("<\\/script><img src=x>"))
        assertEquals(1, Regex("</script>").findAll(html).count())
    }

    @Test fun preservesCustomBehaviorAndEscapesPageLabels() {
        val fragment = "<app><input id='x'><button onclick='document.getElementById(\"x\").value=5'>Calculate</button><script>const answer=5;</script></app>"
        val html = HtmlValidator.extractAndValidate(LocalAppScaffold.wrap(fragment,
            QuickPlan.fromIdea("Add two numbers").copy(appName = "<img src=x>"), DesignSpec()))
        assertTrue(html.contains("const answer=5;"))
        assertTrue(html.contains("id='x'"))
        assertFalse(html.contains("<img src=x>"))
        assertFalse(html.contains("Offline starter app"))
    }
    @Test fun refusesTruncatedOutputInsteadOfCreatingAList() {
        try {
            LocalAppScaffold.wrap("<app><input>", QuickPlan.fromIdea("calculator"), DesignSpec())
            fail("An unfinished model response must fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("did not finish"))
        }
    }
}
