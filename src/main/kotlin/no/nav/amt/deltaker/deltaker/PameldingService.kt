package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.AvbrytUtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.KladdResponse
import no.nav.amt.deltaker.deltaker.api.model.UtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.isoppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.hendelse.HendelseType
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val vedtakService: VedtakService,
    private val isOppfolgingstilfelleClient: IsOppfolgingstilfelleClient,
    private val hendelseService: HendelseService,
    private val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(deltakerlisteId: UUID, personident: String): KladdResponse {
        val eksisterendeDeltaker = deltakerService
            .getDeltakelserForPerson(personident, deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker.toKladdResponse()
        }

        val deltakerliste = deltakerlisteRepository.get(deltakerlisteId).getOrThrow()
        if (deltakerliste.erAvsluttet()) {
            log.warn("Deltakerliste med id $deltakerlisteId er avsluttet")
            throw IllegalArgumentException("Deltakerliste er avsluttet")
        }
        if (!deltakerliste.apentForPamelding) {
            log.warn("Deltakerliste med id $deltakerlisteId er ikke åpen for påmelding")
            throw IllegalArgumentException("Deltakerliste er ikke åpen for påmelding")
        }
        val navBruker = navBrukerService.get(personident).getOrThrow()

        if (!harRiktigInnsatsgruppe(navBruker, deltakerliste)) {
            log.warn("Bruker med id ${navBruker.personId} har ikke riktig innsatsgruppe")
            throw IllegalArgumentException("Bruker har ikke riktig innsatsgruppe")
        }

        val deltaker = nyDeltakerKladd(navBruker, deltakerliste)

        val oppdatertDeltaker = deltakerService.upsertDeltaker(deltaker)

        log.info("Lagret kladd for deltaker med id ${deltaker.id}")

        return oppdatertDeltaker.toKladdResponse()
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

        val fattet = utkast.godkjentAvNav && !oppdatertDeltaker.deltakerliste.erKurs()

        val vedtak = vedtakService.oppdaterEllerOpprettVedtak(
            deltaker = oppdatertDeltaker,
            endretAv = endretAv,
            endretAvEnhet = endretAvNavEnhet,
            fattet = fattet,
            fattetAvNav = fattet,
        )

        val deltakerMedNyttVedtak = oppdatertDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksinformasjon())

        if (utkast.godkjentAvNav && oppdatertDeltaker.deltakerliste.erKurs()) {
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

        val oppdatertDeltaker = if (opprinneligDeltaker.deltakerliste.erKurs()) {
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

        deltakerService.upsertDeltaker(oppdatertDeltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksinformasjon()))

        hendelseService.hendelseForUtkast(oppdatertDeltaker, endretAv, endretAvNavEnhet) { HendelseType.AvbrytUtkast(it) }

        log.info("Avbrutt utkast for deltaker med id $deltakerId")
    }

    private suspend fun harRiktigInnsatsgruppe(navBruker: NavBruker, deltakerliste: Deltakerliste): Boolean =
        if (navBruker.innsatsgruppe in deltakerliste.tiltakstype.innsatsgrupper) {
            true
        } else if (deltakerliste.tiltakstype.tiltakskode == Tiltakstype.Tiltakskode.ARBEIDSRETTET_REHABILITERING &&
            navBruker.innsatsgruppe == Innsatsgruppe.SITUASJONSBESTEMT_INNSATS
        ) {
            log.info("Sjekker om bruker er sykmeldt med arbeidsgiver")
            isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(navBruker.personident)
        } else {
            false
        }

    private fun kanUpserteUtkast(opprinneligDeltakerStatus: DeltakerStatus) = opprinneligDeltakerStatus.type in listOf(
        DeltakerStatus.Type.KLADD,
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    )

    private fun getOppdatertStatus(opprinneligDeltaker: Deltaker, godkjentAvNav: Boolean): DeltakerStatus = if (godkjentAvNav) {
        if (opprinneligDeltaker.deltakerliste.erKurs()) {
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

    private fun nyDeltakerKladd(navBruker: NavBruker, deltakerliste: Deltakerliste) = Deltaker(
        id = UUID.randomUUID(),
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = Deltakelsesinnhold(deltakerliste.tiltakstype.innhold?.ledetekst, emptyList()),
        status = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
        vedtaksinformasjon = null,
        sistEndret = LocalDateTime.now(),
        kilde = Kilde.KOMET,
        erManueltDeltMedArrangor = false,
        opprettet = LocalDateTime.now(),
    )
}

fun Vedtak.tilVedtaksinformasjon(): Deltaker.Vedtaksinformasjon = Deltaker.Vedtaksinformasjon(
    fattet = fattet,
    fattetAvNav = fattetAvNav,
    opprettet = opprettet,
    opprettetAv = opprettetAv,
    opprettetAvEnhet = opprettetAvEnhet,
    sistEndret = sistEndret,
    sistEndretAv = sistEndretAv,
    sistEndretAvEnhet = sistEndretAvEnhet,
)
