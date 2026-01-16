package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.extensions.getVedtakOrThrow
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

fun Routing.registerInternalApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    pameldingService: PameldingService,
    deltakerProducerService: DeltakerProducerService,
    vedtakService: VedtakService,
    innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
    vurderingService: VurderingService,
    hendelseService: HendelseService,
    endringFraTiltakskoordinatorService: EndringFraTiltakskoordinatorService,
    vedtakRepository: VedtakRepository,
    navAnsattService: NavAnsattService,
    navEnhetService: NavEnhetService,
) {
    val scope = CoroutineScope(Dispatchers.IO)

    val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun slettDeltaker(deltakerId: UUID) {
        innsokPaaFellesOppstartService.deleteForDeltaker(deltakerId)
        vurderingService.deleteForDeltaker(deltakerId)
        deltakerService.delete(deltakerId)
    }

    suspend fun slettDeltakerKladd(deltakerId: UUID) {
        pameldingService.slettKladd(deltakerId)
    }

    suspend fun ApplicationCall.reproduserDeltakere() {
        val request = this.receive<RelastDeltakereRequest>()
        scope.launch {
            log.info("Relaster ${request.deltakere.size} deltakere komet er master for på deltaker-v2")
            request.deltakere.forEach { deltakerId ->
                deltakerProducerService.produce(
                    deltakerService.get(deltakerId).getOrThrow(),
                    forcedUpdate = request.forcedUpdate,
                    publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    publiserTilDeltakerV2 = request.publiserTilDeltakerV2,
                )
            }
            log.info("Ferdig med reprodusering av ${request.deltakere.size} deltakere på deltaker-v2")
        }
        this.respond(HttpStatusCode.OK)
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

    post("/internal/tving-arena-innlesing") {
        // Brukes i tilfelle man ønsker å tvinge arena til å lese inn en endring
        // selv om det ikke reelt har blitt gjort en endring, som for eksempel
        // når vi har lest inn og transformert status før vi ble master og arena
        // ikke har fått med seg endringen fordi endretDato er før komet ble master
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<RelastDeltakereRequest>()
            log.info("Republiser deltakere:${request.deltakere} deltakere med ny endretDato på deltaker-v1")
            request.deltakere.forEach { deltakerId ->
                deltakerProducerService.produce(
                    deltakerService.get(deltakerId).getOrThrow().copy(sistEndret = LocalDateTime.now()),
                    forcedUpdate = request.forcedUpdate,
                    publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    publiserTilDeltakerV2 = false,
                )
            }

            log.info("Republiserte ${request.deltakere.size} på deltaker-v1")
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/relast/tiltakstype/{tiltakskode}") {
        if (isInternal(call.request.local.remoteAddress)) {
            val tiltakskode = Tiltakskode.valueOf(
                call.parameters["tiltakskode"]
                    ?: throw IllegalArgumentException("Tiltakskode ikke satt"),
            )

            val request = call.receive<RepubliserRequest>()

            scope.launch {
                log.info("Relaster deltakere for tiltakskode ${tiltakskode.name} på deltaker-v2")
                val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)
                deltakerIder.forEach {
                    deltakerProducerService.produce(
                        deltakerService.get(it).getOrThrow(),
                        forcedUpdate = request.forcedUpdate,
                        publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    )
                }
                log.info("Relastet deltakere for tiltakskode ${tiltakskode.name} på deltaker-v2")
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
                for (tiltakskode in Tiltakskode.entries) {
                    val deltakerIder = deltakerRepository.getDeltakerIderForTiltakskode(tiltakskode)

                    log.info("Gjør klar for relast av ${deltakerIder.size} deltakere på tiltakskode ${tiltakskode.name}.")

                    deltakerIder.forEach { deltakerId ->
                        deltakerProducerService.produce(
                            deltakerService.get(deltakerId).getOrThrow(),
                            forcedUpdate = request.forcedUpdate,
                            publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                        )
                    }

                    log.info("Ferdig relastet av ${deltakerIder.size} deltakere på tiltakskode ${tiltakskode.name}.")
                }
                log.info("Ferdig relastet alle deltakere Team Komet er master for på deltaker-v2")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/relast/deltakere") {
        if (isInternal(call.request.local.remoteAddress)) {
            call.reproduserDeltakere()
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

    post("/internal/slett-kladd") {
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<DeleteDeltakereRequest>()
            scope.launch {
                log.info("Sletter ${request.deltakere.size} deltakere med status KLADD")
                request.deltakere.forEach { deltakerId ->
                    slettDeltakerKladd(deltakerId)
                }
                log.info("Slettet ${request.deltakere.size} deltakere med status KLADD")
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

        val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker).getVedtakOrThrow()
        val status = nyDeltakerStatus(
            DeltakerStatus.Type.AVBRUTT_UTKAST,
            DeltakerStatus.Aarsak(type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT, beskrivelse = null),
        )

        val oppdatertDeltaker = deltaker.copy(status = status, vedtaksinformasjon = vedtak.tilVedtaksInformasjon())

        deltakerService.upsertDeltaker(oppdatertDeltaker)
    }

    post("/internal/relast/hendelse-fra-tiltakskoordinator") {
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<RelastHendelseRequest>()
            scope.launch {
                log.info("Relaster hendelse med endringid: ${request.endringId}")

                val endring = endringFraTiltakskoordinatorService.get(request.endringId)
                    ?: throw IllegalArgumentException(
                        "Kunne ikke relaste hendelse med endring med id: ${request.endringId}, kunne ikke finne endring.",
                    )

                val deltaker = deltakerService.get(endring.deltakerId).getOrThrow()

                if (request.relastDeltaker) {
                    deltakerProducerService.produce(
                        deltaker,
                        forcedUpdate = request.forcedUpdate,
                        publiserTilDeltakerV1 = request.publiserTilDeltakerV1,
                    )
                    log.info("Ferdig relastet deltaker ${deltaker.id}")
                }

                hendelseService.produserHendelseFraTiltaksansvarlig(deltaker, endring)

                log.info("Ferdig relastet hendelse med endringId ${request.endringId},")
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    /*
        Brukes for å produsere hendelse til amt-distribusjon i tilfeller hvor manglende transaksjonshåndtering har ført til
        at en deltaker har fått godkjent utkast men handlingen ikke har blitt produsert på topic slik at amt-distribusjon ikke
        får inaktivert oppgave når neste handling blir publisert
        https://trello.com/c/wHea6vGJ/2630-bug-kan-ikke-inaktivere-oppgave-som-om-den-var-en-beskjed
        https://trello.com/c/kxsww0I4/2466-prod-feil-amt-distribusjon-noe-gikk-galt-med-jobb-sendventendevarslerjob
     */
    post("/internal/relast/produser-hendelse-godkjent-utkast") {
        if (isInternal(call.request.local.remoteAddress)) {
            val request = call.receive<ProduserUtkastHendelseRequest>()
            scope.launch {
                request.deltakere.forEach { deltakerId ->
                    val deltaker = deltakerService.get(deltakerId).getOrThrow()
                    val vedtak = vedtakRepository.getForDeltaker(deltakerId).first()

                    if (vedtak.fattet == null) {
                        log.info("Vedtak er ikke fattet for $deltakerId. Avbryter")
                        return@forEach
                    }
                    if (vedtak.fattetAvNav) {
                        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(vedtak.sistEndretAv)
                        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(vedtak.sistEndretAvEnhet)
                        if (request.dryRun) {
                            log.info("DryRun: Produserer hendelse NavGodkjennUtkast for $deltakerId. status ${deltaker.status.type}")
                            return@forEach
                        }
                        hendelseService.produceHendelseForUtkast(deltaker, navAnsatt, navEnhet) {
                            HendelseType.NavGodkjennUtkast(it)
                        }
                    } else {
                        if (request.dryRun) {
                            log.info("DryRun: Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId")
                            return@forEach
                        }
                        log.info("Produserer hendelse InnbyggerGodkjennUtkast for $deltakerId. status ${deltaker.status.type}")
                        hendelseService.hendelseForUtkastGodkjentAvInnbygger(deltaker)
                        log.info("Done: Produserte hendelse InnbyggerGodkjennUtkast for $deltakerId")
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }
}

data class RelastDeltakereRequest(
    val deltakere: List<UUID>,
    val forcedUpdate: Boolean,
    val publiserTilDeltakerV1: Boolean,
    val publiserTilDeltakerV2: Boolean = true,
)

data class ProduserUtkastHendelseRequest(
    val deltakere: List<UUID>,
    val dryRun: Boolean = false,
)

data class RelastHendelseRequest(
    val endringId: UUID,
    val relastDeltaker: Boolean,
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
