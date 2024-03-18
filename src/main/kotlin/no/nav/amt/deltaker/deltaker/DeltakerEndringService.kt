package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringService(
    private val repository: DeltakerEndringRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {

    fun getForDeltaker(deltakerId: UUID) = repository.getForDeltaker(deltakerId)

    suspend fun upsertEndring(deltaker: Deltaker, request: EndringRequest): Result<Deltaker> {
        val (endretDeltaker, endring) = when (request) {
            is BakgrunnsinformasjonRequest -> {
                val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon(request.bakgrunnsinformasjon)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is InnholdRequest -> {
                val endring = DeltakerEndring.Endring.EndreInnhold(request.innhold)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is DeltakelsesmengdeRequest -> {
                val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                    dagerPerUke = request.dagerPerUke?.toFloat(),
                )
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is StartdatoRequest -> {
                val endring = DeltakerEndring.Endring.EndreStartdato(startdato = request.startdato, sluttdato = request.sluttdato)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is SluttdatoRequest -> {
                val endring = DeltakerEndring.Endring.EndreSluttdato(request.sluttdato)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is SluttarsakRequest -> {
                val endring = DeltakerEndring.Endring.EndreSluttarsak(request.aarsak)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is ForlengDeltakelseRequest -> {
                val endring = DeltakerEndring.Endring.ForlengDeltakelse(request.sluttdato)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is IkkeAktuellRequest -> {
                val endring = DeltakerEndring.Endring.IkkeAktuell(request.aarsak)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is AvsluttDeltakelseRequest -> {
                val endring = DeltakerEndring.Endring.AvsluttDeltakelse(request.aarsak, request.sluttdato)
                Pair(endretDeltaker(deltaker, endring), endring)
            }
        }

        if (endretDeltaker.isSuccess) {
            val ansatt = navAnsattService.hentEllerOpprettNavAnsatt(request.endretAv)
            val enhet = navEnhetService.hentEllerOpprettNavEnhet(request.endretAvEnhet)

            repository.upsert(
                DeltakerEndring(
                    id = UUID.randomUUID(),
                    deltakerId = deltaker.id,
                    endring = endring,
                    endretAv = ansatt.id,
                    endretAvEnhet = enhet.id,
                    endret = LocalDateTime.now(),
                ),
            )
        }

        return endretDeltaker
    }

    private fun endretDeltaker(deltaker: Deltaker, endring: DeltakerEndring.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is DeltakerEndring.Endring.AvsluttDeltakelse -> {
                endreDeltaker(deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET || endring.sluttdato != deltaker.sluttdato || deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET, endring.aarsak.toDeltakerStatusAarsak()),
                    )
                }
            }
            is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> {
                endreDeltaker(deltaker.bakgrunnsinformasjon != endring.bakgrunnsinformasjon) {
                    deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon)
                }
            }

            is DeltakerEndring.Endring.EndreDeltakelsesmengde -> {
                endreDeltaker(deltaker.deltakelsesprosent != endring.deltakelsesprosent || deltaker.dagerPerUke != endring.dagerPerUke) {
                    deltaker.copy(
                        deltakelsesprosent = endring.deltakelsesprosent,
                        dagerPerUke = endring.dagerPerUke,
                    )
                }
            }
            is DeltakerEndring.Endring.EndreInnhold -> {
                endreDeltaker(deltaker.innhold != endring.innhold) {
                    deltaker.copy(innhold = endring.innhold)
                }
            }
            is DeltakerEndring.Endring.EndreSluttdato -> endreDeltaker(endring.sluttdato != deltaker.sluttdato) {
                deltaker.copy(sluttdato = endring.sluttdato)
            }
            is DeltakerEndring.Endring.EndreSluttarsak -> {
                endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
                    deltaker.copy(status = nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET, endring.aarsak.toDeltakerStatusAarsak()))
                }
            }
            is DeltakerEndring.Endring.EndreStartdato -> {
                endreDeltaker(deltaker.startdato != endring.startdato || deltaker.sluttdato != endring.sluttdato) {
                    deltaker.copy(
                        startdato = endring.startdato,
                        sluttdato = endring.sluttdato,
                        status = if (deltaker.status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (endring.startdato != null && !endring.startdato.isAfter(LocalDate.now()))) {
                            nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
                        } else {
                            deltaker.status
                        },
                    )
                }
            }
            is DeltakerEndring.Endring.ForlengDeltakelse -> {
                endreDeltaker(deltaker.sluttdato != endring.sluttdato) {
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = if (deltaker.status.type == DeltakerStatus.Type.HAR_SLUTTET && endring.sluttdato.isAfter(LocalDate.now())) {
                            nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
                        } else {
                            deltaker.status
                        },
                    )
                }
            }
            is DeltakerEndring.Endring.IkkeAktuell -> {
                endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }
        }
    }
}
