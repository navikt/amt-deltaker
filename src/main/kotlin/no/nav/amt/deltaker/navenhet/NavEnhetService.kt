package no.nav.amt.deltaker.navenhet

import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavEnhet(enhetsnummer: String): NavEnhet {
        repository.get(enhetsnummer)?.let { return it }

        log.info("Fant ikke nav-enhet med nummer $enhetsnummer, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(enhetsnummer)
        return upsert(navEnhet)
    }

    suspend fun hentEllerOpprettNavEnhet(id: UUID): NavEnhet {
        repository.get(id)?.let { return it }

        log.info("Fant ikke nav-enhet med id $id, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(id)
        return upsert(navEnhet)
    }

    fun upsert(enhet: NavEnhet) = repository.upsert(enhet)
}
