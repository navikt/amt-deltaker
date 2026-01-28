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
    ): Vedtak {
        val vedtak = hentIkkeFattetVedtak(deltaker.id)

        return vedtakRepository.upsert(
            vedtak.copy(
                gyldigTil = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
                sistEndretAv = avbruttAv.id,
                sistEndretAvEnhet = avbruttAvNavEnhet.id,
            ),
        )
    }

    fun avbrytVedtakVedAvsluttetDeltakerliste(deltaker: Deltaker): Vedtak {
        val vedtak = hentIkkeFattetVedtak(deltaker.id)
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
    ): Vedtak {
        hentIkkeFattetVedtak(deltaker.id)

        log.info("Fatter hovedvedtak for deltaker ${deltaker.id}")
        return upsertOppdatertVedtak(
            fattetAvNav = true,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = deltaker,
            fattetDato = LocalDateTime.now(),
        )
    }

    /**
     Kan bare brukes når deltaker selv godkjenner utkast.
     Hvis Nav fatter vedtaket må `oppdaterEllerOpprettVedtak` brukes.
     */
    fun innbyggerFattVedtak(deltaker: Deltaker): Vedtak {
        val ikkeFattetVedtak = hentIkkeFattetVedtak(deltaker.id)
        val fattetVedtak = ikkeFattetVedtak.copy(fattet = LocalDateTime.now())

        return vedtakRepository.upsert(fattetVedtak)
    }

    private fun hentIkkeFattetVedtak(deltakerId: UUID): Vedtak {
        val vedtaksliste = vedtakRepository.getForDeltaker(deltakerId)

        if (vedtaksliste.none { it.gyldigTil == null }) {
            throw IllegalArgumentException("Deltaker-id $deltakerId har ikke vedtak som kan endres")
        }

        return vedtakRepository.getForDeltaker(deltakerId).firstOrNull { it.fattet == null }
            ?: throw IllegalArgumentException("Deltaker-id $deltakerId har allerede et fattet vedtak")
    }

    // TODO Sjekk at vi ikke genererer ny vedtak fattet dato når noe endrer seg på utkast
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
