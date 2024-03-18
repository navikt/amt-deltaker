package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import java.time.LocalDateTime
import java.util.UUID

class VedtakService(
    private val repository: VedtakRepository,
) {
    fun avbrytVedtak(
        deltakerId: UUID,
        avbruttAv: NavAnsatt,
        avbruttAvNavEnhet: NavEnhet,
    ): Vedtak {
        val ikkeFattetVedtak = repository.getIkkeFattet(deltakerId)
        require(ikkeFattetVedtak != null) {
            "Deltaker $deltakerId har ikke et vedtak som kan avbrytes"
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

    fun oppdaterEllerOpprettVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        fattet: Boolean,
        fattetAvNav: Boolean = false,
    ): Vedtak {
        val vedtak = repository.getIkkeFattet(deltaker.id)

        val oppdatertVedtak = opprettEllerOppdaterVedtak(
            original = vedtak,
            godkjentAvNav = fattetAvNav,
            endretAv = endretAv,
            endretAvNavEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = if (fattet) LocalDateTime.now() else null,
        )
        repository.upsert(oppdatertVedtak)

        return oppdatertVedtak
    }

    fun fattVedtak(id: UUID): Vedtak {
        val vedtak = repository.get(id)

        require(vedtak != null && vedtak.fattet == null) {
            "Vedtak $id kan ikke fattes"
        }

        val fattetVedtak = vedtak.copy(fattet = LocalDateTime.now())

        repository.upsert(fattetVedtak)

        return fattetVedtak
    }

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
