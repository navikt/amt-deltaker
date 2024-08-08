package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import kotliquery.queryOf
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.utils.database.Database

class EndringFraArrangorRepository {
    fun insert(endring: EndringFraArrangor) = Database.query {
        val sql =
            """
            insert into endring_fra_arrangor (id, deltaker_id, arrangor_ansatt_id, opprettet, endring)
            values (:id, :deltaker_id, :arrangor_ansatt_id, :opprettet, :endring)
            on conflict (id) do nothing
            """.trimIndent()
        val params = mapOf(
            "id" to endring.id,
            "deltaker_id" to endring.deltakerId,
            "arrangor_ansatt_id" to endring.opprettetAvArrangorAnsattId,
            "opprettet" to endring.opprettet,
            "endring" to endring.endring,
        )

        it.update(queryOf(sql, params))
    }
}
