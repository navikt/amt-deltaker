package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object TestRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun cleanDatabase() = Database.query { session ->
        val tables = listOf(
            "nav_bruker",
            "nav_ansatt",
            "nav_enhet",
            "deltakerliste",
            "arrangor",
            "tiltakstype",
        )
        tables.forEach {
            val query = queryOf(
                """delete from $it""",
                emptyMap(),
            )

            session.update(query)
        }
    }

    fun insert(arrangor: Arrangor) {
        val sql = """
            INSERT INTO arrangor(id, navn, organisasjonsnummer, overordnet_arrangor_id)
            VALUES (:id, :navn, :organisasjonsnummer, :overordnet_arrangor_id) 
        """.trimIndent()

        Database.query {
            val query = queryOf(
                sql,
                mapOf(
                    "id" to arrangor.id,
                    "navn" to arrangor.navn,
                    "organisasjonsnummer" to arrangor.organisasjonsnummer,
                    "overordnet_arrangor_id" to arrangor.overordnetArrangorId,
                ),
            )
            it.update(query)
        }
    }

    fun insert(navAnsatt: NavAnsatt) = Database.query {
        val sql = """
            insert into nav_ansatt(id, nav_ident, navn, telefonnummer, epost, modified_at)
            values (:id, :nav_ident, :navn, :telefonnummer, :epost, :modified_at) 
            on conflict (id) do nothing;
        """.trimIndent()

        val params = mapOf(
            "id" to navAnsatt.id,
            "nav_ident" to navAnsatt.navIdent,
            "navn" to navAnsatt.navn,
            "telefonnummer" to navAnsatt.telefon,
            "epost" to navAnsatt.epost,
            "modified_at" to LocalDateTime.now(),
        )

        it.update(queryOf(sql, params))
    }

    fun insert(navEnhet: NavEnhet) = Database.query {
        val sql = """
            insert into nav_enhet(id, nav_enhet_nummer, navn, modified_at)
            values (:id, :nav_enhet_nummer, :navn, :modified_at) 
            on conflict (id) do nothing;
        """.trimIndent()

        val params = mapOf(
            "id" to navEnhet.id,
            "nav_enhet_nummer" to navEnhet.enhetsnummer,
            "navn" to navEnhet.navn,
            "modified_at" to LocalDateTime.now(),
        )

        it.update(queryOf(sql, params))
    }

    fun insert(bruker: NavBruker) = Database.query {
        bruker.navEnhetId?.let { enhetId ->
            val navEnhet = lagNavEnhet(id = enhetId)
            try {
                insert(navEnhet)
            } catch (e: Exception) {
                log.warn("Nav-enhet med id ${bruker.navEnhetId} er allerede opprettet")
            }
        }

        bruker.navVeilederId?.let { veilederId ->
            val navAnsatt = lagNavAnsatt(id = veilederId)
            try {
                insert(navAnsatt)
            } catch (e: Exception) {
                log.warn("Nav-ansatt med id ${bruker.navVeilederId} er allerede opprettet")
            }
        }

        val sql = """
            insert into nav_bruker(person_id, personident, fornavn, mellomnavn, etternavn, nav_veileder_id, nav_enhet_id, telefonnummer, epost, er_skjermet, adresse, adressebeskyttelse) 
            values (:person_id, :personident, :fornavn, :mellomnavn, :etternavn, :nav_veileder_id, :nav_enhet_id, :telefonnummer, :epost, :er_skjermet, :adresse, :adressebeskyttelse)
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
        )

        it.update(queryOf(sql, params))
    }

    fun insert(tiltakstype: Tiltakstype) = Database.query {
        val sql = """
            insert into tiltakstype(id, navn, type, innhold) 
            values (:id, :navn, :type, :innhold)
        """.trimIndent()

        val params = mapOf(
            "id" to tiltakstype.id,
            "navn" to tiltakstype.navn,
            "type" to tiltakstype.type.name,
            "innhold" to toPGObject(tiltakstype.innhold),
        )

        it.update(queryOf(sql, params))
    }

    fun insert(deltakerliste: Deltakerliste) {
        try {
            insert(deltakerliste.arrangor)
        } catch (e: Exception) {
            log.warn("Arrangor med id ${deltakerliste.arrangor.id} er allerede opprettet")
        }

        try {
            insert(deltakerliste.tiltakstype)
        } catch (e: Exception) {
            log.warn("Tiltakstype med id ${deltakerliste.tiltakstype.id} er allerede opprettet")
        }

        Database.query {
            val sql = """
                INSERT INTO deltakerliste( id, navn, status, arrangor_id, tiltakstype_id, start_dato, slutt_dato, oppstart)
                VALUES (:id, :navn, :status, :arrangor_id, :tiltakstype_id, :start_dato, :slutt_dato, :oppstart) 
            """.trimIndent()

            it.update(
                queryOf(
                    sql,
                    mapOf(
                        "id" to deltakerliste.id,
                        "navn" to deltakerliste.navn,
                        "status" to deltakerliste.status.name,
                        "arrangor_id" to deltakerliste.arrangor.id,
                        "tiltakstype_id" to deltakerliste.tiltakstype.id,
                        "start_dato" to deltakerliste.startDato,
                        "slutt_dato" to deltakerliste.sluttDato,
                        "oppstart" to deltakerliste.oppstart?.name,
                    ),
                ),
            )
        }
    }
}
