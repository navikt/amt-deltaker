package no.nav.amt.deltaker.application.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.registerHealthApi
import no.nav.amt.deltaker.auth.AuthenticationException
import no.nav.amt.deltaker.auth.AuthorizationException
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
import no.nav.amt.deltaker.deltaker.api.registerDeltakerApi
import no.nav.amt.deltaker.deltaker.api.registerHentDeltakelserApi
import no.nav.amt.deltaker.deltaker.api.registerPameldingApi
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.internal.registerInternalApi
import no.nav.amt.deltaker.testdata.TestdataService
import no.nav.amt.deltaker.testdata.registerTestdataApi
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.tiltakskoordinator.registerTiltakskoordinatorApi
import no.nav.amt.deltaker.unleash.UnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Application.configureRouting(
    pameldingService: PameldingService,
    deltakerService: DeltakerService,
    deltakerHistorikkService: DeltakerHistorikkService,
    tilgangskontrollService: TilgangskontrollService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    unleashToggle: UnleashToggle,
    innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
    vurderingService: VurderingService,
    hendelseService: HendelseService,
    endringFraTiltakskoordinatorService: EndringFraTiltakskoordinatorService,
    testdataService: TestdataService,
) {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.BadRequest, call, cause)
            call.respondText(text = "400: ${cause.message}", status = HttpStatusCode.BadRequest)
        }
        exception<AuthenticationException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.Forbidden, call, cause)
            call.respondText(text = "401: ${cause.message}", status = HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.Forbidden, call, cause)
            call.respondText(text = "403: ${cause.message}", status = HttpStatusCode.Forbidden)
        }
        exception<NoSuchElementException> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.NotFound, call, cause)
            call.respondText(text = "404: ${cause.message}", status = HttpStatusCode.NotFound)
        }
        exception<Throwable> { call, cause ->
            StatusPageLogger.log(HttpStatusCode.InternalServerError, call, cause)
            call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        registerHealthApi()

        registerPameldingApi(pameldingService, deltakerHistorikkService)
        registerDeltakerApi(deltakerService, deltakerHistorikkService)
        registerHentDeltakelserApi(tilgangskontrollService, deltakerService, deltakelserResponseMapper, unleashToggle)
        registerInternalApi(
            deltakerService,
            deltakerProducerService,
            vedtakService,
            innsokPaaFellesOppstartService,
            vurderingService,
            hendelseService,
            endringFraTiltakskoordinatorService,
        )
        registerTiltakskoordinatorApi(deltakerService)

        if (!Environment.isProd()) {
            registerTestdataApi(testdataService)
        }

        val catchAllRoute = "{...}"
        route(catchAllRoute) {
            handle {
                StatusPageLogger.log(
                    HttpStatusCode.NotFound,
                    this.call,
                    NoSuchElementException("Endepunktet eksisterer ikke"),
                )
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

object StatusPageLogger {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    fun log(
        statusCode: HttpStatusCode,
        call: ApplicationCall,
        cause: Throwable,
    ) {
        val msg = "${statusCode.value} ${statusCode.description}: " +
            "${call.request.httpMethod.value} ${call.request.path()}\n" +
            "Error: ${cause.message}"

        when (statusCode.value) {
            in 100..399 -> log.info(msg)
            in 400..404 -> log.warn(msg)
            else -> log.error(msg, cause)
        }
    }
}
