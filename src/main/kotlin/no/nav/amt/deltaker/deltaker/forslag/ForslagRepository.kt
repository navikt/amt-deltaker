package no.nav.amt.deltaker.deltaker.forslag

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.util.UUID

class ForslagRepository {
    companion object {
        fun rowMapper(row: Row, alias: String? = "f"): Forslag {
            val col = prefixColumn(alias)

            return Forslag(
                id = row.uuid(col("id")),
                deltakerId = row.uuid(col("deltaker_id")),
                opprettetAvArrangorAnsattId = row.uuid(col("arrangoransatt_id")),
                opprettet = row.localDateTime(col("opprettet")),
                begrunnelse = row.string(col("begrunnelse")),
                endring = objectMapper.readValue(row.string(col("endring"))),
                status = objectMapper.readValue(row.string(col("status"))),
            )
        }
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                f.id as "f.id",
                f.deltaker_id as "f.deltaker_id",
                f.arrangoransatt_id as "f.arrangoransatt_id",
                f.opprettet as "f.opprettet",
                f.begrunnelse as "f.begrunnelse",
                f.endring as "f.endring",
                f.status as "f.status"
            FROM forslag f 
            WHERE f.deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(Companion::rowMapper).asList)
    }

    fun get(id: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                f.id as "f.id",
                f.deltaker_id as "f.deltaker_id",
                f.arrangoransatt_id as "f.arrangoransatt_id",
                f.opprettet as "f.opprettet",
                f.begrunnelse as "f.begrunnelse",
                f.endring as "f.endring",
                f.status as "f.status"
            FROM forslag f 
            WHERE f.id = :id;
            """.trimIndent(),
            mapOf("id" to id),
        ).map(Companion::rowMapper).asSingle
        it.run(query)?.let { d -> Result.success(d) }
            ?: Result.failure(NoSuchElementException("Ingen forslag med id $id"))
    }

    fun upsert(forslag: Forslag) = Database.query {
        val sql =
            """
            INSERT INTO forslag(
                id, 
                deltaker_id, 
                arrangoransatt_id, 
                opprettet, 
                begrunnelse, 
                endring,  
                status)
            VALUES (:id,
            		:deltaker_id,
            		:arrangoransatt_id,
            		:opprettet,
            		:begrunnelse,
            		:endring,
                    :status)
            ON CONFLICT (id) DO UPDATE SET
            		deltaker_id     	= :deltaker_id,
            		arrangoransatt_id	= :arrangoransatt_id,
            		opprettet 			= :opprettet,
            		begrunnelse			= :begrunnelse,
            		endring				= :endring,
                    status              = :status,
                    modified_at         = current_timestamp
            """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to forslag.id,
                    "deltaker_id" to forslag.deltakerId,
                    "arrangoransatt_id" to forslag.opprettetAvArrangorAnsattId,
                    "opprettet" to forslag.opprettet,
                    "begrunnelse" to forslag.begrunnelse,
                    "endring" to toPGObject(forslag.endring),
                    "status" to toPGObject(forslag.status),
                ),
            ),
        )
    }

    fun delete(id: UUID) = Database.query {
        val query = queryOf(
            """delete from forslag where id = :id""",
            mapOf("id" to id),
        )
        it.update(query)
    }

    fun kanLagres(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT id FROM deltaker WHERE id = :id
            """.trimIndent(),
            mapOf("id" to deltakerId),
        ).map { row -> row.uuid("id") }.asSingle
        it.run(query)?.let { true } ?: false
    }
}
