package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import kotlinx.coroutines.delay

/**
 * Factory for 50 additional browser automation tools (browser_*).
 */
object BrowserAdvancedTools {
    private const val TAB_PARAM = """"tab_id":{"type":"string"}"""

    fun createAll(controller: BrowserController): List<AgentTool> =
        SPECS.map { spec -> JsBrowserTool(controller, spec) }

    private data class BrowserToolSpec(
        val name: String,
        val description: String,
        val parametersJson: String,
        val readOnly: Boolean = false,
        val storeKey: String? = null,
        val execute: suspend (BrowserController, Map<String, Any?>, String?, ToolExecutionContext) -> ToolExecutionResult,
    )

    private class JsBrowserTool(
        private val controller: BrowserController,
        private val spec: BrowserToolSpec,
    ) : AgentTool {
        override val name = spec.name
        override val description = spec.description
        override val parametersJson = spec.parametersJson

        override suspend fun execute(
            args: Map<String, Any?>,
            context: ToolExecutionContext,
        ): ToolExecutionResult {
            val tabId = args["tab_id"]?.toString() ?: context.activeTabId
            val result = spec.execute(controller, args, tabId, context)
            spec.storeKey?.let { key ->
                context.extractedData[key] = result.output.take(12_000)
            }
            return result
        }
    }

    private fun js(
        name: String,
        description: String,
        params: String = "",
        readOnly: Boolean = true,
        storeKey: String? = null,
        script: String,
    ) = BrowserToolSpec(
        name = name,
        description = description,
        parametersJson = if (params.isBlank()) {
            """{"type":"object","properties":{$TAB_PARAM}}"""
        } else {
            """{"type":"object","properties":{$params,$TAB_PARAM}}"""
        },
        readOnly = readOnly,
        storeKey = storeKey,
        execute = { controller, args, tabId, _ ->
            val output = controller.evaluateScript(script, tabId) ?: "no_webview"
            ToolExecutionResult(output, success = output != "no_webview" && !output.startsWith("error:"))
        },
    )

    private fun jsWithArgs(
        name: String,
        description: String,
        params: String,
        readOnly: Boolean = false,
        storeKey: String? = null,
        scriptBuilder: (Map<String, Any?>) -> String,
    ) = BrowserToolSpec(
        name = name,
        description = description,
        parametersJson = """{"type":"object","properties":{$params,$TAB_PARAM}}""",
        readOnly = readOnly,
        storeKey = storeKey,
        execute = { controller, args, tabId, _ ->
            val output = controller.evaluateScript(scriptBuilder(args), tabId) ?: "no_webview"
            ToolExecutionResult(output, success = output != "no_webview" && !output.startsWith("error:"))
        },
    )

    private fun native(
        name: String,
        description: String,
        params: String = "",
        readOnly: Boolean = false,
        block: suspend (BrowserController, Map<String, Any?>, String?) -> String,
    ) = BrowserToolSpec(
        name = name,
        description = description,
        parametersJson = if (params.isBlank()) {
            """{"type":"object","properties":{$TAB_PARAM}}"""
        } else {
            """{"type":"object","properties":{$params,$TAB_PARAM}}"""
        },
        readOnly = readOnly,
        execute = { controller, args, tabId, _ ->
            ToolExecutionResult(block(controller, args, tabId), success = true)
        },
    )

