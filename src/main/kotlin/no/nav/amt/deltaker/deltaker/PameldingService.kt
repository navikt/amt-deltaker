package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.KladdResponse
import no.nav.amt.deltaker.deltaker.api.model.UtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val vedtakRepository: VedtakRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(
        deltakerlisteId: UUID,
        personident: String,
    ): KladdResponse {
        val eksisterendeDeltaker = deltakerService
            .getDeltakelser(personident, deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker.toKladdResponse()
        }

        val deltakerliste = deltakerlisteRepository.get(deltakerlisteId).getOrThrow()
        val navBruker = navBrukerService.get(personident).getOrThrow()
        val deltaker = nyDeltakerKladd(navBruker, deltakerliste)

        deltakerService.upsertDeltaker(deltaker)

        log.info("Lagret kladd for deltaker med id ${deltaker.id}")

        return deltakerService.get(deltaker.id).getOrThrow()
            .toKladdResponse()
    }

    suspend fun upsertUtkast(deltakerId: UUID, utkast: UtkastRequest) {
        val opprinneligDeltaker = deltakerService.get(deltakerId).getOrThrow()

        val status = when (opprinneligDeltaker.status.type) {
            DeltakerStatus.Type.KLADD -> nyDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> opprinneligDeltaker.status
            else -> throw IllegalArgumentException(
                "Kan ikke upserte ukast for deltaker $deltakerId " +
                    "med status ${opprinneligDeltaker.status.type}," +
                    "status må være ${DeltakerStatus.Type.KLADD} eller ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}.",
            )
        }

        val oppdatertDeltaker = opprinneligDeltaker.copy(
            innhold = utkast.innhold,
            bakgrunnsinformasjon = utkast.bakgrunnsinformasjon,
            deltakelsesprosent = utkast.deltakelsesprosent,
            dagerPerUke = utkast.dagerPerUke,
            status = status,
            sistEndret = LocalDateTime.now(),
        )

        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(utkast.endretAv)
        val endretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(utkast.endretAvEnhet)

        val vedtak = vedtakRepository.getIkkeFattet(deltakerId)

        val oppdatertVedtak = oppdatertVedtak(
            original = vedtak,
            godkjentAvNav = utkast.godkjentAvNav,
            endretAv = endretAv,
            endretAvNavEnhet = endretAvNavEnhet,
            deltaker = oppdatertDeltaker,
        )
        vedtakRepository.upsert(oppdatertVedtak)

        deltakerService.upsertDeltaker(oppdatertDeltaker.copy(vedtaksinformasjon = oppdatertVedtak.tilVedtaksinformasjon()))

        log.info("Opprettet utkast for deltaker med id $deltakerId")
    }

    private fun oppdatertVedtak(
        original: Vedtak?,
        godkjentAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvNavEnhet: NavEnhet,
        deltaker: Deltaker,
        fattet: LocalDateTime? = null,
    ) = Vedtak(
        id = original?.id ?: UUID.randomUUID(),
        deltakerId = deltaker.id,
        fattet = fattet,
        gyldigTil = null,
        deltakerVedVedtak = deltaker.toDeltakerVedVedtak(),
        fattetAvNav = godkjentAvNav,
        opprettetAv = original?.opprettetAv ?: endretAv.id,
        opprettetAvEnhet = original?.opprettetAvEnhet ?: endretAvNavEnhet.id,
        opprettet = original?.opprettet ?: LocalDateTime.now(),
        sistEndretAv = endretAv.id,
        sistEndretAvEnhet = endretAvNavEnhet.id,
        sistEndret = LocalDateTime.now(),
    )

    private fun nyDeltakerKladd(
        navBruker: NavBruker,
        deltakerliste: Deltakerliste,
    ) = Deltaker(
        id = UUID.randomUUID(),
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        innhold = emptyList(),
        status = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
        vedtaksinformasjon = null,
        sistEndret = LocalDateTime.now(),
    )
}
