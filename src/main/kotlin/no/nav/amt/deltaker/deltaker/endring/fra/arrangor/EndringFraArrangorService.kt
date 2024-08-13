package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import no.nav.amt.deltaker.deltaker.getStatusEndretStartOgSluttdato
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor

class EndringFraArrangorService(
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val hendelseService: HendelseService,
) {
    suspend fun insertEndring(deltaker: Deltaker, endring: EndringFraArrangor): Result<Deltaker> {
        val endretDeltaker = when (endring.endring) {
            is EndringFraArrangor.LeggTilOppstartsdato -> {
                endretDeltaker(deltaker, endring.endring)
            }
        }

        endretDeltaker.onSuccess {
            endringFraArrangorRepository.insert(endring)
            hendelseService.hendelseForEndringFraArrangor(endring, it)
        }

        return endretDeltaker
    }

    private fun endretDeltaker(deltaker: Deltaker, endring: EndringFraArrangor.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraArrangor.LeggTilOppstartsdato -> {
                endreDeltaker(deltaker.startdato != endring.startdato && deltaker.sluttdato != endring.sluttdato) {
                    val oppdatertStatus = deltaker.getStatusEndretStartOgSluttdato(
                        startdato = endring.startdato,
                        sluttdato = endring.sluttdato,
                    )
                    deltaker.copy(
                        startdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else endring.startdato,
                        sluttdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else endring.sluttdato,
                        status = oppdatertStatus,
                    )
                }
            }
        }
    }
}
