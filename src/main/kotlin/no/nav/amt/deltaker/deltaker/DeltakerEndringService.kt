package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
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
            is DeltakerEndring.Endring.AvsluttDeltakelse -> TODO()
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
            is DeltakerEndring.Endring.EndreSluttarsak -> TODO()
            is DeltakerEndring.Endring.EndreSluttdato -> TODO()
            is DeltakerEndring.Endring.EndreStartdato -> TODO()
            is DeltakerEndring.Endring.ForlengDeltakelse -> TODO()
            is DeltakerEndring.Endring.IkkeAktuell -> TODO()
        }
    }
}
