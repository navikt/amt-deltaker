package no.nav.amt.deltaker.navansatt

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.db.Database
import java.time.LocalDateTime
import java.util.UUID

class NavAnsattRepository {
    private fun rowMapper(row: Row) = NavAnsatt(
        id = row.uuid("id"),
        navIdent = row.string("nav_ident"),
        navn = row.string("navn"),
        epost = row.stringOrNull("epost"),
        telefon = row.stringOrNull("telefonnummer"),
    )

    fun upsert(navAnsatt: NavAnsatt): NavAnsatt {
        val sql =
            """
            INSERT INTO nav_ansatt(id, nav_ident, navn, telefonnummer, epost, modified_at)
            VALUES (:id, :nav_ident, :navn, :telefonnummer, :epost, :modified_at) 
            ON CONFLICT (id) DO UPDATE SET
                nav_ident = :nav_ident,
                navn = :navn,
                telefonnummer = :telefonnummer,
                epost = :epost,
                modified_at = :modified_at
            returning *
            """.trimIndent()

        return Database.query {
            val query = queryOf(
                sql,
                mapOf(
                    "id" to navAnsatt.id,
                    "nav_ident" to navAnsatt.navIdent,
                    "navn" to navAnsatt.navn,
                    "telefonnummer" to navAnsatt.telefon,
                    "epost" to navAnsatt.epost,
                    "modified_at" to LocalDateTime.now(),
                ),
            ).map(::rowMapper).asSingle

            it.run(query)
        } ?: throw RuntimeException("Noe gikk galt ved lagring av nav-ansatt")
    }

    fun get(id: UUID): NavAnsatt? {
        return Database.query {
            val query = queryOf(
                """select * from nav_ansatt where id = :id""",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }

    fun get(navIdent: String): NavAnsatt? {
        return Database.query {
            val query = queryOf(
                """select * from nav_ansatt where nav_ident = :nav_ident""",
                mapOf("nav_ident" to navIdent),
            ).map(::rowMapper).asSingle

            it.run(query)
        }
    }

    fun delete(id: UUID) = Database.query {
        val query = queryOf(
            """delete from nav_ansatt where id = :id""",
            mapOf("id" to id),
        )
        it.update(query)
    }

    fun getMany(veilederIdenter: List<String>) = Database.query {
        val statement = "select * from nav_ansatt where nav_ident in (${veilederIdenter.joinToString { "?" }})"

        val query = queryOf(
            statement,
            *veilederIdenter.toTypedArray(),
        )

        it.run(query.map(::rowMapper).asList)
    }
}
