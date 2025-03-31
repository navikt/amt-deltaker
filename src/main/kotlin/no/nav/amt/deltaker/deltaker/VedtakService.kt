package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.lib.models.deltaker.Vedtak
import java.time.LocalDateTime
import java.util.UUID

class VedtakService(
    private val repository: VedtakRepository,
    private val hendelseService: HendelseService,
) {
    fun avbrytVedtak(
        deltaker: Deltaker,
        avbruttAv: NavAnsatt,
        avbruttAvNavEnhet: NavEnhet,
    ): Vedtak {
        val ikkeFattetVedtak = repository.getIkkeFattet(deltaker.id)
        require(ikkeFattetVedtak != null) {
            "Deltaker ${deltaker.id} har ikke et vedtak som kan avbrytes"
        }

        val avbruttVedtak = ikkeFattetVedtak.copy(
            gyldigTil = LocalDateTime.now(),
            sistEndret = LocalDateTime.now(),
            sistEndretAv = avbruttAv.id,
            sistEndretAvEnhet = avbruttAvNavEnhet.id,
        )

        repository.upsert(avbruttVedtak)

        return avbruttVedtak
    }

    fun avbrytVedtakVedAvsluttetDeltakerliste(deltaker: Deltaker): Vedtak {
        val ikkeFattetVedtak = repository.getIkkeFattet(deltaker.id)
        require(ikkeFattetVedtak != null) {
            "Deltaker ${deltaker.id} har ikke et vedtak som kan avbrytes"
        }

        val avbruttVedtak = ikkeFattetVedtak.copy(
            gyldigTil = LocalDateTime.now(),
        )

        repository.upsert(avbruttVedtak)

        return avbruttVedtak
    }

    fun oppdaterEllerOpprettVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        fattet: Boolean,
        fattetAvNav: Boolean = false,
    ): Vedtak {
        val eksisterendeVedtak = repository.getIkkeFattet(deltaker.id)

        val oppdatertVedtak = opprettEllerOppdaterVedtak(
            original = eksisterendeVedtak,
            godkjentAvNav = fattetAvNav,
            endretAv = endretAv,
            endretAvNavEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = if (fattet) LocalDateTime.now() else null,
        )
        repository.upsert(oppdatertVedtak)

        return oppdatertVedtak
    }

    fun fattVedtak(deltaker: Deltaker): Vedtak {
        val vedtak = repository.getIkkeFattet(deltaker.id)

        require(vedtak != null) {
            "Deltaker ${deltaker.id} har ikke et vedtak som kan fattes"
        }

        val fattetVedtak = vedtak.copy(fattet = LocalDateTime.now())

        repository.upsert(fattetVedtak)

        return fattetVedtak
    }

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    private fun opprettEllerOppdaterVedtak(
        original: Vedtak?,
        godkjentAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvNavEnhet: NavEnhet,
        deltaker: Deltaker,
        fattet: LocalDateTime? = null,
        gyldigTil: LocalDateTime? = null,
    ) = Vedtak(
        id = original?.id ?: UUID.randomUUID(),
        deltakerId = deltaker.id,
        fattet = fattet,
        gyldigTil = gyldigTil,
        deltakerVedVedtak = deltaker.toDeltakerVedVedtak(),
        fattetAvNav = godkjentAvNav,
        opprettetAv = original?.opprettetAv ?: endretAv.id,
        opprettetAvEnhet = original?.opprettetAvEnhet ?: endretAvNavEnhet.id,
        opprettet = original?.opprettet ?: LocalDateTime.now(),
        sistEndretAv = endretAv.id,
        sistEndretAvEnhet = endretAvNavEnhet.id,
        sistEndret = LocalDateTime.now(),
    )
}
