package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.auth.AuthorizationException
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

fun Routing.registerInternalApi(deltakerService: DeltakerService, deltakerProducerService: DeltakerProducerService) {
    val scope = CoroutineScope(Dispatchers.IO)

    val log: Logger = LoggerFactory.getLogger(javaClass)

    post("/internal/endre-sokt-inn") {
        if (isInternal(call.request.local.remoteAddress)) {
            scope.launch {
                log.info("Mottatt forespørsel for å endre SOKT_INN til IKKE_AKTUELL.")
                val deltakerIder = deltakerService.getDeltakereMedStatus(DeltakerStatus.Type.SOKT_INN)

                deltakerIder.forEach {
                    val deltaker = deltakerService.get(it).getOrThrow()
                    deltakerService.upsertDeltaker(
                        deltaker.copy(
                            status = DeltakerStatus(
                                id = UUID.randomUUID(),
                                type = DeltakerStatus.Type.IKKE_AKTUELL,
                                aarsak = null,
                                gyldigFra = LocalDateTime.now(),
                                gyldigTil = null,
                                opprettet = LocalDateTime.now(),
                            ),
                        ),
                    )
                }

                log.info("Oppdatert status til IKKE_AKTUELL for ${deltakerIder.size} deltakere med status SOKT_INN.")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

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
            val request = call.receive<RepubliserRequest>()
            scope.launch {
                log.info("Relaster deltakere for tiltakstype ${tiltakstype.name} på deltaker-v2")
                val deltakerIder = deltakerService.getDeltakerIderForTiltakstype(tiltakstype)
                deltakerIder.forEach {
                    deltakerProducerService.produce(
                        deltakerService.get(it).getOrThrow(),
                        forcedUpdate = request.forcedUpdate,
                        publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    )
                }
                log.info("Relastet deltakere for tiltakstype ${tiltakstype.name} på deltaker-v2")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }
}

data class RepubliserRequest(
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
)

fun isInternal(remoteAdress: String): Boolean {
    return remoteAdress == "127.0.0.1"
}
