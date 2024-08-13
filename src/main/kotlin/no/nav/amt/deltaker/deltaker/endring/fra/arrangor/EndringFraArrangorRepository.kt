package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class EndringFraArrangorRepository {
    companion object {
        fun rowMapper(row: Row, alias: String? = "ea"): EndringFraArrangor {
            val col = prefixColumn(alias)

            return EndringFraArrangor(
                id = row.uuid(col("id")),
                deltakerId = row.uuid(col("deltaker_id")),
                opprettetAvArrangorAnsattId = row.uuid(col("arrangor_ansatt_id")),
                opprettet = row.localDateTime(col("opprettet")),
                endring = objectMapper.readValue(row.string(col("endring"))),
            )
        }
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                ea.id as "ea.id",
                ea.deltaker_id as "ea.deltaker_id",
                ea.arrangor_ansatt_id as "ea.arrangor_ansatt_id",
                ea.opprettet as "ea.opprettet",
                ea.endring as "ea.endring"
            FROM endring_fra_arrangor ea 
            WHERE ea.deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(Companion::rowMapper).asList)
    }

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
            "endring" to toPGObject(endring.endring),
        )

        it.update(queryOf(sql, params))
    }
}
