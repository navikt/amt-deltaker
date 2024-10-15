package no.nav.amt.deltaker.navbruker

import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import org.slf4j.LoggerFactory

class NavBrukerService(
    private val repository: NavBrukerRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
    private val navEnhetService: NavEnhetService,
    private val navAnsattService: NavAnsattService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun get(personident: String): Result<NavBruker> {
        val brukerResult = repository.get(personident)
        if (brukerResult.isSuccess) {
            // workaround for deltakere som ikke har fått lastet innsatsgruppe ennå
            val bruker = brukerResult.getOrThrow()
            if (bruker.innsatsgruppe != null) {
                return brukerResult
            }
        }

        val bruker = amtPersonServiceClient.hentNavBruker(personident)
        bruker.navEnhetId?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }
        bruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }

        log.info("Oppretter nav-bruker ${bruker.personId}")
        return repository.upsert(bruker)
    }
}
