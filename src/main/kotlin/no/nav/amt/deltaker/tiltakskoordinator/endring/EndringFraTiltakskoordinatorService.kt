package no.nav.amt.deltaker.tiltakskoordinator.endring

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class EndringFraTiltakskoordinatorService(
    private val repository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
) {
    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    fun getForDeltaker(deltakerId: UUID) = repository.getForDeltaker(deltakerId)

    fun get(id: UUID) = repository.get(id)

    fun sjekkEndringUtfall(deltaker: Deltaker, endring: EndringFraTiltakskoordinator.Endring): Result<Deltaker> {
        fun createResult(gyldigEndring: Boolean, deltakerOnSuccess: () -> Deltaker) = if (gyldigEndring) {
            Result.success(deltakerOnSuccess())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        if (deltaker.status.type == DeltakerStatus.Type.FEILREGISTRERT) {
            return Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraTiltakskoordinator.SettPaaVenteliste -> {
                createResult(deltaker.status.type != DeltakerStatus.Type.VENTELISTE) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTELISTE),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }

            is EndringFraTiltakskoordinator.DelMedArrangor -> {
                createResult(deltaker.status.type == DeltakerStatus.Type.SOKT_INN && !deltaker.erManueltDeltMedArrangor) {
                    deltaker.copy(erManueltDeltMedArrangor = true)
                }
            }

            is EndringFraTiltakskoordinator.TildelPlass -> {
                checkNotNull(deltaker.deltakerliste.startDato) { "Kursdeltaker mangler startdato" }

                val startDateInFuture = deltaker.deltakerliste.startDato.isAfter(LocalDate.now())

                createResult(true) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                        startdato = deltaker.deltakerliste.startDato.takeIf { startDateInFuture },
                        sluttdato = deltaker.deltakerliste.sluttDato?.takeIf { startDateInFuture },
                    )
                }
            }

            is EndringFraTiltakskoordinator.Avslag -> createResult(
                deltaker.status.type in listOf(
                    DeltakerStatus.Type.SOKT_INN,
                    DeltakerStatus.Type.VURDERES,
                    DeltakerStatus.Type.VENTELISTE,
                    DeltakerStatus.Type.VENTER_PA_OPPSTART,
                ),
            ) {
                deltaker.copy(
                    status = nyDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                    startdato = null,
                    sluttdato = null,
                )
            }
        }
    }

    // Midlertidig workaround som lagrer historikk mens amt-tiltak er master for deltakere
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

    companion object {
        private fun EndringFraTiltakskoordinator.Avslag.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
            type = DeltakerStatus.Aarsak.Type.valueOf(this.type.name),
            beskrivelse = beskrivelse,
        )
    }
}
