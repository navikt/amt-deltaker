package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.KladdResponse
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class KladdService(
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
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

        deltakerService.lagreKladd(deltaker)

        return deltakerService.get(deltaker.id).getOrThrow()
            .toKladdResponse()
    }

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
