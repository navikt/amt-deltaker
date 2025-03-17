package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

object TestRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun cleanDatabase() = Database.query { session ->
        val tables = listOf(
            "deltaker_endring",
            "forslag",
            "endring_fra_arrangor",
            "vedtak",
            "importert_fra_arena",
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
        val sql =
            """
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
        val sql =
            """
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
        val sql =
            """
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

        val sql =
            """
            insert into nav_bruker(
                person_id, personident, fornavn, mellomnavn, etternavn, nav_veileder_id, nav_enhet_id, telefonnummer, epost, er_skjermet, 
                adresse, adressebeskyttelse, oppfolgingsperioder, innsatsgruppe
            ) 
            values (
                :person_id, :personident, :fornavn, :mellomnavn, :etternavn, :nav_veileder_id, :nav_enhet_id, :telefonnummer, :epost, :er_skjermet, 
                :adresse, :adressebeskyttelse, :oppfolgingsperioder, :innsatsgruppe
            )
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
            "innsatsgruppe" to bruker.innsatsgruppe?.name,
        )

        it.update(queryOf(sql, params))
    }

    fun insert(tiltakstype: Tiltakstype) = Database.query {
        try {
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
        } catch (e: Exception) {
            log.warn("Tiltakstype ${tiltakstype.navn} er allerede opprettet")
        }
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
            val sql =
                """
                INSERT INTO deltakerliste( id, navn, status, arrangor_id, tiltakstype_id, start_dato, slutt_dato, oppstart, apent_for_pamelding)
                VALUES (:id, :navn, :status, :arrangor_id, :tiltakstype_id, :start_dato, :slutt_dato, :oppstart, :apent_for_pamelding) 
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
                        "apent_for_pamelding" to deltakerliste.apentForPamelding,
                    ),
                ),
            )
        }
    }

    fun insert(deltaker: Deltaker, vedtak: Vedtak? = null) = Database.query {
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

        val sql =
            """
            insert into deltaker(
                id, person_id, deltakerliste_id, startdato, sluttdato, dager_per_uke, 
                deltakelsesprosent, bakgrunnsinformasjon, innhold, kilde, modified_at,
                er_manuelt_delt_med_arrangor
            )
            values (
                :id, :person_id, :deltakerlisteId, :startdato, :sluttdato, :dagerPerUke, 
                :deltakelsesprosent, :bakgrunnsinformasjon, :innhold, :kilde, :sistEndret,
                :er_manuelt_delt_med_arrangor
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
            "innhold" to toPGObject(deltaker.deltakelsesinnhold),
            "kilde" to deltaker.kilde.name,
            "sistEndret" to deltaker.sistEndret,
            "er_manuelt_delt_med_arrangor" to deltaker.erManueltDeltMedArrangor,
        )

        it.update(queryOf(sql, params))
        insert(deltaker.status, deltaker.id)

        if (vedtak != null) {
            try {
                insert(vedtak)
            } catch (e: Exception) {
                log.warn("Vedtak med id ${vedtak.id} er allerede opprettet")
            }
        }
    }

    fun insert(status: DeltakerStatus, deltakerId: UUID) = Database.query {
        val sql =
            """
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
        val sql =
            """
            insert into vedtak(id, deltaker_id, fattet, gyldig_til, deltaker_ved_vedtak, fattet_av_nav, opprettet_av,
              opprettet_av_enhet, sist_endret_av, sist_endret_av_enhet, modified_at, created_at) 
            values (:id, :deltaker_id, :fattet, :gyldig_til, :deltaker_ved_vedtak, :fattet_av_nav, :opprettet_av,
              :opprettet_av_enhet, :sist_endret_av, :sist_endret_av_enhet, :sist_endret, :created_at) 
            on conflict (id) do nothing;
            """.trimIndent()

        val params = mapOf(
            "id" to vedtak.id,
            "deltaker_id" to vedtak.deltakerId,
            "fattet" to vedtak.fattet,
            "gyldig_til" to vedtak.gyldigTil,
            "deltaker_ved_vedtak" to toPGObject(vedtak.deltakerVedVedtak),
            "fattet_av_nav" to vedtak.fattetAvNav,
            "opprettet_av" to vedtak.opprettetAv,
            "opprettet_av_enhet" to vedtak.opprettetAvEnhet,
            "sist_endret_av" to vedtak.sistEndretAv,
            "sist_endret_av_enhet" to vedtak.sistEndretAvEnhet,
            "sist_endret" to vedtak.sistEndret,
            "created_at" to vedtak.opprettet,
        )

        it.update(queryOf(sql, params))
    }

    fun insert(deltakerEndring: DeltakerEndring, behandlet: LocalDateTime? = LocalDateTime.now()) = Database.query {
        val sql =
            """
            insert into deltaker_endring (id, deltaker_id, endring, endret, endret_av, endret_av_enhet, modified_at, forslag_id, behandlet)
            values (:id, :deltaker_id, :endring, :endret, :endret_av, :endret_av_enhet, current_timestamp, :forslag_id, :behandlet)
            on conflict (id) do nothing;
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerEndring.id,
            "deltaker_id" to deltakerEndring.deltakerId,
            "endring" to toPGObject(deltakerEndring.endring),
            "endret" to deltakerEndring.endret,
            "endret_av" to deltakerEndring.endretAv,
            "endret_av_enhet" to deltakerEndring.endretAvEnhet,
            "endret" to deltakerEndring.endret,
            "forslag_id" to deltakerEndring.forslag?.id,
            "behandlet" to behandlet,
        )

        it.update(queryOf(sql, params))
    }

    fun insert(forslag: Forslag) = Database.query {
        val sql =
            """
            INSERT INTO forslag(id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
            VALUES (:id, :deltaker_id, :arrangoransatt_id, :opprettet, :begrunnelse, :endring, :status)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to forslag.id,
                    "deltaker_id" to forslag.deltakerId,
                    "arrangoransatt_id" to forslag.opprettetAvArrangorAnsattId,
                    "opprettet" to forslag.opprettet,
                    "begrunnelse" to forslag.begrunnelse,
                    "endring" to toPGObject(forslag.endring),
                    "status" to toPGObject(forslag.status),
                ),
            ),
        )
    }

    fun insert(endringFraArrangor: EndringFraArrangor) = Database.query {
        val sql =
            """
            insert into endring_fra_arrangor (id, deltaker_id, arrangor_ansatt_id, opprettet, endring)
            values (:id, :deltaker_id, :arrangor_ansatt_id, :opprettet, :endring)
            on conflict (id) do nothing
            """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "id" to endringFraArrangor.id,
                    "deltaker_id" to endringFraArrangor.deltakerId,
                    "arrangor_ansatt_id" to endringFraArrangor.opprettetAvArrangorAnsattId,
                    "opprettet" to endringFraArrangor.opprettet,
                    "endring" to toPGObject(endringFraArrangor.endring),
                ),
            ),
        )
    }

    fun insert(importertFraArena: ImportertFraArena) = Database.query {
        val sql =
            """
            INSERT INTO importert_fra_arena(
                deltaker_id, 
                importert_dato, 
                deltaker_ved_import)
            VALUES (:deltaker_id,
                    :importert_dato,
            		    :deltaker_ved_import)
            ON CONFLICT (deltaker_id) DO NOTHING
            """.trimIndent()

        it.update(
            queryOf(
                sql,
                mapOf(
                    "deltaker_id" to importertFraArena.deltakerId,
                    "importert_dato" to importertFraArena.importertDato,
                    "deltaker_ved_import" to toPGObject(importertFraArena.deltakerVedImport),
                ),
            ),
        )
    }

    fun insert(endring: EndringFraTiltakskoordinator) = Database.query {
        val sql =
            """
            insert into endring_fra_tiltakskoordinator (id, deltaker_id, nav_ansatt_id, endret, endring) 
            values (:id, :deltaker_id, :nav_ansatt_id, :endret, :endring)
            """.trimIndent()

        val params = mapOf(
            "id" to endring.id,
            "deltaker_id" to endring.deltakerId,
            "nav_ansatt_id" to endring.endretAv,
            "endret" to endring.endret,
            "endring" to toPGObject(endring.endring),
        )

        it.update(queryOf(sql, params))
    }

    fun <T> insertAll(vararg values: T) {
        values.forEach {
            when (it) {
                is NavAnsatt -> insert(it)
                is NavBruker -> insert(it)
                is NavEnhet -> insert(it)
                is Arrangor -> insert(it)
                is Tiltakstype -> insert(it)
                is Deltakerliste -> insert(it)
                is Deltaker -> insert(it)
                is Vedtak -> insert(it)
                is Forslag -> insert(it)
                is DeltakerEndring -> insert(it)
                is EndringFraArrangor -> insert(it)
                is ImportertFraArena -> insert(it)
                is EndringFraTiltakskoordinator -> insert(it)
                else -> NotImplementedError("insertAll for type ${it!!::class} er ikke implementert")
            }
        }
    }
}
