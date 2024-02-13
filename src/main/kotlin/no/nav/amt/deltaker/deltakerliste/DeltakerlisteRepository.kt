package no.nav.amt.deltaker.deltakerliste

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.db.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun rowMapper(row: Row) = Deltakerliste(
        id = row.uuid("deltakerliste_id"),
        tiltak = Tiltak(
            navn = row.string("tiltaksnavn"),
            type = Tiltak.Type.valueOf(row.string("tiltakstype")),
        ),
        navn = row.string("deltakerliste_navn"),
        status = Deltakerliste.Status.valueOf(row.string("status")),
        startDato = row.localDate("start_dato"),
        sluttDato = row.localDate("slutt_dato"),
        oppstart = Deltakerliste.Oppstartstype.valueOf(row.string("oppstart")),
        arrangor = Arrangor(
            id = row.uuid("arrangor_id"),
            navn = row.string("arrangor_navn"),
            organisasjonsnummer = row.string("organisasjonsnummer"),
            overordnetArrangorId = row.uuidOrNull("overordnet_arrangor_id"),
        ),
    )

    fun upsert(deltakerliste: Deltakerliste) = Database.query {
        val sql = """
        INSERT INTO deltakerliste(
            id, 
            navn, 
            status, 
            arrangor_id, 
            tiltaksnavn, 
            tiltakstype, 
            start_dato, 
            slutt_dato, 
            oppstart)
        VALUES (:id,
        		:navn,
        		:status,
        		:arrangor_id,
        		:tiltaksnavn,
        		:tiltakstype,
        		:start_dato,
        		:slutt_dato,
                :oppstart)
        ON CONFLICT (id) DO UPDATE SET
        		navn     				= :navn,
        		status					= :status,
        		arrangor_id 			= :arrangor_id,
        		tiltaksnavn				= :tiltaksnavn,
        		tiltakstype				= :tiltakstype,
        		start_dato				= :start_dato,
        		slutt_dato				= :slutt_dato,
                oppstart                = :oppstart,
                modified_at             = current_timestamp
        """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to deltakerliste.id,
                    "navn" to deltakerliste.navn,
                    "status" to deltakerliste.status.name,
                    "arrangor_id" to deltakerliste.arrangor.id,
                    "tiltaksnavn" to deltakerliste.tiltak.navn,
                    "tiltakstype" to deltakerliste.tiltak.type.name,
                    "start_dato" to deltakerliste.startDato,
                    "slutt_dato" to deltakerliste.sluttDato,
                    "oppstart" to deltakerliste.oppstart?.name,
                ),
            ),
        )

        log.info("Upsertet deltakerliste med id ${deltakerliste.id}")
    }

    fun delete(id: UUID) = Database.query {
        it.update(
            queryOf(
                statement = "delete from deltakerliste where id = :id",
                paramMap = mapOf("id" to id),
            ),
        )
        log.info("Slettet deltakerliste med id $id")
    }

    fun get(id: UUID) = Database.query {
        val query = queryOf(
            """
                SELECT deltakerliste.id   AS deltakerliste_id,
                   arrangor_id,
                   tiltaksnavn,
                   tiltakstype,
                   deltakerliste.navn AS deltakerliste_navn,
                   status,
                   start_dato,
                   slutt_dato,
                   oppstart,
                   a.navn             AS arrangor_navn,
                   organisasjonsnummer,
                   overordnet_arrangor_id
                FROM deltakerliste
                     INNER JOIN arrangor a ON a.id = deltakerliste.arrangor_id
                WHERE deltakerliste.id = :id
            """.trimIndent(),
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        it.run(query)?.let { dl -> Result.success(dl) }
            ?: Result.failure(NoSuchElementException("Fant ikke deltakerliste med id $id"))
    }
}