    private val SPECS: List<BrowserToolSpec> = listOf(
        // 1–5: navigation & page state
        native("browser_forward", "Go forward one step in browser history.", readOnly = false) { c, _, t ->
            c.goForward(t); "forward"
        },
        native("browser_reload", "Reload the current page.", readOnly = false) { c, _, t ->
            c.reload(t); "reloaded"
        },
        native("browser_stop", "Stop the current page load.", readOnly = false) { c, _, t ->
            c.stopLoading(t); "stopped"
        },
        js("browser_get_title", "Get the document title of the active page.", storeKey = "page_title", script = "document.title"),
        js("browser_get_url", "Get the current page URL.", storeKey = "page_url_live", script = "location.href"),

        // 6–12: element interaction
        jsWithArgs(
            "browser_select_option",
            "Select a dropdown option by @eN ref and visible option text or value.",
            """"ref":{"type":"string"},"value":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                val value = args["value"]?.toString().orEmpty().replace("\\", "\\\\").replace("'", "\\'")
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el||el.tagName!=='SELECT')return 'not_found';
                  for(var i=0;i<el.options.length;i++){
                    if(el.options[i].text.indexOf('$value')>=0||el.options[i].value==='$value'){el.selectedIndex=i;el.dispatchEvent(new Event('change',{bubbles:true}));return 'selected:'+el.options[i].text;}
                  }
                  return 'option_not_found';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_hover",
            "Hover over an interactive element by @eN ref (reveals menus/tooltips).",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));
                  el.dispatchEvent(new MouseEvent('mouseenter',{bubbles:true}));
                  return 'hovered $ref';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_double_click",
            "Double-click an element by @eN ref.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.dispatchEvent(new MouseEvent('dblclick',{bubbles:true,cancelable:true}));
                  return 'double_clicked $ref';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_right_click",
            "Right-click (context menu) an element by @eN ref.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));
                  return 'right_clicked $ref';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_clear_input",
            "Clear text from an input or textarea by @eN ref.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.value='';el.textContent='';
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  return 'cleared $ref';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_focus",
            "Focus an input or button by @eN ref.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.focus();return 'focused $ref';
                })();
                """.trimIndent()
            },
        ),

