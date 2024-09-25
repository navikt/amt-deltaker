package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.auth.AuthorizationException
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

fun Routing.registerInternalApi(deltakerService: DeltakerService, deltakerProducerService: DeltakerProducerService) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    post("/internal/feilregistrer/{deltakerId}") {
        if (isInternal(call.request.local.remoteAddress)) {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            deltakerService.feilregistrerDeltaker(deltakerId)
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/relast/{deltakerId}") {
        if (isInternal(call.request.local.remoteAddress)) {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val deltaker = deltakerService.get(deltakerId)
            log.info("Relaster deltaker $deltakerId på deltaker-v2")
            deltakerProducerService.produce(deltaker.getOrThrow(), publiserTilDeltakerV1 = false)
            log.info("Relastet deltaker $deltakerId på deltaker-v2")
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/relast/tiltakstype/{tiltakstype}") {
        if (isInternal(call.request.local.remoteAddress)) {
            val tiltakstype = Tiltakstype.ArenaKode.valueOf(call.parameters["tiltakstype"]!!)
            log.info("Relaster deltakere for tiltakstype ${tiltakstype.name} på deltaker-v2")
            val deltakere = deltakerService.getDeltakereForTiltakstype(tiltakstype)
            deltakere.forEach {
                deltakerProducerService.produce(it, publiserTilDeltakerV1 = false)
            }
            log.info("Relastet deltakere for tiltakstype ${tiltakstype.name} på deltaker-v2")
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }
}

fun isInternal(remoteAdress: String): Boolean {
    return remoteAdress == "127.0.0.1"
}
