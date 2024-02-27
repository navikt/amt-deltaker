package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.KladdResponse
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(
        deltakerlisteId: UUID,
        personident: String,
        opprettetAv: String,
        opprettetAvEnhet: String,
    ): KladdResponse {
        val eksisterendeDeltaker = deltakerService
            .getDeltakelser(personident, deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            val sistEndretAv = navAnsattService.hentEllerOpprettNavAnsatt(eksisterendeDeltaker.sistEndretAv)
            val sistEndretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(eksisterendeDeltaker.sistEndretAvEnhet)
            return eksisterendeDeltaker.toKladdResponse(sistEndretAv, sistEndretAvNavEnhet)
        }

        val deltakerliste = deltakerlisteRepository.get(deltakerlisteId).getOrThrow()
        val navBruker = navBrukerService.get(personident).getOrThrow()
        val sistEndretAv = navAnsattService.hentEllerOpprettNavAnsatt(opprettetAv)
        val sistEndretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(opprettetAvEnhet)
        val deltaker = nyDeltakerKladd(navBruker, deltakerliste, sistEndretAv, sistEndretAvNavEnhet)

        deltakerService.lagreKladd(deltaker)

        return deltakerService.get(deltaker.id).getOrThrow()
            .toKladdResponse(sistEndretAv, sistEndretAvNavEnhet)
    }

    private fun nyDeltakerKladd(
        navBruker: NavBruker,
        deltakerliste: Deltakerliste,
        opprettetAv: NavAnsatt,
        opprettetAvEnhet: NavEnhet,
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
        sistEndretAv = opprettetAv.id,
        sistEndret = LocalDateTime.now(),
        sistEndretAvEnhet = opprettetAvEnhet.id,
        opprettet = LocalDateTime.now(),
    )
}
