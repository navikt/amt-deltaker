package no.nav.amt.deltaker.tiltakskoordinator.endring

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class EndringFraTiltakskoordinatorRepository {
    companion object {
        fun rowMapper(row: Row, alias: String? = "et"): EndringFraTiltakskoordinator {
            val col = prefixColumn(alias)

            return EndringFraTiltakskoordinator(
                id = row.uuid(col("id")),
                deltakerId = row.uuid(col("deltaker_id")),
                endretAv = row.uuid(col("nav_ansatt_id")),
                endretAvEnhet = row.uuid(col("nav_enhet_id")),
                endret = row.localDateTime(col("endret")),
                endring = objectMapper.readValue(row.string(col("endring"))),
            )
        }
    }

    fun insert(endringer: List<EndringFraTiltakskoordinator>, session: Session) {
        val sql =
            """
            insert into endring_fra_tiltakskoordinator (id, deltaker_id, nav_ansatt_id, nav_enhet_id, endret, endring) 
            values (:id, :deltaker_id, :nav_ansatt_id, :nav_enhet_id, :endret, :endring)
            """.trimIndent()
        val params = endringer.map { endring ->
            mapOf(
                "id" to endring.id,
                "deltaker_id" to endring.deltakerId,
                "nav_ansatt_id" to endring.endretAv,
                "nav_enhet_id" to endring.endretAvEnhet,
                "endret" to endring.endret,
                "endring" to toPGObject(endring.endring),
            )
        }

        session.batchPreparedNamedStatement(sql, params)
    }

    fun insert(endringer: List<EndringFraTiltakskoordinator>) = Database.query {
        insert(endringer, it)
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                et.id as "et.id",
                et.deltaker_id as "et.deltaker_id",
                et.nav_ansatt_id as "et.nav_ansatt_id",
                et.nav_enhet_id as "et.nav_enhet_id",
                et.endret as "et.endret",
                et.endring as "et.endring"
            FROM endring_fra_tiltakskoordinator et 
            WHERE et.deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(::rowMapper).asList)
    }

    fun get(id: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                et.id as "et.id",
                et.deltaker_id as "et.deltaker_id",
                et.nav_ansatt_id as "et.nav_ansatt_id",
                et.nav_enhet_id as "et.nav_enhet_id",
                et.endret as "et.endret",
                et.endring as "et.endring"
            FROM endring_fra_tiltakskoordinator et 
            WHERE et.id = :id;
            """.trimIndent(),
            mapOf("id" to id),
        )
        it.run(query.map(::rowMapper).asSingle)
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            DELETE FROM endring_fra_tiltakskoordinator 
            WHERE deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.update(query)
    }
}