        // 13–18: checkbox & scroll
        jsWithArgs(
            "browser_checkbox_toggle",
            "Toggle a checkbox or radio button by @eN ref.",
            """"ref":{"type":"string"},"checked":{"type":"boolean"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                val checked = args["checked"]?.toString()?.toBooleanStrictOrNull()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  ${if (checked != null) "el.checked=$checked;" else "el.checked=!el.checked;"}
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                  return 'checked='+el.checked;
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_scroll_to_ref",
            "Scroll an element into view by @eN ref.",
            """"ref":{"type":"string"}""",
            readOnly = true,
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.scrollIntoView({block:'center',behavior:'smooth'});
                  return 'scrolled_to $ref';
                })();
                """.trimIndent()
            },
        ),
        js("browser_scroll_to_top", "Scroll to the top of the page.", readOnly = true, script = "window.scrollTo(0,0);'top'"),
        js("browser_scroll_to_bottom", "Scroll to the bottom of the page.", readOnly = true, script = "window.scrollTo(0,document.body.scrollHeight);'bottom'"),
        jsWithArgs(
            "browser_swipe",
            "Simulate a vertical swipe via smooth scroll (up/down).",
            """"direction":{"type":"string","description":"up or down"},"amount":{"type":"integer"}""",
            readOnly = true,
            scriptBuilder = { args ->
                val dir = args["direction"]?.toString()?.lowercase() ?: "down"
                val amount = args["amount"]?.toString()?.toIntOrNull() ?: 400
                val delta = if (dir == "up") -amount else amount
                "window.scrollBy({top:$delta,behavior:'smooth'});'swiped_$dir'"
            },
        ),
        jsWithArgs(
            "browser_press_and_hold",
            "Simulate press-and-hold on element by @eN ref (500ms).",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));
                  setTimeout(function(){el.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));},500);
                  return 'press_hold $ref';
                })();
                """.trimIndent()
            },
        ),

        // 19–26: extraction
        js(
            "browser_get_links",
            "Extract up to 40 links (text + href) from the page.",
            storeKey = "page_links",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('a[href]').forEach(function(a){
                if(out.length>=40)return;
                var t=(a.innerText||'').trim().slice(0,80);
                var h=a.href||'';
                if(h&&h.indexOf('javascript:')<0)out.push(t+' -> '+h);
              });
              return out.join('\n');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_images",
            "Extract up to 30 image URLs (src + alt) from the page.",
            storeKey = "page_images",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('img[src]').forEach(function(img){
                if(out.length>=30)return;
                out.push((img.alt||'image').slice(0,60)+' -> '+img.src);
              });
              return out.join('\n');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_forms",
            "List form fields (inputs, selects, textareas) with types and labels.",
            storeKey = "page_forms",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('input,select,textarea').forEach(function(el,i){
                if(i>=35)return;
                out.push((el.tagName||'')+' type='+(el.type||'')+' name='+(el.name||'')+' id='+(el.id||'')+' placeholder='+(el.placeholder||''));
              });
              return out.join('\n');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_meta",
            "Get meta description, keywords, and Open Graph tags.",
            storeKey = "page_meta",
            script = """
            (function(){
              var g=function(n){var m=document.querySelector("meta[name='"+n+"'],meta[property='"+n+"']");return m?m.content:'';};
              return 'description:'+g('description')+'\nog:title:'+g('og:title')+'\nog:description:'+g('og:description')+'\nkeywords:'+g('keywords');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_headings",
            "Extract page outline from h1–h6 headings.",
            storeKey = "page_headings",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h){
                out.push(h.tagName+': '+(h.innerText||'').trim().slice(0,120));
              });
              return out.join('\n');
            })();
            """.trimIndent(),
        ),
        jsWithArgs(
            "browser_find_text",
            "Find text on page and return surrounding context snippets.",
            """"query":{"type":"string"}""",
            storeKey = "find_text_results",
            scriptBuilder = { args ->
                val q = args["query"]?.toString().orEmpty().replace("\\", "\\\\").replace("'", "\\'")
                """
                (function(){
                  var text=document.body?document.body.innerText:'';
                  var q='$q';
                  var idx=text.toLowerCase().indexOf(q.toLowerCase());
                  if(idx<0)return 'not_found';
                  var hits=[];
                  var pos=0;
                  while(pos<text.length&&hits.length<5){
                    var i=text.toLowerCase().indexOf(q.toLowerCase(),pos);
                    if(i<0)break;
                    hits.push(text.substring(Math.max(0,i-40),Math.min(text.length,i+q.length+40)));
                    pos=i+q.length;
                  }
                  return hits.join('\n---\n');
                })();
                """.trimIndent()
            },
        ),
        js(
            "browser_get_selection",
            "Get currently selected text on the page.",
            storeKey = "selected_text",
            script = "(window.getSelection?window.getSelection().toString():'')",
        ),

        // 27–32: overlays, modals, cookies
        js(
            "browser_dismiss_overlays",
            "Click common close/dismiss buttons (X, Close, No thanks, Skip).",
            readOnly = false,
            script = """
            (function(){
              var words=['close','dismiss','no thanks','skip','not now','×','✕'];
              var clicked=[];
              document.querySelectorAll('button,[role=button],a,.close,[aria-label]').forEach(function(el){
                var t=((el.innerText||'')+(el.getAttribute('aria-label')||'')).toLowerCase();
                for(var i=0;i<words.length;i++){
                  if(t.indexOf(words[i])>=0&&clicked.length<3){try{el.click();clicked.push(t.slice(0,40));}catch(e){}}
                }
              });
              return clicked.length?('dismissed:'+clicked.join(',')):'no_overlays_found';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_accept_cookies",
            "Click common cookie consent Accept / Allow all buttons.",
            readOnly = false,
            script = """
            (function(){
              var words=['accept all','allow all','accept cookies','agree','i agree','got it','ok'];
              var nodes=document.querySelectorAll('button,[role=button]');
              for(var i=0;i<nodes.length;i++){
                var t=(nodes[i].innerText||'').toLowerCase();
                for(var j=0;j<words.length;j++){
                  if(t.indexOf(words[j])>=0){nodes[i].click();return 'accepted:'+t.slice(0,50);}
                }
              }
              return 'no_cookie_banner';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_close_modal",
            "Close modal dialogs by clicking close buttons or pressing Escape.",
            readOnly = false,
            script = """
            (function(){
              document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
              var modals=document.querySelectorAll('[role=dialog],[class*=modal],[class*=Modal]');
              for(var i=0;i<modals.length;i++){
                var btn=modals[i].querySelector('button,[aria-label*=close],[class*=close]');
                if(btn){btn.click();return 'modal_closed';}
              }
              return 'no_modal';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_cookies_notice",
            "Detect if a cookie consent banner is likely visible.",
            storeKey = "cookie_banner",
            script = """
            (function(){
              var text=(document.body?document.body.innerText:'').toLowerCase();
              var has=text.indexOf('cookie')>=0||text.indexOf('consent')>=0||text.indexOf('privacy')>=0;
              return has?'cookie_notice_detected':'no_notice';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_page_info",
            "Summary: title, URL, language, readyState, link/form counts.",
            storeKey = "page_info",
            script = """
            (function(){
              return JSON.stringify({
                title:document.title,
                url:location.href,
                lang:document.documentElement.lang||'',
                readyState:document.readyState,
                links:document.querySelectorAll('a').length,
                forms:document.querySelectorAll('form').length,
                images:document.querySelectorAll('img').length
              });
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_breadcrumbs",
            "Extract breadcrumb navigation links.",
            storeKey = "breadcrumbs",
            script = """
            (function(){
              var sel=['[class*=breadcrumb] a','nav[aria-label*=breadcrumb] a','ol.breadcrumb a','.breadcrumbs a'];
              var out=[];
              for(var s=0;s<sel.length;s++){
                document.querySelectorAll(sel[s]).forEach(function(a){out.push((a.innerText||'').trim()+' -> '+a.href);});
                if(out.length)break;
              }
              return out.join('\n')||'no_breadcrumbs';
            })();
            """.trimIndent(),
        ),

        // 33–38: wait & visibility
        BrowserToolSpec(
            name = "browser_wait_for_text",
            description = "Wait until specific text appears on the page (polls up to 15s).",
            parametersJson = """{"type":"object","properties":{"text":{"type":"string"},"timeout_ms":{"type":"integer"},$TAB_PARAM},"required":["text"]}""",
            readOnly = true,
            execute = { controller, args, tabId, _ ->
                val text = args["text"]?.toString().orEmpty()
                val timeout = args["timeout_ms"]?.toString()?.toLongOrNull() ?: 15_000L
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeout) {
                    val page = controller.getPageText(tabId).orEmpty()
                    if (page.contains(text, ignoreCase = true)) {
                        return@BrowserToolSpec ToolExecutionResult("found: $text", success = true)
                    }
                    delay(500)
                }
                ToolExecutionResult("timeout waiting for: $text", success = false)
            },
        ),
        BrowserToolSpec(
            name = "browser_wait_for_url",
            description = "Wait until the URL contains a substring (polls up to 15s).",
            parametersJson = """{"type":"object","properties":{"substring":{"type":"string"},"timeout_ms":{"type":"integer"},$TAB_PARAM},"required":["substring"]}""",
            readOnly = true,
            execute = { controller, args, tabId, _ ->
                val sub = args["substring"]?.toString().orEmpty()
                val timeout = args["timeout_ms"]?.toString()?.toLongOrNull() ?: 15_000L
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeout) {
                    val url = controller.getCurrentUrl(tabId).orEmpty()
                    if (url.contains(sub, ignoreCase = true)) {
                        return@BrowserToolSpec ToolExecutionResult("url_match: $url", success = true)
                    }
                    delay(400)
                }
                ToolExecutionResult("timeout waiting for url containing: $sub", success = false)
            },
        ),
        BrowserToolSpec(
            name = "browser_wait_for_element",
            description = "Wait until an element with @eN ref exists (run browser_snapshot first).",
            parametersJson = """{"type":"object","properties":{"ref":{"type":"string"},"timeout_ms":{"type":"integer"},$TAB_PARAM},"required":["ref"]}""",
            readOnly = true,
            execute = { controller, args, tabId, _ ->
                val ref = args["ref"]?.toString().orEmpty()
                val timeout = args["timeout_ms"]?.toString()?.toLongOrNull() ?: 8000L
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeout) {
                    val found = controller.evaluateScript(
                        """document.querySelector('[data-autobrowse-ref="$ref"]')?'found':'missing'""",
                        tabId,
                    )
                    if (found == "found") {
                        return@BrowserToolSpec ToolExecutionResult("found $ref", success = true)
                    }
                    delay(300)
                }
                ToolExecutionResult("timeout waiting for $ref", success = false)
            },
        ),
        jsWithArgs(
            "browser_element_visible",
            "Check if an @eN ref element is visible in the viewport.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  var r=el.getBoundingClientRect();
                  var vis=r.width>0&&r.height>0&&r.top<innerHeight&&r.bottom>0;
                  return vis?'visible':'hidden';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_compare_url",
            "Check if current URL contains the expected substring.",
            """"expected":{"type":"string"}""",
            storeKey = "url_match",
            scriptBuilder = { args ->
                val exp = args["expected"]?.toString().orEmpty().replace("'", "\\'")
                "location.href.toLowerCase().indexOf('$exp'.toLowerCase())>=0?'match':'no_match:'+location.href"
            },
        ),
        jsWithArgs(
            "browser_count_elements",
            "Count DOM elements matching a CSS selector.",
            """"selector":{"type":"string"}""",
            scriptBuilder = { args ->
                val sel = args["selector"]?.toString().orEmpty().replace("'", "\\'")
                "document.querySelectorAll('$sel').length.toString()"
            },
        ),

        // 39–44: screenshot, zoom, js
        BrowserToolSpec(
            name = "browser_screenshot",
            description = "Capture a JPEG screenshot of the page (stores base64 preview in context).",
            parametersJson = """{"type":"object","properties":{$TAB_PARAM}}""",
            readOnly = true,
            storeKey = "screenshot_base64",
            execute = { controller, _, tabId, context ->
                val b64 = controller.captureScreenshotBase64(tabId)
                if (b64 != null) {
                    context.pendingVisionImages += b64
                    ToolExecutionResult("screenshot captured (${b64.length} chars base64)", success = true)
                } else {
                    ToolExecutionResult("screenshot failed", success = false)
                }
            },
        ),
        js("browser_zoom_in", "Increase page text size (browser zoom).", readOnly = false, script = "document.body.style.zoom=(parseFloat(document.body.style.zoom||1)+0.1);'zoom_in'"),
        js("browser_zoom_out", "Decrease page text size (browser zoom).", readOnly = false, script = "document.body.style.zoom=Math.max(0.5,parseFloat(document.body.style.zoom||1)-0.1);'zoom_out'"),
        js("browser_zoom_reset", "Reset page zoom to default.", readOnly = false, script = "document.body.style.zoom='1';'zoom_reset'"),
        jsWithArgs(
            "browser_execute_js",
            "Execute custom JavaScript on the page (read-only expressions recommended).",
            """"script":{"type":"string"}""",
            readOnly = true,
            scriptBuilder = { args ->
                args["script"]?.toString().orEmpty().ifBlank { "''" }
            },
        ),

        // 45–50: tables, extractors, readability, iframes, attributes
        js(
            "browser_get_tables",
            "Extract tables as pipe-separated text rows (up to 5 tables).",
            storeKey = "page_tables",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('table').forEach(function(tbl,ti){
                if(ti>=5)return;
                tbl.querySelectorAll('tr').forEach(function(tr,ri){
                  if(ri>=15)return;
                  var cells=[];
                  tr.querySelectorAll('th,td').forEach(function(c){cells.push((c.innerText||'').trim().slice(0,60));});
                  if(cells.length)out.push(cells.join(' | '));
                });
                out.push('---');
              });
              return out.join('\n');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_extract_prices",
            "Extract price-like patterns ($, €, £) from visible text.",
            storeKey = "extracted_prices",
            script = """
            (function(){
              var text=document.body?document.body.innerText:'';
              var m=text.match(/[$€£]\s?\d+[\d,.]*/g)||[];
              return [...new Set(m)].slice(0,20).join('\n')||'no_prices';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_extract_emails",
            "Extract email addresses from page text.",
            storeKey = "extracted_emails",
            script = """
            (function(){
              var text=document.body?document.body.innerText:'';
              var m=text.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g)||[];
              return [...new Set(m)].slice(0,15).join('\n')||'no_emails';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_extract_phones",
            "Extract phone-number-like patterns from page text.",
            storeKey = "extracted_phones",
            script = """
            (function(){
              var text=document.body?document.body.innerText:'';
              var m=text.match(/(\+?\d[\d\s().-]{8,}\d)/g)||[];
              return [...new Set(m)].slice(0,12).join('\n')||'no_phones';
            })();
            """.trimIndent(),
        ),
        js(
            "browser_readability",
            "Extract main article/content text (heuristic — strips nav/ads).",
            storeKey = "article_text",
            script = """
            (function(){
              var sel=['article','main','[role=main]','.post-content','.article-body','#content'];
              for(var i=0;i<sel.length;i++){
                var el=document.querySelector(sel[i]);
                if(el&&(el.innerText||'').trim().length>200)return (el.innerText||'').trim().slice(0,8000);
              }
              var ps=document.querySelectorAll('p');
              var buf=[];
              ps.forEach(function(p){if(buf.join('').length<8000)buf.push((p.innerText||'').trim());});
              return buf.join('\n\n').slice(0,8000)||(document.body?document.body.innerText.slice(0,4000):'');
            })();
            """.trimIndent(),
        ),
        js(
            "browser_get_iframes",
            "List iframe sources on the page.",
            storeKey = "page_iframes",
            script = """
            (function(){
              var out=[];
              document.querySelectorAll('iframe[src]').forEach(function(f){
                out.push((f.title||'iframe')+' -> '+f.src);
              });
              return out.join('\n')||'no_iframes';
            })();
            """.trimIndent(),
        ),
        jsWithArgs(
            "browser_get_attributes",
            "Get an HTML attribute value from an element by @eN ref.",
            """"ref":{"type":"string"},"attribute":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                val attr = args["attribute"]?.toString().orEmpty().replace("'", "\\'")
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  return el.getAttribute('$attr')||'';
                })();
                """.trimIndent()
            },
        ),
        jsWithArgs(
            "browser_select_all_text",
            "Select all text inside an input, textarea, or contenteditable by @eN ref.",
            """"ref":{"type":"string"}""",
            scriptBuilder = { args ->
                val ref = args["ref"]?.toString().orEmpty()
                """
                (function(){
                  var el=document.querySelector('[data-autobrowse-ref="$ref"]');
                  if(!el)return 'not_found';
                  el.focus();
                  if(el.select){el.select();return 'selected_all';}
                  var r=document.createRange();r.selectNodeContents(el);
                  var s=window.getSelection();s.removeAllRanges();s.addRange(r);
                  return 'selected_all';
                })();
                """.trimIndent()
            },
        ),
        js(
            "browser_get_timing",
            "Get page load timing metrics (navigation, DOMContentLoaded, load).",
            storeKey = "page_timing",
            script = """
            (function(){
              var t=performance.timing||{};
              return JSON.stringify({
                domContentLoaded:t.domContentLoadedEventEnd-t.navigationStart,
                loadComplete:t.loadEventEnd-t.navigationStart,
                response:t.responseEnd-t.requestStart,
                readyState:document.readyState
              });
            })();
            """.trimIndent(),
        ),
    ).also { specs ->
        require(specs.size == 50) { "Expected exactly 50 advanced browser tools, got ${specs.size}" }
    }

    /** Read-only tools: refresh context only, no post-action wait. */
    val READ_ONLY_NAMES: Set<String> = SPECS.filter { it.readOnly }.map { it.name }.toSet()
}