package no.nav.amt.deltaker.navansatt

import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import org.slf4j.LoggerFactory
import java.util.UUID

class NavAnsattService(
    private val repository: NavAnsattRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentEllerOpprettNavAnsatt(navIdent: String): NavAnsatt {
        repository.get(navIdent)?.let { return it }

        log.info("Fant ikke nav-ansatt med ident $navIdent, henter fra amt-person-service")
        val navAnsatt = amtPersonServiceClient.hentNavAnsatt(navIdent)
        return oppdaterNavAnsatt(navAnsatt)
    }

    suspend fun hentEllerOpprettNavAnsatt(id: UUID): NavAnsatt {
        repository.get(id)?.let { return it }

        log.info("Fant ikke nav-ansatt med id $id, henter fra amt-person-service")
        val navAnsatt = amtPersonServiceClient.hentNavAnsatt(id)
        return oppdaterNavAnsatt(navAnsatt)
    }

    fun oppdaterNavAnsatt(navAnsatt: NavAnsatt): NavAnsatt {
        return repository.upsert(navAnsatt)
    }

    fun slettNavAnsatt(navAnsattId: UUID) {
        repository.delete(navAnsattId)
    }

    fun hentAnsatte(veilederIdenter: List<String>) = repository.getMany(veilederIdenter).associateBy { it.navIdent }
}
