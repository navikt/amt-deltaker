package no.nav.amt.deltaker.deltakerliste.tiltakstype

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory

class TiltakstypeRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun rowMapper(row: Row, alias: String? = null): Tiltakstype {
            val col = prefixColumn(alias)

            return Tiltakstype(
                id = row.uuid(col("id")),
                navn = row.string(col("navn")),
                tiltakskode = Tiltakstype.Tiltakskode.valueOf(row.string(col("tiltakskode"))),
                arenaKode = Tiltakstype.ArenaKode.valueOf(row.string(col("type"))),
                innsatsgrupper = row.string(col("innsatsgrupper")).let { objectMapper.readValue(it) },
                innhold = row.stringOrNull(col("innhold"))?.let { objectMapper.readValue<DeltakerRegistreringInnhold?>(it) },
            )
        }
    }

    fun upsert(tiltakstype: Tiltakstype) = Database.query {
        val sql =
            """
            INSERT INTO tiltakstype(
                id, 
                navn, 
                tiltakskode,
                type, 
                innsatsgrupper,
                innhold)
            VALUES (:id,
            		:navn,
                    :tiltakskode,
            		:type,
                    :innsatsgrupper,
            		:innhold)
            ON CONFLICT (id) DO UPDATE SET
            		navn     		    = :navn,
                    tiltakskode         = :tiltakskode,
            		type				= :type,
            		innsatsgrupper		= :innsatsgrupper,
            		innhold 			= :innhold,
                    modified_at         = current_timestamp
            """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to tiltakstype.id,
                    "navn" to tiltakstype.navn,
                    "tiltakskode" to tiltakstype.tiltakskode.name,
                    "type" to tiltakstype.arenaKode.name,
                    "innsatsgrupper" to toPGObject(tiltakstype.innsatsgrupper),
                    "innhold" to toPGObject(tiltakstype.innhold),
                ),
            ),
        )

        log.info("Upsertet tiltakstype med id ${tiltakstype.id}")
    }

    fun get(type: Tiltakstype.ArenaKode) = Database.query {
        val query = queryOf(
            """
            SELECT id,
               navn,
               tiltakskode,
               type,
               innsatsgrupper,
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
