package no.nav.amt.deltaker.navansatt.navenhet

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.db.Database
import java.time.LocalDateTime
import java.util.UUID

class NavEnhetRepository {
    private fun rowMapper(row: Row) = NavEnhet(
        id = row.uuid("id"),
        enhetsnummer = row.string("nav_enhet_nummer"),
        navn = row.string("navn"),
    )

    fun upsert(navEnhet: NavEnhet): NavEnhet {
        val sql =
            """
            INSERT INTO nav_enhet(id, nav_enhet_nummer, navn, modified_at)
            VALUES (:id, :nav_enhet_nummer, :navn, :modified_at) 
            ON CONFLICT (id) DO UPDATE SET
                nav_enhet_nummer = :nav_enhet_nummer,
                navn = :navn,
                modified_at = :modified_at
            returning *
            """.trimIndent()

        return Database.query {
            val query = queryOf(
                sql,
                mapOf(
                    "id" to navEnhet.id,
                    "nav_enhet_nummer" to navEnhet.enhetsnummer,
                    "navn" to navEnhet.navn,
                    "modified_at" to LocalDateTime.now(),
                ),
            ).map(::rowMapper).asSingle

            it.run(query)
        } ?: throw RuntimeException("Noe gikk galt ved lagring av nav-enhet")
    }

    fun get(enhetsnummer: String): NavEnhet? {
        return Database.query {
            val query = queryOf(
                """select * from nav_enhet where nav_enhet_nummer = :nav_enhet_nummer""",
                mapOf("nav_enhet_nummer" to enhetsnummer),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }

    fun get(id: UUID): NavEnhet? {
        return Database.query {
            val query = queryOf(
                """select * from nav_enhet where id = :id""",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }
}
