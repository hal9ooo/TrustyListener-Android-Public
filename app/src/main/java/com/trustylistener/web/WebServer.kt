package com.trustylistener.web

import android.content.Context
import android.util.Log
import com.trustylistener.domain.usecase.GetRecentLogsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor web server for web UI
 * Provides REST API and HTML interface like the desktop version
 */
@Singleton
class WebServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getRecentLogsUseCase: GetRecentLogsUseCase
) {
    companion object {
        private const val TAG = "WebServer"
        private const val PORT = 8080
    }

    private var server: ApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, PORT) {
            routing {
                // Main page
                get("/") {
                    call.respondHtml {
                        renderMainPage()
                    }
                }

                // API endpoints
                get("/api/logs") {
                    try {
                        val logs = getRecentLogsUseCase.getRecent(100)
                        val json = Json.encodeToString(logs)
                        call.respondText(json, ContentType.Application.Json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching logs", e)
                        call.respondText(
                            """{"error": "${e.message}"}""",
                            ContentType.Application.Json,
                            status = HttpStatusCode.InternalServerError
                        )
                    }
                }

                // Health check
                get("/api/health") {
                    call.respondText(
                        """{"status": "ok", "port": $PORT}""",
                        ContentType.Application.Json
                    )
                }

                // Static assets
                staticResources()
            }
        }.start(wait = false)

        Log.d(TAG, "Web server started on port $PORT")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        scope.cancel()
        Log.d(TAG, "Web server stopped")
    }

    private fun Routing.staticResources() {
        val appContext = context
        // Serve clips if needed
        get("/clips/{filename}") {
            val filename = call.parameters["filename"]
            if (filename != null) {
                val file = File(appContext.filesDir, "audio_clips/$filename")
                if (file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respondText("File not found", status = HttpStatusCode.NotFound)
                }
            } else {
                call.respondText("Filename not specified", status = HttpStatusCode.BadRequest)
            }
        }
    }

    private fun HTML.renderMainPage() {
        head {
            title("TrustyListener - Web UI")
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            style {
                unsafe {
                    raw("""
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }
                .header { background: #1976d2; color: white; padding: 1rem; }
                .container { max-width: 1200px; margin: 0 auto; padding: 1rem; }
                .card { background: white; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .log-item { padding: 0.75rem; border-bottom: 1px solid #eee; }
                .log-item:last-child { border-bottom: none; }
                .timestamp { color: #666; font-size: 0.875rem; }
                .class-name { font-weight: 600; font-size: 1.1rem; }
                .score { color: #1976d2; font-weight: 500; }
                .status { padding: 0.5rem 1rem; background: #4caf50; color: white; border-radius: 4px; display: inline-block; }
                """.trimIndent())
                }
            }
        }
        body {
            div(classes = "header") {
                h1 { +"TrustyListener" }
                p { +"Audio Event Detection" }
            }
            div(classes = "container") {
                div(classes = "card") {
                    h2 { +"Stato" }
                    span(classes = "status") { +"Attivo" }
                    p { +"Web server in esecuzione su porta $PORT" }
                }

                div(classes = "card") {
                    h2 { +"Eventi Recenti" }
                    div { id = "logs"
                        p { +"Caricamento..." }
                    }
                }
            }

            script {
                unsafe {
                    raw("""
                async function loadLogs() {
                    try {
                        const response = await fetch('/api/logs');
                        const logs = await response.json();
                        const logsDiv = document.getElementById('logs');

                        if (logs.length === 0) {
                            logsDiv.innerHTML = '<p>Nessun evento registrato</p>';
                            return;
                        }

                        logsDiv.innerHTML = logs.map(log => `
                            <div class="log-item">
                                <div class="timestamp">${'$'}{new Date(log.timestamp).toLocaleString()}</div>
                                <div class="class-name">${'$'}{log.className}</div>
                                <div class="score">Confidenza: ${'$'}{(log.score * 100).toFixed(1)}%</div>
                            </div>
                        `).join('');
                    } catch (error) {
                        console.error('Error loading logs:', error);
                    }
                }

                loadLogs();
                setInterval(loadLogs, 5000); // Refresh every 5 seconds
                """.trimIndent())
                }
            }
        }
    }
}
