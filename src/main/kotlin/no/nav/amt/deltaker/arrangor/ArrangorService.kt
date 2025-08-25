package no.nav.amt.deltaker.arrangor

import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import java.util.UUID

class ArrangorService(
    private val repository: ArrangorRepository,
    private val amtArrangorClient: AmtArrangorClient,
) {
    suspend fun hentArrangor(orgnr: String): Arrangor {
        return repository.get(orgnr) ?: return opprettArrangor(orgnr)
    }

    fun hentArrangor(id: UUID): Arrangor? = repository.get(id)

    private suspend fun opprettArrangor(orgnr: String): Arrangor {
        val arrangor = amtArrangorClient.hentArrangor(orgnr)

        arrangor.overordnetArrangor?.let { repository.upsert(it) }
        repository.upsert(arrangor.toModel())

        return arrangor.toModel()
    }
}
