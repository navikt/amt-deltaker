package no.nav.amt.deltaker.tiltakskoordinator.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDateTime
import java.util.UUID

class EndringFraTiltakskoordinatorService(
    private val repository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
) {
    // Midlertidig workaround som lagrer historikk mens amt-tiltak er master for deltakere
    @Suppress("unused")
    suspend fun insertDelMedArrangor(
        deltakere: List<Deltaker>,
        endretAv: String,
        endretAvEnhet: NavEnhet,
    ) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endretAv)
        val endringer = deltakere.map {
            EndringFraTiltakskoordinator(
                id = UUID.randomUUID(),
                deltakerId = it.id,
                endring = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAv = navAnsatt.id,
                endretAvEnhet = endretAvEnhet.id,
                endret = LocalDateTime.now(),
            )
        }

        repository.insert(endringer)
    }
}
