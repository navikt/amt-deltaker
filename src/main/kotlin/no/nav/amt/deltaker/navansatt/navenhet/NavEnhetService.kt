package no.nav.amt.deltaker.navansatt.navenhet

import no.nav.amt.deltaker.navansatt.AmtPersonServiceClient
import org.slf4j.LoggerFactory

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavEnhet(enhetsnummer: String): NavEnhet {
        repository.get(enhetsnummer)?.let { return it }

        log.info("Fant ikke nav-enhet med nummer $enhetsnummer, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(enhetsnummer)
        return repository.upsert(navEnhet)
    }
}
