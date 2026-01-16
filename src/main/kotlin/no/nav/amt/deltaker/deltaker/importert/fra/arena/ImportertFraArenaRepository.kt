package no.nav.amt.deltaker.deltaker.importert.fra.arena

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class ImportertFraArenaRepository {
    companion object {
        fun rowMapper(row: Row, alias: String? = "i"): ImportertFraArena {
            val col = prefixColumn(alias)

            return ImportertFraArena(
                deltakerId = row.uuid(col("deltaker_id")),
                importertDato = row.localDateTime(col("importert_dato")),
                deltakerVedImport = objectMapper.readValue(row.string(col("deltaker_ved_import"))),
            )
        }
    }

    fun upsert(importertFraArena: ImportertFraArena) {
        val sql =
            """
            INSERT INTO importert_fra_arena(
                deltaker_id, 
                importert_dato, 
                deltaker_ved_import)
            VALUES (:deltaker_id,
                    COALESCE(:importert_dato, CURRENT_TIMESTAMP),
                    :deltaker_ved_import)
            ON CONFLICT (deltaker_id) DO UPDATE SET
              importert_dato      = CURRENT_TIMESTAMP, 
              deltaker_ved_import = :deltaker_ved_import
            """.trimIndent()

        Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "deltaker_id" to importertFraArena.deltakerId,
                        "deltaker_ved_import" to toPGObject(importertFraArena.deltakerVedImport),
                    ),
                ),
            )
        }
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            SELECT 
                i.deltaker_id as "i.deltaker_id",
                i.importert_dato as "i.importert_dato",
                i.deltaker_ved_import as "i.deltaker_ved_import"
            FROM importert_fra_arena i 
            WHERE i.deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(Companion::rowMapper).asSingle)
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            DELETE FROM importert_fra_arena
            WHERE deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.update(query)
    }
}
