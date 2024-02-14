package no.nav.amt.deltaker.navbruker

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.db.Database
import java.util.UUID

class NavBrukerRepository {

    private fun rowMapper(row: Row) = NavBruker(
        personId = row.uuid("person_id"),
        personident = row.string("personident"),
        fornavn = row.string("fornavn"),
        mellomnavn = row.stringOrNull("mellomnavn"),
        etternavn = row.string("etternavn"),
    )

    fun upsert(bruker: NavBruker) = Database.query {
        val sql = """
            insert into nav_bruker(person_id, personident, fornavn, mellomnavn, etternavn) 
            values (:person_id, :personident, :fornavn, :mellomnavn, :etternavn)
            on conflict (person_id) do update set
                personident = :personident,
                fornavn = :fornavn,
                mellomnavn = :mellomnavn,
                etternavn = :etternavn,
                modified_at = current_timestamp
            returning *
        """.trimIndent()

        val params = mapOf(
            "person_id" to bruker.personId,
            "personident" to bruker.personident,
            "fornavn" to bruker.fornavn,
            "mellomnavn" to bruker.mellomnavn,
            "etternavn" to bruker.etternavn,
        )

        it.run(queryOf(sql, params).map(::rowMapper).asSingle)
            ?.let { b -> Result.success(b) }
            ?: Result.failure(NoSuchElementException("Noe gikk galt med upsert av bruker ${bruker.personId}"))
    }

    fun get(personId: UUID) = Database.query {
        val query = queryOf(
            statement = "select * from nav_bruker where person_id = :person_id",
            paramMap = mapOf("person_id" to personId),
        )

        it.run(query.map(::rowMapper).asSingle)
            ?.let { b -> Result.success(b) }
            ?: Result.failure(NoSuchElementException("Fant ikke bruker $personId"))
    }

    fun get(personident: String) = Database.query {
        val query = queryOf(
            statement = "select * from nav_bruker where personident = :personident",
            paramMap = mapOf("personident" to personident),
        )

        it.run(query.map(::rowMapper).asSingle)
            ?.let { b -> Result.success(b) }
            ?: Result.failure(NoSuchElementException("Fant ikke bruker med personident"))
    }
}
