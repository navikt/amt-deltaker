package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.auth.AuthorizationException
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.tilVedtaksinformasjon
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.util.UUID

fun Routing.registerInternalApi(
    deltakerService: DeltakerService,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
    vurderingService: VurderingService,
) {
    val scope = CoroutineScope(Dispatchers.IO)

    val log: Logger = LoggerFactory.getLogger(javaClass)

    fun slettDeltaker(deltakerId: UUID) {
        innsokPaaFellesOppstartService.deleteForDeltaker(deltakerId)
        vurderingService.deleteForDeltaker(deltakerId)
        deltakerService.delete(deltakerId)
    }

    post("/internal/sett-ikke-aktuell/{fra-status}") {
        if (isInternal(call.request.local.remoteAddress)) {
            scope.launch {
                val fraStatus = DeltakerStatus.Type.valueOf(call.parameters["fra-status"]!!)
                log.info("Mottatt forespørsel for å endre deltakere med status $fraStatus til IKKE_AKTUELL.")
                val deltakerIder = deltakerService.getDeltakereMedStatus(fraStatus)

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
                        forcedUpdate = true,
                    )
                }

                log.info("Oppdatert status til IKKE_AKTUELL for ${deltakerIder.size} deltakere med status $fraStatus.")
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
            val request = call.receive<RepubliserRequest>()
            val deltaker = deltakerService.get(deltakerId)
            log.info("Relaster deltaker $deltakerId på deltaker-v2")
            deltakerProducerService.produce(
                deltaker.getOrThrow(),
                forcedUpdate = request.forcedUpdate,
                publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
            )
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

    post("/internal/relast/alle-deltakere") {
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<RepubliserRequest>()
            scope.launch {
                log.info("Relaster alle deltakere komet er master for på deltaker-v2")
                for (tiltakstype in Tiltakstype.ArenaKode.entries) {
                    val deltakerIder = deltakerService.getDeltakerIderForTiltakstype(tiltakstype)
                    log.info("Gjør klar for relast av ${deltakerIder.size} deltakere på tiltakstype ${tiltakstype.name}.")
                    deltakerIder.forEach {
                        deltakerProducerService.produce(
                            deltakerService.get(it).getOrThrow(),
                            forcedUpdate = request.forcedUpdate,
                            publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                        )
                    }
                    log.info("Ferdig relastet av ${deltakerIder.size} deltakere på tiltakstype ${tiltakstype.name}.")
                }
                log.info("Ferdig relastet alle deltakere komet er master for på deltaker-v2")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/relast/deltakere") {
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<RelastDeltakereRequest>()
            scope.launch {
                log.info("Relaster ${request.deltakere.size} deltakere komet er master for på deltaker-v2")
                request.deltakere.forEach { deltakerId ->
                    deltakerProducerService.produce(
                        deltakerService.get(deltakerId).getOrThrow(),
                        forcedUpdate = request.forcedUpdate,
                        publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    )
                }
                log.info("Ferdig relastet alle deltakere komet er master for på deltaker-v2")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/slett-deltakere") {
        if (isInternal(call.request.local.remoteAddress)) {
            if (!Environment.isDev()) throw IllegalStateException("Kan kun slette deltaker i dev")
            val request = call.receive<DeleteDeltakereRequest>()
            scope.launch {
                log.info("Sletter ${request.deltakere.size} deltakere")
                request.deltakere.forEach { deltakerId ->
                    deltakerProducerService.tombstone(deltakerId)
                    slettDeltaker(deltakerId)
                }
                log.info("Slettet ${request.deltakere.size} deltakere")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    get("internal/avbryt-utkast/{deltakerId}") {
        if (!isInternal(call.request.local.remoteAddress)) {
            throw AuthorizationException("Ikke tilgang til api")
        }

        val deltakerId = call.parameters.getOrFail("deltakerId").let { UUID.fromString(it) }
        val deltaker = deltakerService.get(deltakerId).getOrThrow()

        val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker)
        val status = nyDeltakerStatus(
            DeltakerStatus.Type.AVBRUTT_UTKAST,
            DeltakerStatus.Aarsak(type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT, beskrivelse = null),
        )

        val oppdatertDeltaker = deltaker.copy(status = status, vedtaksinformasjon = vedtak.tilVedtaksinformasjon())

        deltakerService.upsertDeltaker(oppdatertDeltaker)
    }
}

data class RelastDeltakereRequest(
    val deltakere: List<UUID>,
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
)

data class DeleteDeltakereRequest(
    val deltakere: List<UUID>,
)

data class RepubliserRequest(
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
)

fun isInternal(remoteAdress: String): Boolean = remoteAdress == "127.0.0.1"
