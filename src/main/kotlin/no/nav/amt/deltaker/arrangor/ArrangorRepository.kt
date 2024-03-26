package no.nav.amt.deltaker.arrangor

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.db.Database
import java.time.LocalDateTime
import java.util.UUID

class ArrangorRepository {
    private fun rowMapper(row: Row) = Arrangor(
        id = row.uuid("id"),
        navn = row.string("navn"),
        organisasjonsnummer = row.string("organisasjonsnummer"),
        overordnetArrangorId = row.uuidOrNull("overordnet_arrangor_id"),
    )

    fun upsert(arrangor: Arrangor) {
        val sql =
            """
            INSERT INTO arrangor(id, navn, organisasjonsnummer, overordnet_arrangor_id)
            VALUES (:id, :navn, :organisasjonsnummer, :overordnet_arrangor_id) 
            ON CONFLICT (id) DO UPDATE SET
                navn = :navn,
                organisasjonsnummer = :organisasjonsnummer,
                overordnet_arrangor_id = :overordnet_arrangor_id,
                modified_at = :modified_at
            """.trimIndent()

        Database.query {
            val query = queryOf(
                sql,
                mapOf(
                    "id" to arrangor.id,
                    "navn" to arrangor.navn,
                    "organisasjonsnummer" to arrangor.organisasjonsnummer,
                    "overordnet_arrangor_id" to arrangor.overordnetArrangorId,
                    "modified_at" to LocalDateTime.now(),
                ),
            )
            it.update(query)
        }
    }

    fun get(id: UUID): Arrangor? {
        return Database.query {
            val query = queryOf(
                """select * from arrangor where id = :id""",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }

    fun get(orgnr: String): Arrangor? {
        return Database.query {
            val query = queryOf(
                """select * from arrangor where organisasjonsnummer = :orgnr""",
                mapOf("orgnr" to orgnr),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }

    fun delete(id: UUID) = Database.query {
        val query = queryOf(
            """delete from arrangor where id = :id""",
            mapOf("id" to id),
        )

        it.update(query)
    }
}
