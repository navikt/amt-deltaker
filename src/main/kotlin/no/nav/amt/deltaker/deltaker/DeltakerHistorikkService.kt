package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.util.UUID

class DeltakerHistorikkService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val vedtakRepository: VedtakRepository,
    private val forslagRepository: ForslagRepository,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
) {
    fun getForDeltaker(id: UUID): List<DeltakerHistorikk> {
        val endringer = deltakerEndringRepository.getForDeltaker(id).map { DeltakerHistorikk.Endring(it) }
        val vedtak = vedtakRepository.getForDeltaker(id).map { DeltakerHistorikk.Vedtak(it) }
        val forslag = forslagRepository.getForDeltaker(id).filter { it.skalInkluderesIHistorikk() }.map { DeltakerHistorikk.Forslag(it) }
        val endringerFraArrangor = endringFraArrangorRepository.getForDeltaker(id).map { DeltakerHistorikk.EndringFraArrangor(it) }
        val importertFraArena = importertFraArenaRepository.getForDeltaker(id)
            ?.let { listOf(DeltakerHistorikk.ImportertFraArena(it)) }
            ?: emptyList()

        val historikk = endringer
            .plus(vedtak)
            .plus(importertFraArena)
            .plus(forslag)
            .plus(endringerFraArrangor)
            .sortedByDescending {
                when (it) {
                    is DeltakerHistorikk.Endring -> it.endring.endret
                    is DeltakerHistorikk.Vedtak -> it.vedtak.sistEndret
                    is DeltakerHistorikk.Forslag -> it.forslag.sistEndret
                    is DeltakerHistorikk.EndringFraArrangor -> it.endringFraArrangor.opprettet
                    is DeltakerHistorikk.ImportertFraArena -> it.importertFraArena.importertDato
                }
            }

        return historikk
    }

    fun getInnsoktDato(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate? {
        getInnsoktDatoFraImportertDeltaker(deltakerhistorikk)?.let { return it }

        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        return vedtak.minByOrNull { it.opprettet }?.opprettet?.toLocalDate()
    }

    fun getForsteVedtakFattet(deltakerId: UUID): LocalDate? {
        val deltakerhistorikk = getForDeltaker(deltakerId)
        getInnsoktDatoFraImportertDeltaker(deltakerhistorikk)?.let { return it }

        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }

        return forsteVedtak?.fattet?.toLocalDate()
    }

    fun getInnsoktDatoFraImportertDeltaker(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate? {
        deltakerhistorikk.filterIsInstance<DeltakerHistorikk.ImportertFraArena>().firstOrNull()
            ?.let { return it.importertFraArena.deltakerVedImport.innsoktDato }
        return null
    }
}

fun Forslag.skalInkluderesIHistorikk() = when (this.status) {
    is Forslag.Status.Avvist,
    is Forslag.Status.Erstattet,
    is Forslag.Status.Tilbakekalt,
    -> true

    is Forslag.Status.Godkjent,
    Forslag.Status.VenterPaSvar,
    -> false
}
