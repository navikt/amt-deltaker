package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.harIkkeStartet
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerStatusProgresjon {
    private val log = LoggerFactory.getLogger(javaClass)

    fun tilAvsluttendeStatusOgDatoer(deltakere: List<Deltaker>, fremtidigStatusProvider: (Deltaker) -> DeltakerStatus?): List<Deltaker> =
        listOf(
            avbrytUtkast(deltakere),
            ikkeAktuell(deltakere),
            avbryt(deltakere),
            harSluttet(deltakere, fremtidigStatusProvider),
            fullfor(deltakere),
        ).flatten()

    fun tilDeltar(deltakere: List<Deltaker>): List<Deltaker> = deltakere
        .map { it.medNyStatus(DeltakerStatus.Type.DELTAR) }
        .also { log.info("Endret status til DELTAR for ${deltakere.size}") }

    private fun fullfor(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliFullfort = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { it.deltarPaKurs() && !sluttetForTidlig(it) }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.FULLFORT, getSluttarsak(it))
                    .medNySluttdato(getOppdatertSluttdato(it))
            }
        log.info("Endret status til FULLFØRT for ${skalBliFullfort.size}")

        return skalBliFullfort
    }

    private fun avbryt(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliAvbrutt = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { sluttetForTidlig(it) }
            .map { it.medNyStatus(DeltakerStatus.Type.AVBRUTT, getSluttarsak(it)).medNySluttdato(getOppdatertSluttdato(it)) }
        log.info("Endret status til AVBRUTT for ${skalBliAvbrutt.size}")

        return skalBliAvbrutt
    }

    private fun harSluttet(deltakere: List<Deltaker>, fremtidigStatusProvider: (Deltaker) -> DeltakerStatus?): List<Deltaker> {
        val skalBliHarSluttet = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { !it.deltarPaKurs() }
            .map {
                val fremtidigStatus = fremtidigStatusProvider(it)
                if (fremtidigStatus != null) {
                    it.copy(status = fremtidigStatus, sluttdato = getOppdatertSluttdato(it))
                } else {
                    it
                        .medNyStatus(DeltakerStatus.Type.HAR_SLUTTET, getSluttarsak(it))
                        .medNySluttdato(getOppdatertSluttdato(it))
                }
            }

        log.info("Endret status til HAR SLUTTET for ${skalBliHarSluttet.size}")

        return skalBliHarSluttet
    }

    private fun ikkeAktuell(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliIkkeAktuell = deltakere
            .filter { it.status.harIkkeStartet() }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.IKKE_AKTUELL, getSluttarsak(it))
                    .medNySluttdato(null)
                    .medNyStartdato(null)
            }
        log.info("Endret status til IKKE AKTUELL for ${skalBliIkkeAktuell.size}")

        return skalBliIkkeAktuell
    }

    private fun avbrytUtkast(deltakere: List<Deltaker>): List<Deltaker> {
        val utkastSomSkalAvbrytes = deltakere
            .filter { it.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING }
            .map {
                it.medNyStatus(
                    DeltakerStatus.Type.AVBRUTT_UTKAST,
                    // Årsak skal settes selv om gjennomføringen blir avsluttet normalt
                    DeltakerStatus.Aarsak(
                        type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
                        beskrivelse = null,
                    ),
                )
            }

        return utkastSomSkalAvbrytes
    }

    private fun getOppdatertSluttdato(deltaker: Deltaker): LocalDate? =
        if (deltaker.sluttdato == null || deltaker.sluttdato.isAfter(LocalDate.now())) {
            if (deltaker.deltakerliste.sluttDato != null && !deltaker.deltakerliste.sluttDato.isAfter(LocalDate.now())) {
                deltaker.deltakerliste.sluttDato
            } else {
                LocalDate.now()
            }
        } else {
            deltaker.sluttdato
        }

    private fun getSluttarsak(deltaker: Deltaker): DeltakerStatus.Aarsak? = if (deltaker.deltakerliste.erAvlystEllerAvbrutt()) {
        DeltakerStatus.Aarsak(
            type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
            beskrivelse = null,
        )
    } else {
        null
    }

    private fun sluttetForTidlig(deltaker: Deltaker): Boolean {
        if (!deltaker.deltarPaKurs()) {
            return false
        }
        deltaker.deltakerliste.sluttDato?.let {
            return deltaker.sluttdato?.isBefore(it) == true
        }
        return false
    }

    private fun Deltaker.medNyStatus(status: DeltakerStatus.Type, aarsak: DeltakerStatus.Aarsak? = null) = this.copy(
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = status,
            aarsak = aarsak,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
    )

    private fun Deltaker.medNySluttdato(sluttdato: LocalDate?) = this.copy(sluttdato = sluttdato)

    private fun Deltaker.medNyStartdato(startdato: LocalDate?) = this.copy(startdato = startdato)
}
