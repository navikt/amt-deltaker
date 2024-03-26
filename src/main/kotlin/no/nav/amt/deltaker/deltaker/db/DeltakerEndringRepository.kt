package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import java.util.UUID

class DeltakerEndringRepository {
    private fun rowMapper(row: Row): DeltakerEndring {
        return DeltakerEndring(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            endring = objectMapper.readValue(row.string("endring")),
            endretAv = row.uuid("endret_av"),
            endretAvEnhet = row.uuid("endret_av_enhet"),
            endret = row.localDateTime("dh.modified_at"),
        )
    }

    fun upsert(deltakerEndring: DeltakerEndring) = Database.query {
        val sql =
            """
            insert into deltaker_endring (id, deltaker_id, endring, endret_av, endret_av_enhet)
            values (:id, :deltaker_id, :endring, :endret_av, :endret_av_enhet)
            on conflict (id) do update set 
                deltaker_id = :deltaker_id,
                endring = :endring,
                endret_av = :endret_av,
                endret_av_enhet = :endret_av_enhet,
                modified_at = current_timestamp
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerEndring.id,
            "deltaker_id" to deltakerEndring.deltakerId,
            "endring" to toPGObject(deltakerEndring.endring),
            "endret_av" to deltakerEndring.endretAv,
            "endret_av_enhet" to deltakerEndring.endretAvEnhet,
        )

        it.update(queryOf(sql, params))
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT dh.id              AS id,
                   dh.deltaker_id     AS deltaker_id,
                   dh.endring         AS endring,
                   dh.endret_av       AS endret_av,
                   dh.endret_av_enhet AS endret_av_enhet,
                   dh.modified_at     AS "dh.modified_at"
            FROM deltaker_endring dh
            WHERE deltaker_id = :deltaker_id
            ORDER BY dh.created_at;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(::rowMapper).asList)
    }
}
