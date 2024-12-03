package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import no.nav.amt.deltaker.deltaker.endring.getStatusEndretStartOgSluttdato
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.LoggerFactory
import java.util.UUID

class EndringFraArrangorService(
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val hendelseService: HendelseService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

        endretDeltaker.onFailure {
            log.warn("Endring fra arrangor for deltaker ${deltaker.id} medfører ingen endring")
        }

        return endretDeltaker
    }

    fun deleteForDeltaker(deltakerId: UUID) = endringFraArrangorRepository.deleteForDeltaker(deltakerId)

    private fun endretDeltaker(deltaker: Deltaker, endring: EndringFraArrangor.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is EndringFraArrangor.LeggTilOppstartsdato -> {
                endreDeltaker(deltaker.startdato != endring.startdato) {
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
