package no.nav.amt.deltaker.tiltakskoordinator.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.models.tiltakskoordinator.requests.EndringFraTiltakskoordinatorRequest
import java.time.LocalDateTime
import java.util.UUID

class EndringFraTiltakskoordinatorService(
    private val repository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
) {
    suspend fun endre(deltakere: List<Deltaker>, request: EndringFraTiltakskoordinatorRequest): List<Result<Deltaker>> {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(request.endretAv)

        val endringer = deltakere.associateWith { deltaker ->
            val endring = when (request) {
                is DelMedArrangorRequest -> EndringFraTiltakskoordinator.DelMedArrangor
            }

            EndringFraTiltakskoordinator(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                endring = endring,
                endretAv = navAnsatt.id,
                endret = LocalDateTime.now(),
            )
        }

        val resultat = endringer.map { (deltaker, endring) -> endretDeltaker(deltaker, endring.endring) to endring }

        val gyldigeEndringer = resultat
            .filter { (res, _) -> res.isSuccess }
            .map { (_, endring) -> endring }

        repository.insert(gyldigeEndringer)

        return resultat.map { it.first }
    }

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    private fun endretDeltaker(deltaker: Deltaker, endring: EndringFraTiltakskoordinator.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraTiltakskoordinator.DelMedArrangor -> {
                endreDeltaker(deltaker.status.type == DeltakerStatus.Type.SOKT_INN && !deltaker.erManueltDeltMedArrangor) {
                    deltaker.copy(erManueltDeltMedArrangor = true)
                }
            }
        }
    }

    // Midlertidig workaround som lagrer historikk mens amt-tiltak er master for deltakere
    suspend fun insertDelMedArrangor(deltakere: List<Deltaker>, endretAv: String) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endretAv)
        val endringer = deltakere.map {
            EndringFraTiltakskoordinator(
                id = UUID.randomUUID(),
                deltakerId = it.id,
                endring = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAv = navAnsatt.id,
                endret = LocalDateTime.now(),
            )
        }

        repository.insert(endringer)
    }
}
