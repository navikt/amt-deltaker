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
    ): Vedtaksutfall {
        val ikkeFattetVedtak = repository.getIkkeFattet(deltaker.id) ?: return Vedtaksutfall.ManglerVedtakSomKanEndres

        val avbruttVedtak = ikkeFattetVedtak.copy(
            gyldigTil = LocalDateTime.now(),
            sistEndret = LocalDateTime.now(),
            sistEndretAv = avbruttAv.id,
            sistEndretAvEnhet = avbruttAvNavEnhet.id,
        )

        repository.upsert(avbruttVedtak)

        return Vedtaksutfall.OK(avbruttVedtak)
    }

    fun avbrytVedtakVedAvsluttetDeltakerliste(deltaker: Deltaker): Vedtaksutfall {
        val ikkeFattetVedtak = repository.getIkkeFattet(deltaker.id) ?: return Vedtaksutfall.ManglerVedtakSomKanEndres

        val avbruttVedtak = ikkeFattetVedtak.copy(
            gyldigTil = LocalDateTime.now(),
        )

        repository.upsert(avbruttVedtak)

        return Vedtaksutfall.OK(avbruttVedtak)
    }

    fun oppdaterEllerOpprettVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        fattet: Boolean,
        fattetAvNav: Boolean = false,
    ): Vedtak {
        val eksisterendeVedtak = repository.getIkkeFattet(deltaker.id)

        return upsertOppdatertVedtak(
            eksisterendeVedtak = eksisterendeVedtak,
            fattetAvNav = fattetAvNav,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = fattet,
        )
    }

    fun navFattVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): Vedtaksutfall {
        val vedtak = repository.getForDeltaker(deltaker.id)

        if (!vedtak.any { it.gyldigTil == null }) {
            return Vedtaksutfall.ManglerVedtakSomKanEndres
        }

        val ikkeFattetVedtak = vedtak.firstOrNull { it.fattet == null } ?: return Vedtaksutfall.VedtakAlleredeFattet

        val oppdatertVedtak = upsertOppdatertVedtak(
            eksisterendeVedtak = ikkeFattetVedtak,
            fattetAvNav = true,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = true,
        )

        return Vedtaksutfall.OK(oppdatertVedtak)
    }

    /**
     Kan bare brukes når deltaker selv godkjenner utkast.
     Hvis Nav fatter vedtaket må `oppdaterEllerOpprettVedtak` brukes.
     */
    fun innbyggerFattVedtak(deltaker: Deltaker): Vedtaksutfall {
        val vedtak = repository.getForDeltaker(deltaker.id)

        if (!vedtak.any { it.gyldigTil == null }) {
            return Vedtaksutfall.ManglerVedtakSomKanEndres
        }

        val ikkeFattetVedtak = vedtak.firstOrNull { it.fattet == null } ?: return Vedtaksutfall.VedtakAlleredeFattet

        val fattetVedtak = ikkeFattetVedtak.copy(fattet = LocalDateTime.now())

        repository.upsert(fattetVedtak)

        return Vedtaksutfall.OK(fattetVedtak)
    }

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    private fun upsertOppdatertVedtak(
        eksisterendeVedtak: Vedtak?,
        fattetAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        deltaker: Deltaker,
        fattet: Boolean,
    ): Vedtak {
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

sealed interface Vedtaksutfall {
    data class OK(
        val vedtak: Vedtak,
    ) : Vedtaksutfall

    data object ManglerVedtakSomKanEndres : Vedtaksutfall

    data object VedtakAlleredeFattet : Vedtaksutfall
}

fun Vedtaksutfall.getVedtakOrThrow(details: String = ""): Vedtak = when (this) {
    is Vedtaksutfall.OK -> vedtak
    else -> throw toException(details)
}

private fun Vedtaksutfall.toException(details: String = ""): Exception = when (this) {
    Vedtaksutfall.ManglerVedtakSomKanEndres ->
        IllegalArgumentException("Deltaker har ikke vedtak som kan endres $details")
    Vedtaksutfall.VedtakAlleredeFattet ->
        IllegalArgumentException("Deltaker har allerede et fattet vedtak $details")
    is Vedtaksutfall.OK ->
        IllegalStateException("Tried to convert successful result to exception: $vedtak")
}
