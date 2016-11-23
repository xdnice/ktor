package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import kotlin.concurrent.*
import kotlin.system.*

class ShutDownUrl(val url: String, val exitCode: ApplicationCall.() -> Int) {

    fun doShutdown(call: ApplicationCall): Nothing {
        call.application.environment.log.warning("Shutdown URL was called: server is going down")
        thread {
            call.application.dispose()
            exitProcess(exitCode(call))
        }

        call.respond(HttpStatusCode.Gone)
    }

    object HostFeature : ApplicationFeature<HostPipeline, ShutDownUrl.Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: HostPipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(HostPipeline.Before) { call ->
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    object ApplicationCallFeature : ApplicationFeature<ApplicationCallPipeline, ShutDownUrl.Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    class Configuration {
        var shutDownUrl = "/ktor/application/shutdown"
        var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }
}