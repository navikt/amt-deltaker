package no.nav.amt.deltaker.arrangor

import no.nav.amt.deltaker.apiclients.arrangor.AmtArrangorClient
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

        if (arrangor.overordnetArrangor != null) repository.upsert(arrangor.overordnetArrangor)
        repository.upsert(arrangor.toModel())

        return arrangor.toModel()
    }
}
