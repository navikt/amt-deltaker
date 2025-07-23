package no.nav.amt.deltaker.deltaker

import kotliquery.TransactionalSession
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navenhet.NavEnhet
import no.nav.amt.lib.models.deltaker.Vedtak
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class VedtakService(
    private val repository: VedtakRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun avbrytVedtak(
        deltaker: Deltaker,
        avbruttAv: NavAnsatt,
        avbruttAvNavEnhet: NavEnhet,
    ): Vedtaksutfall {
        val ikkeFattetVedtak = when (val it = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.OK -> it.vedtak
            Vedtaksutfall.ManglerVedtakSomKanEndres,
            Vedtaksutfall.VedtakAlleredeFattet,
            -> return it
        }

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
        val ikkeFattetVedtak = when (val it = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.OK -> it.vedtak
            Vedtaksutfall.ManglerVedtakSomKanEndres,
            Vedtaksutfall.VedtakAlleredeFattet,
            -> return it
        }

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
    ): Vedtaksutfall {
        val eksisterendeVedtak = when (val it = hentIkkeFattetVedtak(deltaker.id)) {
            Vedtaksutfall.ManglerVedtakSomKanEndres -> null
            is Vedtaksutfall.OK -> it.vedtak
            Vedtaksutfall.VedtakAlleredeFattet -> return it
        }

        return Vedtaksutfall.OK(
            upsertOppdatertVedtak(
                eksisterendeVedtak = eksisterendeVedtak,
                endretAv = endretAv,
                endretAvEnhet = endretAvEnhet,
                deltaker = deltaker,
                fattet = false,
                fattetAvNav = false,
            ),
        )
    }

    fun navFattVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        session: TransactionalSession? = null,
    ): Vedtaksutfall {
        when (val utfall = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.ManglerVedtakSomKanEndres ->
                throw IllegalStateException("Deltaker ${deltaker.id} mangler et vedtak som kan fattes")
            is Vedtaksutfall.VedtakAlleredeFattet -> {
                log.info("Vedtak allerede fattet for deltaker ${deltaker.id}, fatter ikke nytt vedtak")
                return utfall
            }
            is Vedtaksutfall.OK -> {
                log.info("Fatter hovedvedtak for deltaker ${deltaker.id}")
                val oppdatertVedtak = upsertOppdatertVedtak(
                    eksisterendeVedtak = utfall.vedtak,
                    fattetAvNav = true,
                    endretAv = endretAv,
                    endretAvEnhet = endretAvEnhet,
                    deltaker = deltaker,
                    fattet = true,
                    session = session,
                )
                return Vedtaksutfall.OK(oppdatertVedtak)
            }
        }
    }

    fun navFattEksisterendeEllerOpprettVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): Vedtaksutfall {
        val ikkeFattetVedtak = when (val it = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.OK -> it.vedtak
            Vedtaksutfall.ManglerVedtakSomKanEndres -> null
            Vedtaksutfall.VedtakAlleredeFattet -> return it
        }

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
        val ikkeFattetVedtak = when (val it = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.OK -> it.vedtak
            else -> return it
        }

        val fattetVedtak = ikkeFattetVedtak.copy(fattet = LocalDateTime.now())

        repository.upsert(fattetVedtak)

        return Vedtaksutfall.OK(fattetVedtak)
    }

    fun hentIkkeFattetVedtak(deltakerId: UUID): Vedtaksutfall {
        val vedtak = repository.getForDeltaker(deltakerId)

        if (!vedtak.any { it.gyldigTil == null }) {
            return Vedtaksutfall.ManglerVedtakSomKanEndres
        }

        return vedtak.firstOrNull { it.fattet == null }?.let { Vedtaksutfall.OK(it) }
            ?: Vedtaksutfall.VedtakAlleredeFattet
    }

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    fun upsertOppdatertVedtak(
        eksisterendeVedtak: Vedtak?,
        fattetAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        deltaker: Deltaker,
        fattet: Boolean,
        session: TransactionalSession? = null,
    ): Vedtak {
        val oppdatertVedtak = opprettEllerOppdaterVedtak(
            original = eksisterendeVedtak,
            godkjentAvNav = fattetAvNav,
            endretAv = endretAv,
            endretAvNavEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = if (fattet) LocalDateTime.now() else null,
        )
        if (session != null) {
            repository.upsert(oppdatertVedtak, session)
        } else {
            repository.upsert(oppdatertVedtak)
        }

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

fun Vedtaksutfall.getVedtakOrThrow(msg: String = ""): Vedtak = when (this) {
    is Vedtaksutfall.OK -> vedtak
    else -> throw toException(msg)
}

private fun Vedtaksutfall.toException(detaljer: String = ""): Exception = when (this) {
    Vedtaksutfall.ManglerVedtakSomKanEndres ->
        IllegalArgumentException("Deltaker har ikke vedtak som kan endres $detaljer")

    Vedtaksutfall.VedtakAlleredeFattet ->
        IllegalArgumentException("Deltaker har allerede et fattet vedtak $detaljer")

    is Vedtaksutfall.OK ->
        IllegalStateException("Prøvde å behandle OK utfall som et exception: ${vedtak.id}")
}
