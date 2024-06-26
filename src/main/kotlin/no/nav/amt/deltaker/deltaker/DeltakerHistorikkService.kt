package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.skalInkluderesIHistorikk
import java.time.LocalDate
import java.util.UUID

class DeltakerHistorikkService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val vedtakRepository: VedtakRepository,
    private val forslagRepository: ForslagRepository,
) {
    fun getForDeltaker(id: UUID): List<DeltakerHistorikk> {
        val deltakerHistorikk = deltakerEndringRepository.getForDeltaker(id).map { DeltakerHistorikk.Endring(it) }
        val vedtak = vedtakRepository.getForDeltaker(id).map { DeltakerHistorikk.Vedtak(it) }
        val forslag = forslagRepository.getForDeltaker(id).filter { it.skalInkluderesIHistorikk() }.map { DeltakerHistorikk.Forslag(it) }

        val historikk = deltakerHistorikk
            .plus(vedtak)
            .plus(forslag)
            .sortedByDescending {
                when (it) {
                    is DeltakerHistorikk.Endring -> it.endring.endret
                    is DeltakerHistorikk.Vedtak -> it.vedtak.sistEndret
                    is DeltakerHistorikk.Forslag -> it.forslag.sistEndret
                }
            }

        return historikk
    }

    fun getInnsoktDato(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate? {
        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        return vedtak.minByOrNull { it.opprettet }?.opprettet?.toLocalDate()
    }

    fun getForsteVedtakFattet(deltakerId: UUID): LocalDate? {
        val deltakerhistorikk = getForDeltaker(deltakerId)
        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }

        return forsteVedtak?.fattet?.toLocalDate()
    }
}
