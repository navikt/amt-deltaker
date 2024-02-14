package no.nav.amt.deltaker.deltakerliste.tiltakstype

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import org.slf4j.LoggerFactory

class TiltakstypeRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun rowMapper(row: Row) = Tiltakstype(
        id = row.uuid("id"),
        navn = row.string("navn"),
        type = Tiltakstype.Type.valueOf(row.string("type")),
        innhold = row.string("innhold").let { objectMapper.readValue(it) },
    )

    fun upsert(tiltakstype: Tiltakstype) = Database.query {
        val sql = """
        INSERT INTO tiltakstype(
            id, 
            navn, 
            type, 
            innhold)
        VALUES (:id,
        		:navn,
        		:type,
        		:innhold)
        ON CONFLICT (id) DO UPDATE SET
        		navn     		    = :navn,
        		type				= :type,
        		innhold 			= :innhold,
                modified_at         = current_timestamp
        """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to tiltakstype.id,
                    "navn" to tiltakstype.navn,
                    "type" to tiltakstype.type.name,
                    "innhold" to toPGObject(tiltakstype.innhold),
                ),
            ),
        )

        log.info("Upsertet tiltakstype med id ${tiltakstype.id}")
    }

    fun get(type: Tiltakstype.Type) = Database.query {
        val query = queryOf(
            """
                SELECT id,
                   navn,
                   type,
                   innhold
                FROM tiltakstype
                WHERE type = :type
            """.trimIndent(),
            mapOf("type" to type.name),
        ).map(::rowMapper).asSingle

        it.run(query)?.let { t -> Result.success(t) }
            ?: Result.failure(NoSuchElementException("Fant ikke tiltakstype ${type.name}"))
    }
}
