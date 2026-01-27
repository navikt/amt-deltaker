package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class VedtakService(
    private val vedtakRepository: VedtakRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun avbrytVedtak(
        deltaker: Deltaker,
        avbruttAv: NavAnsatt,
        avbruttAvNavEnhet: NavEnhet,
    ): Vedtak? {
        val vedtak = hentIkkeFattetVedtak(deltaker.id) ?: return null

        return vedtakRepository.upsert(
            vedtak.copy(
                gyldigTil = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
                sistEndretAv = avbruttAv.id,
                sistEndretAvEnhet = avbruttAvNavEnhet.id,
            ),
        )
    }

    fun avbrytVedtakVedAvsluttetDeltakerliste(deltaker: Deltaker): Vedtak? {
        val vedtak = hentIkkeFattetVedtak(deltaker.id) ?: return null
        val avbruttVedtak = vedtak.copy(
            gyldigTil = LocalDateTime.now(),
        )

        return vedtakRepository.upsert(avbruttVedtak)
    }

    fun oppdaterEllerOpprettVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        skalFattesAvNav: Boolean,
    ): Vedtak = upsertOppdatertVedtak(
        eksisterendeVedtak = hentIkkeFattetVedtak(deltaker.id),
        fattetAvNav = skalFattesAvNav,
        endretAv = endretAv,
        endretAvEnhet = endretAvEnhet,
        deltaker = deltaker,
        fattetDato = if (skalFattesAvNav) LocalDateTime.now() else null,
    )

    fun navFattVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): Vedtaksutfall {
        when (val utfall = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.ManglerVedtakSomKanEndres -> {
                throw IllegalStateException("Deltaker ${deltaker.id} mangler et vedtak som kan fattes")
            }

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
                    fattetDato = LocalDateTime.now(),
                )
                return Vedtaksutfall.OK(oppdatertVedtak)
            }
        }
    }

    /**
     Kan bare brukes når deltaker selv godkjenner utkast.
     Hvis Nav fatter vedtaket må `oppdaterEllerOpprettVedtak` brukes.
     */
    fun innbyggerFattVedtak(deltaker: Deltaker): Vedtaksutfall {
        val ikkeFattetVedtak = when (val utfall = hentIkkeFattetVedtak(deltaker.id)) {
            is Vedtaksutfall.OK -> utfall.vedtak
            else -> return utfall
        }
        val vedtak = hentIkkeFattetVedtak(deltaker.id).getOrThrow()
        if (vedtak == null) return

        val fattetVedtak = ikkeFattetVedtak.copy(fattet = LocalDateTime.now())

        val upsertedVedtak = vedtakRepository.upsert(fattetVedtak)
        return Vedtaksutfall.OK(upsertedVedtak)
    }

    private fun hentIkkeFattetVedtak(deltakerId: UUID): Vedtak? =
        vedtakRepository.getForDeltaker(deltakerId).firstOrNull { it.fattet == null }

// TODO SJekk at vi ikke genererer ny vedtak fattet dato når noe endrer seg på utkast
    fun upsertOppdatertVedtak(
        fattetAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        deltaker: Deltaker,
        fattetDato: LocalDateTime?,
    ): Vedtak {
        val eksisterendeVedtak = hentIkkeFattetVedtak(deltaker.id)
        val oppdatertVedtak = opprettEllerOppdaterVedtak(
            original = eksisterendeVedtak,
            godkjentAvNav = fattetAvNav,
            endretAv = endretAv,
            endretAvNavEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattet = fattetDato,
        )

        return vedtakRepository.upsert(oppdatertVedtak)
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
