package no.nav.amt.deltaker.job

import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.harIkkeStartet
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerStatusOppdateringService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun oppdaterDeltakerStatuser() {
        oppdaterTilAvsluttendeStatus()
        oppdaterStatusTilDeltar()
    }

    suspend fun avsluttDeltakelserForAvbruttDeltakerliste(deltakerlisteId: UUID) {
        val deltakerePaAvbruttDeltakerliste = deltakerService.getDeltakereForDeltakerliste(deltakerlisteId)
            .filter { it.status.type != DeltakerStatus.Type.KLADD }

        oppdaterTilAvsluttendeStatus(deltakerePaAvbruttDeltakerliste)
    }

    private suspend fun oppdaterTilAvsluttendeStatus() {
        val deltakereSomSkalHaAvsluttendeStatus =
            deltakerRepository.skalHaAvsluttendeStatus().plus(deltakerRepository.deltarPaAvsluttetDeltakerliste())
                .distinct()
        oppdaterTilAvsluttendeStatus(deltakereSomSkalHaAvsluttendeStatus)
    }

    private suspend fun oppdaterTilAvsluttendeStatus(deltakereSomSkalHaAvsluttendeStatus: List<Deltaker>) {
        val skalBliAvbruttUtkast = deltakereSomSkalHaAvsluttendeStatus.filter { it.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING }
        val skalBliIkkeAktuell = deltakereSomSkalHaAvsluttendeStatus.filter { it.status.harIkkeStartet() }
        val skalBliAvbrutt = deltakereSomSkalHaAvsluttendeStatus
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { sluttetForTidlig(it) }

        val skalBliHarSluttet = deltakereSomSkalHaAvsluttendeStatus
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { !it.deltarPaKurs() }

        val skalBliFullfort = deltakereSomSkalHaAvsluttendeStatus
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { it.deltarPaKurs() && !sluttetForTidlig(it) }

        skalBliAvbruttUtkast.forEach {
            oppdaterDeltaker(
                it.copy(
                    status = DeltakerStatus(
                        id = UUID.randomUUID(),
                        type = DeltakerStatus.Type.AVBRUTT_UTKAST,
                        aarsak = null,
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                        opprettet = LocalDateTime.now(),
                    ),
                ),
            )
        }

        skalBliIkkeAktuell.forEach {
            oppdaterDeltaker(
                it.copy(
                    status = DeltakerStatus(
                        id = UUID.randomUUID(),
                        type = DeltakerStatus.Type.IKKE_AKTUELL,
                        aarsak = getSluttarsak(it),
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                        opprettet = LocalDateTime.now(),
                    ),
                ),
            )
        }
        log.info("Endret status til IKKE AKTUELL for ${skalBliIkkeAktuell.size}")

        skalBliAvbrutt.forEach {
            oppdaterDeltaker(
                it.copy(
                    status = DeltakerStatus(
                        id = UUID.randomUUID(),
                        type = DeltakerStatus.Type.AVBRUTT,
                        aarsak = getSluttarsak(it),
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                        opprettet = LocalDateTime.now(),
                    ),
                    sluttdato = getOppdatertSluttdato(it),
                ),
            )
        }
        log.info("Endret status til AVBRUTT for ${skalBliAvbrutt.size}")

        skalBliFullfort.forEach {
            oppdaterDeltaker(
                it.copy(
                    status = DeltakerStatus(
                        id = UUID.randomUUID(),
                        type = DeltakerStatus.Type.FULLFORT,
                        aarsak = null,
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                        opprettet = LocalDateTime.now(),
                    ),
                    sluttdato = getOppdatertSluttdato(it),
                ),
            )
        }
        log.info("Endret status til FULLFØRT for ${skalBliFullfort.size}")

        oppdaterStatusTilHarSluttet(skalBliHarSluttet)
    }

    private suspend fun oppdaterStatusTilDeltar() {
        val deltakere = deltakerRepository.skalHaStatusDeltar().distinct()

        deltakere.forEach {
            oppdaterDeltaker(
                it.copy(
                    status = DeltakerStatus(
                        id = UUID.randomUUID(),
                        type = DeltakerStatus.Type.DELTAR,
                        aarsak = null,
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                        opprettet = LocalDateTime.now(),
                    ),
                ),
            )
        }
        log.info("Endret status til DELTAR for ${deltakere.size}")
    }

    private suspend fun oppdaterStatusTilHarSluttet(skalBliHarSluttet: List<Deltaker>) {
        skalBliHarSluttet.forEach {
            val fremtidigStatus = deltakerRepository.getDeltakerStatuser(it.id).firstOrNull { status ->
                status.gyldigTil == null && !status.gyldigFra.toLocalDate().isAfter(
                    LocalDate.now(),
                ) && status.type == DeltakerStatus.Type.HAR_SLUTTET
            }
            if (fremtidigStatus != null) {
                oppdaterDeltaker(
                    it.copy(
                        status = fremtidigStatus,
                        sluttdato = getOppdatertSluttdato(it),
                    ),
                )
            } else {
                oppdaterDeltaker(
                    it.copy(
                        status = DeltakerStatus(
                            id = UUID.randomUUID(),
                            type = DeltakerStatus.Type.HAR_SLUTTET,
                            aarsak = getSluttarsak(it),
                            gyldigFra = LocalDateTime.now(),
                            gyldigTil = null,
                            opprettet = LocalDateTime.now(),
                        ),
                        sluttdato = getOppdatertSluttdato(it),
                    ),
                )
            }
        }
        log.info("Endret status til HAR SLUTTET for ${skalBliHarSluttet.size}")
    }

    private suspend fun oppdaterDeltaker(deltaker: Deltaker) {
        deltakerService.upsertDeltaker(deltaker)
        log.info("Oppdatert status for deltaker med id ${deltaker.id}")
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

    private fun getOppdatertSluttdato(deltaker: Deltaker): LocalDate? {
        return if (deltaker.sluttdato == null || deltaker.sluttdato.isAfter(LocalDate.now())) {
            if (deltaker.deltakerliste.sluttDato != null && !deltaker.deltakerliste.sluttDato.isAfter(LocalDate.now())) {
                deltaker.deltakerliste.sluttDato
            } else {
                LocalDate.now()
            }
        } else {
            deltaker.sluttdato
        }
    }

    private fun getSluttarsak(deltaker: Deltaker): DeltakerStatus.Aarsak? {
        return if (deltaker.deltakerliste.erAvlystEllerAvbrutt()) {
            DeltakerStatus.Aarsak(
                type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
                beskrivelse = null,
            )
        } else {
            null
        }
    }
}
