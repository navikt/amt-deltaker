package no.nav.amt.deltaker.deltaker

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
    ): Deltaker {
        val eksisterendeDeltaker = deltakerService
            .getDeltakelser(personident, deltakerlisteId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker
        }

        val deltakerliste = deltakerlisteRepository.get(deltakerlisteId).getOrThrow()
        val navBruker = navBrukerService.get(personident).getOrThrow()
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(opprettetAv)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(opprettetAvEnhet)
        val deltaker = nyDeltakerKladd(navBruker, deltakerliste, navAnsatt, navEnhet)

        deltakerService.lagreKladd(deltaker)

        return deltakerService.get(deltaker.id).getOrThrow()
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
        sistEndretAv = opprettetAv,
        sistEndret = LocalDateTime.now(),
        sistEndretAvEnhet = opprettetAvEnhet,
        opprettet = LocalDateTime.now(),
    )
}
