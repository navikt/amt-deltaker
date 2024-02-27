package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

object TestRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun cleanDatabase() = Database.query { session ->
        val tables = listOf(
            "deltaker_endring",
            "vedtak",
            "deltaker_status",
            "deltaker",
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

    fun insert(deltaker: Deltaker, sistEndretAv: NavAnsatt, sistEndretAvEnhet: NavEnhet) = Database.query {
        try {
            insert(deltaker.navBruker)
        } catch (e: Exception) {
            log.warn("Nav-bruker med id ${deltaker.navBruker.personId} er allerede opprettet")
        }

        try {
            insert(deltaker.deltakerliste)
        } catch (e: Exception) {
            log.warn("Deltakerliste med id ${deltaker.deltakerliste.id} er allerede opprettet")
        }

        try {
            insert(sistEndretAv)
        } catch (e: Exception) {
            log.warn("Ansatt med id ${sistEndretAv.id} er allerede opprettet")
        }

        try {
            insert(sistEndretAvEnhet)
        } catch (e: Exception) {
            log.warn("Enhet med id ${sistEndretAvEnhet.id} er allerede opprettet")
        }

        val sql = """
            insert into deltaker(
                id, person_id, deltakerliste_id, startdato, sluttdato, dager_per_uke, 
                deltakelsesprosent, bakgrunnsinformasjon, innhold, sist_endret_av, sist_endret_av_enhet, modified_at
            )
            values (
                :id, :person_id, :deltakerlisteId, :startdato, :sluttdato, :dagerPerUke, 
                :deltakelsesprosent, :bakgrunnsinformasjon, :innhold, :sistEndretAv, :sistEndretAvEnhet, :sistEndret
            )
        """.trimIndent()

        val params = mapOf(
            "id" to deltaker.id,
            "person_id" to deltaker.navBruker.personId,
            "deltakerlisteId" to deltaker.deltakerliste.id,
            "startdato" to deltaker.startdato,
            "sluttdato" to deltaker.sluttdato,
            "dagerPerUke" to deltaker.dagerPerUke,
            "deltakelsesprosent" to deltaker.deltakelsesprosent,
            "bakgrunnsinformasjon" to deltaker.bakgrunnsinformasjon,
            "innhold" to toPGObject(deltaker.innhold),
            "sistEndretAv" to deltaker.sistEndretAv,
            "sistEndretAvEnhet" to deltaker.sistEndretAvEnhet,
            "sistEndret" to deltaker.sistEndret,
        )

        it.update(queryOf(sql, params))
        insert(deltaker.status, deltaker.id)
    }

    fun insert(status: DeltakerStatus, deltakerId: UUID) = Database.query {
        val sql = """
            insert into deltaker_status(id, deltaker_id, type, aarsak, gyldig_fra, gyldig_til, created_at) 
            values (:id, :deltaker_id, :type, :aarsak, :gyldig_fra, :gyldig_til, :created_at) 
            on conflict (id) do nothing;
        """.trimIndent()

        val params = mapOf(
            "id" to status.id,
            "deltaker_id" to deltakerId,
            "type" to status.type.name,
            "aarsak" to toPGObject(status.aarsak),
            "gyldig_fra" to status.gyldigFra,
            "gyldig_til" to status.gyldigTil,
            "created_at" to status.opprettet,
        )

        it.update(queryOf(sql, params))
    }

    fun insert(vedtak: Vedtak) = Database.query {
        val sql = """
            insert into vedtak(id, deltaker_id, fattet, gyldig_til, deltaker_ved_vedtak, fattet_av_nav, opprettet_av,
              opprettet_av_enhet, sist_endret_av, sist_endret_av_enhet) 
            values (:id, :deltaker_id, :fattet, :gyldig_til, :deltaker_ved_vedtak, :fattet_av_nav, :opprettet_av,
              :opprettet_av_enhet, :sist_endret_av, :sist_endret_av_enhet) 
            on conflict (id) do nothing;
        """.trimIndent()

        val params = mapOf(
            "id" to vedtak.id,
            "deltaker_id" to vedtak.deltakerId,
            "fattet" to vedtak.fattet,
            "gyldig_til" to vedtak.gyldigTil,
            "deltaker_ved_vedtak" to toPGObject(vedtak.deltakerVedVedtak),
            "fattet_av_nav" to vedtak.fattetAvNav?.let(::toPGObject),
            "opprettet_av" to vedtak.opprettetAv,
            "opprettet_av_enhet" to vedtak.opprettetAvEnhet,
            "sist_endret_av" to vedtak.sistEndretAv,
            "sist_endret_av_enhet" to vedtak.sistEndretAvEnhet,
        )

        it.update(queryOf(sql, params))
    }
}
