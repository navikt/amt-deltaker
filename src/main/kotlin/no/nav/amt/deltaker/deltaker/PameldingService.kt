package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.api.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.deltaker.deltaker.api.paamelding.request.UtkastRequest
import no.nav.amt.deltaker.deltaker.extensions.getVedtakOrThrow
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavBruker
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerService: DeltakerService,
    private val deltakerListeRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
    private val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettDeltaker(deltakerListeId: UUID, personIdent: String): Deltaker {
        val eksisterendeDeltaker = deltakerService
            .getDeltakelserForPerson(personIdent, deltakerListeId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker
        }

        return deltakerService
            .upsertDeltaker(
                lagDeltaker(
                    navBrukerService.get(personIdent).getOrThrow(),
                    deltakerListeRepository.get(deltakerListeId).getOrThrow(),
                ),
            ).also { deltaker ->
                log.info("Lagret kladd for deltaker med id ${deltaker.id}")
            }
    }

    fun slettKladd(deltakerId: UUID) {
        val opprinneligDeltaker = deltakerService.get(deltakerId).getOrThrow()
        if (opprinneligDeltaker.status.type != DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke slette deltaker med id $deltakerId som har status ${opprinneligDeltaker.status.type}")
            throw IllegalArgumentException(
                "Kan ikke slette deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
            )
        }
        deltakerService.delete(deltakerId)
    }

    suspend fun upsertUtkast(deltakerId: UUID, utkast: UtkastRequest): Deltaker {
        val opprinneligDeltaker = deltakerService.get(deltakerId).getOrThrow()

        require(kanUpserteUtkast(opprinneligDeltaker.status)) {
            "Kan ikke upserte ukast for deltaker $deltakerId " +
                "med status ${opprinneligDeltaker.status.type}," +
                "status må være ${DeltakerStatus.Type.KLADD} eller ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}."
        }

        val status = getOppdatertStatus(opprinneligDeltaker, utkast.godkjentAvNav)

        val oppdatertDeltaker = opprinneligDeltaker.copy(
            deltakelsesinnhold = utkast.deltakelsesinnhold,
            bakgrunnsinformasjon = utkast.bakgrunnsinformasjon,
            deltakelsesprosent = utkast.deltakelsesprosent,
            dagerPerUke = utkast.dagerPerUke,
            status = status,
            sistEndret = LocalDateTime.now(),
        )

        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(utkast.endretAv)
        val endretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(utkast.endretAvEnhet)

        val fattet = utkast.godkjentAvNav && !oppdatertDeltaker.deltakerliste.erFellesOppstart

        val vedtak = if (fattet) {
            vedtakService.navFattEksisterendeEllerOpprettVedtak(oppdatertDeltaker, endretAv, endretAvNavEnhet)
        } else {
            vedtakService.oppdaterEllerOpprettVedtak(
                deltaker = oppdatertDeltaker,
                endretAv = endretAv,
                endretAvEnhet = endretAvNavEnhet,
            )
        }.getVedtakOrThrow(deltakerId.toString())

        val deltakerMedNyttVedtak = oppdatertDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())

        if (utkast.godkjentAvNav && oppdatertDeltaker.deltakerliste.erFellesOppstart) {
            innsokPaaFellesOppstartService.nyttInnsokUtkastGodkjentAvNav(deltakerMedNyttVedtak, opprinneligDeltaker.status)
        }

        val deltaker = deltakerService.upsertDeltaker(deltakerMedNyttVedtak)

        hendelseService.hendelseForUtkast(deltaker, endretAv, endretAvNavEnhet) {
            if (utkast.godkjentAvNav) {
                HendelseType.NavGodkjennUtkast(it)
            } else if (opprinneligDeltaker.status.type == DeltakerStatus.Type.KLADD) {
                HendelseType.OpprettUtkast(it)
            } else {
                HendelseType.EndreUtkast(it)
            }
        }

        log.info("Upsertet utkast for deltaker med id $deltakerId, meldt på direkte: ${utkast.godkjentAvNav}")

        return deltaker
    }

    suspend fun innbyggerGodkjennUtkast(deltakerId: UUID): Deltaker {
        val opprinneligDeltaker = deltakerService.get(deltakerId).getOrThrow()

        val oppdatertDeltaker = if (opprinneligDeltaker.deltakerliste.erFellesOppstart) {
            innbyggerGodkjennInnsok(opprinneligDeltaker)
        } else {
            deltakerService.innbyggerFattVedtak(opprinneligDeltaker)
        }

        hendelseService.hendelseForUtkastGodkjentAvInnbygger(oppdatertDeltaker)

        return oppdatertDeltaker
    }

    private suspend fun innbyggerGodkjennInnsok(opprinneligDeltaker: Deltaker): Deltaker {
        val oppdatertDeltaker = opprinneligDeltaker.copy(
            status = nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            sistEndret = LocalDateTime.now(),
        )

        innsokPaaFellesOppstartService.nyttInnsokUtkastGodkjentAvDeltaker(oppdatertDeltaker, opprinneligDeltaker.status)

        return deltakerService.upsertDeltaker(oppdatertDeltaker)
    }

    suspend fun avbrytUtkast(deltakerId: UUID, avbrytUtkastRequest: AvbrytUtkastRequest) {
        val opprinneligDeltaker = deltakerService.get(deltakerId).getOrThrow()

        if (opprinneligDeltaker.status.type != DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            log.warn(
                "Kan ikke avbryte utkast for deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
            )
            throw IllegalArgumentException(
                "Kan ikke avbryte utkast for deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
            )
        }

        val status = nyDeltakerStatus(DeltakerStatus.Type.AVBRUTT_UTKAST)

        val oppdatertDeltaker = opprinneligDeltaker.copy(
            status = status,
            sistEndret = LocalDateTime.now(),
        )

        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(avbrytUtkastRequest.avbruttAv)
        val endretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(avbrytUtkastRequest.avbruttAvEnhet)

        val vedtak = vedtakService
            .avbrytVedtak(oppdatertDeltaker, endretAv, endretAvNavEnhet)
            .getVedtakOrThrow("Kunne ikke avbryte vedtak for deltaker $deltakerId")

        deltakerService.upsertDeltaker(oppdatertDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon()))

        hendelseService.hendelseForUtkast(oppdatertDeltaker, endretAv, endretAvNavEnhet) { HendelseType.AvbrytUtkast(it) }

        log.info("Avbrutt utkast for deltaker med id $deltakerId")
    }

    private fun kanUpserteUtkast(opprinneligDeltakerStatus: DeltakerStatus) = opprinneligDeltakerStatus.type in listOf(
        DeltakerStatus.Type.KLADD,
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    )

    private fun getOppdatertStatus(opprinneligDeltaker: Deltaker, godkjentAvNav: Boolean): DeltakerStatus = if (godkjentAvNav) {
        if (opprinneligDeltaker.deltakerliste.erFellesOppstart) {
            nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
        } else if (opprinneligDeltaker.startdato != null && opprinneligDeltaker.startdato.isBefore(LocalDate.now())) {
            nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
        } else {
            nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
        }
    } else {
        when (opprinneligDeltaker.status.type) {
            DeltakerStatus.Type.KLADD -> nyDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> opprinneligDeltaker.status
            else -> throw IllegalArgumentException(
                "Kan ikke upserte utkast for deltaker " +
                    "med status ${opprinneligDeltaker.status.type}," +
                    "status må være ${DeltakerStatus.Type.KLADD} eller ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}.",
            )
        }
    }

    private fun lagDeltaker(navBruker: NavBruker, deltakerListe: Deltakerliste) = Deltaker(
        id = UUID.randomUUID(),
        navBruker = navBruker,
        deltakerliste = deltakerListe,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = Deltakelsesinnhold(deltakerListe.tiltakstype.innhold?.ledetekst, emptyList()),
        status = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
        vedtaksinformasjon = null,
        sistEndret = LocalDateTime.now(),
        kilde = Kilde.KOMET,
        erManueltDeltMedArrangor = false,
        opprettet = LocalDateTime.now(),
    )
}
