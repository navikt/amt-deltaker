package no.nav.amt.deltaker.internal.konvertervedtak

import no.nav.amt.deltaker.deltaker.DeltakerService
import org.slf4j.LoggerFactory

class KonverterVedtakService(
    private val vedtakOldRepository: VedtakOldRepository,
    private val deltakerService: DeltakerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun konverterVedtak() {
        val alleVedtak = vedtakOldRepository.getAll()
        alleVedtak.forEach {
            val vedtak = it.toVedtak()
            vedtakOldRepository.updateDeltakerVedVedtak(vedtak)
            deltakerService.produserDeltaker(vedtak.deltakerId)
        }
        log.info("Konverterte ${alleVedtak.size} vedtak")
    }
}
