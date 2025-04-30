package no.nav.amt.deltaker.tiltakskoordinator.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.nyDeltakerStatus
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class EndringFraTiltakskoordinatorService(
    private val repository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
) {
    suspend fun upsertEndring(
        deltakere: List<Deltaker>,
        endringsType: EndringFraTiltakskoordinator.Endring,
        endretAv: String,
    ): List<Result<Deltaker>> {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endretAv)

        val deltakereMedEndringMap = deltakere.associateWith { deltaker ->
            EndringFraTiltakskoordinator(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                endring = endringsType,
                endretAv = navAnsatt.id,
                endret = LocalDateTime.now(),
            )
        }

        val tentativtEndredeDeltakere = deltakereMedEndringMap
            .map { (deltaker, endring) -> sjekkEndringUtfall(deltaker, endring.endring) to endring }

        val gyldigeEndringer = tentativtEndredeDeltakere
            .filter { (res, _) -> res.isSuccess }
            .map { (_, endring) -> endring }

        repository.insert(gyldigeEndringer)

        return tentativtEndredeDeltakere.map { it.first }
    }

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    private fun sjekkEndringUtfall(deltaker: Deltaker, endring: EndringFraTiltakskoordinator.Endring): Result<Deltaker> {
        fun createResult(gyldigEndring: Boolean, deltakerOnSuccess: () -> Deltaker) = if (gyldigEndring) {
            Result.success(deltakerOnSuccess())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraTiltakskoordinator.SettPaaVenteliste -> {
                createResult(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
                    deltaker.copy(status = nyDeltakerStatus(DeltakerStatus.Type.VENTELISTE))
                }
            }
            is EndringFraTiltakskoordinator.DelMedArrangor -> {
                createResult(deltaker.status.type == DeltakerStatus.Type.SOKT_INN && !deltaker.erManueltDeltMedArrangor) {
                    deltaker.copy(erManueltDeltMedArrangor = true)
                }
            }
            is EndringFraTiltakskoordinator.TildelPlass -> {
                createResult(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                        startdato = getStartDatoForKursDeltaker(deltaker),
                        sluttdato = getSluttDatoForKursDeltaker(deltaker),
                    )
                }
            }
        }
    }

    private fun getStartDatoForKursDeltaker(deltaker: Deltaker): LocalDate? {
        if (deltaker.deltakerliste.startDato.isAfter(LocalDate.now())) {
            return deltaker.deltakerliste.startDato
        } else {
            return null
        }
    }

    private fun getSluttDatoForKursDeltaker(deltaker: Deltaker): LocalDate? {
        if (deltaker.deltakerliste.startDato.isAfter(LocalDate.now())) {
            return deltaker.deltakerliste.sluttDato
        } else {
            return null
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
