package no.nav.amt.deltaker.navbruker

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.navbruker.model.Adressebeskyttelse
import no.nav.amt.deltaker.navbruker.model.NavBruker
import java.util.UUID

class NavBrukerRepository {
    private fun rowMapper(row: Row) = NavBruker(
        personId = row.uuid("person_id"),
        personident = row.string("personident"),
        fornavn = row.string("fornavn"),
        mellomnavn = row.stringOrNull("mellomnavn"),
        etternavn = row.string("etternavn"),
        navVeilederId = row.uuidOrNull("nav_veileder_id"),
        navEnhetId = row.uuidOrNull("nav_enhet_id"),
        telefon = row.stringOrNull("telefonnummer"),
        epost = row.stringOrNull("epost"),
        erSkjermet = row.boolean("er_skjermet"),
        adresse = row.stringOrNull("adresse")?.let { objectMapper.readValue(it) },
        adressebeskyttelse = row.stringOrNull("adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
        oppfolgingsperioder = row.stringOrNull("oppfolgingsperioder")?.let { objectMapper.readValue(it) } ?: emptyList(),
    )

    fun upsert(bruker: NavBruker) = Database.query {
        val sql =
            """
            insert into nav_bruker(person_id, personident, fornavn, mellomnavn, etternavn, nav_veileder_id, nav_enhet_id, telefonnummer, epost, er_skjermet, adresse, adressebeskyttelse, oppfolgingsperioder) 
            values (:person_id, :personident, :fornavn, :mellomnavn, :etternavn, :nav_veileder_id, :nav_enhet_id, :telefonnummer, :epost, :er_skjermet, :adresse, :adressebeskyttelse, :oppfolgingsperioder)
            on conflict (person_id) do update set
                personident = :personident,
                fornavn = :fornavn,
                mellomnavn = :mellomnavn,
                etternavn = :etternavn,
                nav_veileder_id = :nav_veileder_id,
                nav_enhet_id = :nav_enhet_id,
                telefonnummer = :telefonnummer,
                epost = :epost,
                er_skjermet = :er_skjermet,
                adresse = :adresse,
                adressebeskyttelse = :adressebeskyttelse,
                oppfolgingsperioder = :oppfolgingsperioder,
                modified_at = current_timestamp
            returning *
            """.trimIndent()

        val params = mapOf(
            "person_id" to bruker.personId,
            "personident" to bruker.personident,
            "fornavn" to bruker.fornavn,
            "mellomnavn" to bruker.mellomnavn,
            "etternavn" to bruker.etternavn,
            "nav_veileder_id" to bruker.navVeilederId,
            "nav_enhet_id" to bruker.navEnhetId,
            "telefonnummer" to bruker.telefon,
            "epost" to bruker.epost,
            "er_skjermet" to bruker.erSkjermet,
            "adresse" to toPGObject(bruker.adresse),
            "adressebeskyttelse" to bruker.adressebeskyttelse?.name,
            "oppfolgingsperioder" to toPGObject(bruker.oppfolgingsperioder),
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
