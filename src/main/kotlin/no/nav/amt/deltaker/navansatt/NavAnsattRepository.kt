package no.nav.amt.deltaker.navansatt

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDateTime
import java.util.UUID

class NavAnsattRepository {
    fun upsert(navAnsatt: NavAnsatt): NavAnsatt {
        val sql =
            """
            INSERT INTO nav_ansatt(id, nav_ident, navn, telefonnummer, epost, modified_at, nav_enhet_id)
            VALUES (:id, :nav_ident, :navn, :telefonnummer, :epost, :modified_at, :nav_enhet_id) 
            ON CONFLICT (id) DO UPDATE SET
                nav_ident = :nav_ident,
                navn = :navn,
                telefonnummer = :telefonnummer,
                epost = :epost,
                modified_at = :modified_at,
                nav_enhet_id = :nav_enhet_id
            RETURNING *
            """.trimIndent()

        val query = queryOf(
            sql,
            mapOf(
                "id" to navAnsatt.id,
                "nav_ident" to navAnsatt.navIdent,
                "navn" to navAnsatt.navn,
                "telefonnummer" to navAnsatt.telefon,
                "epost" to navAnsatt.epost,
                "modified_at" to LocalDateTime.now(),
                "nav_enhet_id" to navAnsatt.navEnhetId,
            ),
        ).map(::rowMapper).asSingle

        return Database.query { session ->
            session.run(query)
        } ?: throw RuntimeException("Noe gikk galt ved lagring av nav-ansatt")
    }

    fun getOrThrow(id: UUID): NavAnsatt = get(id) ?: throw NoSuchElementException("Fant ikke Nav-ansatt med id $id")

    fun get(id: UUID): NavAnsatt? {
        val query = queryOf(
            """SELECT * FROM nav_ansatt WHERE id = :id""",
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        return Database.query { session -> session.run(query) }
    }

    fun getOrThrow(navIdent: String): NavAnsatt =
        get(navIdent) ?: throw NoSuchElementException("Fant ikke Nav-ansatt med navIdent $navIdent")

    fun get(navIdent: String): NavAnsatt? {
        val query = queryOf(
            """select * from nav_ansatt where nav_ident = :nav_ident""",
            mapOf("nav_ident" to navIdent),
        ).map(::rowMapper).asSingle

        return Database.query { session -> session.run(query) }
    }

    fun delete(id: UUID) {
        val query = queryOf(
            """delete from nav_ansatt where id = :id""",
            mapOf("id" to id),
        )

        Database.query { session -> session.update(query) }
    }

    fun getMany(veilederIdenter: List<String>): List<NavAnsatt> {
        val sql =
            """
            SELECT * 
            FROM nav_ansatt 
            WHERE nav_ident IN (${veilederIdenter.joinToString { "?" }})
            """.trimIndent()

        val query = queryOf(
            sql,
            *veilederIdenter.toTypedArray(),
        ).map(::rowMapper).asList

        return Database.query { session -> session.run(query) }
    }

    companion object {
        private fun rowMapper(row: Row) = NavAnsatt(
            id = row.uuid("id"),
            navIdent = row.string("nav_ident"),
            navn = row.string("navn"),
            epost = row.stringOrNull("epost"),
            telefon = row.stringOrNull("telefonnummer"),
            navEnhetId = row.uuidOrNull("nav_enhet_id"),
        )
    }
}
