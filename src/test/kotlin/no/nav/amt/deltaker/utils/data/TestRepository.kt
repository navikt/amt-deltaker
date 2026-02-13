package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object TestRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun insert(navAnsatt: NavAnsatt) {
        navAnsatt.navEnhetId?.let { id ->
            NavEnhetRepository().upsert(lagNavEnhet(id))
        }

        NavAnsattRepository().upsert(navAnsatt)
    }

    fun insert(bruker: NavBruker) {
        bruker.navEnhetId?.let { NavEnhetRepository().upsert(lagNavEnhet(it)) }
        bruker.navVeilederId?.let { NavAnsattRepository().upsert(lagNavAnsatt(id = it, navEnhetId = bruker.navEnhetId)) }

        NavBrukerRepository().upsert(bruker)
    }

    fun insert(deltakerliste: Deltakerliste, overordnetArrangor: Arrangor? = null) {
        TiltakstypeRepository().upsert(deltakerliste.tiltakstype)
        overordnetArrangor?.let { ArrangorRepository().upsert(it) }
        ArrangorRepository().upsert(deltakerliste.arrangor)
        DeltakerlisteRepository().upsert(deltakerliste)
    }

    fun insert(vedtak: Vedtak) {
        VedtakRepository().upsert(vedtak)

        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE vedtak SET modified_at = :sist_endret, created_at = :opprettet WHERE id = :id",
                    mapOf(
                        "id" to vedtak.id,
                        "sist_endret" to vedtak.sistEndret,
                        "opprettet" to vedtak.opprettet,
                    ),
                ),
            )
        }
    }

    fun insert(deltaker: Deltaker, vedtak: Vedtak? = null) {
        insert(deltaker.navBruker)

        try {
            insert(deltaker.deltakerliste)
        } catch (_: Exception) {
            log.warn("Deltakerliste med id ${deltaker.deltakerliste.id} er allerede opprettet")
        }

        val sql =
            """
            INSERT INTO deltaker(
                id, person_id, deltakerliste_id, startdato, sluttdato, dager_per_uke, 
                deltakelsesprosent, bakgrunnsinformasjon, innhold, kilde, modified_at,
                er_manuelt_delt_med_arrangor
            )
            VALUES (
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

        Database.query { session -> session.update(queryOf(sql, params)) }

        DeltakerStatusRepository.lagreStatus(deltaker.id, deltaker.status)

        log.info("inserted deltaker ${deltaker.id}")

        vedtak?.let { insert(vedtak) }
    }

    fun insert(deltakerEndring: DeltakerEndring, behandlet: LocalDateTime? = LocalDateTime.now()) {
        val sql =
            """
            INSERT INTO deltaker_endring (
                id, 
                deltaker_id, 
                endring, 
                endret, 
                endret_av, 
                endret_av_enhet, 
                modified_at, 
                forslag_id, 
                behandlet)
            VALUES (
                :id, 
                :deltaker_id, 
                :endring, 
                :endret, 
                :endret_av, 
                :endret_av_enhet, 
                current_timestamp, 
                :forslag_id, 
                :behandlet)
            ON CONFLICT (id) DO NOTHING;
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

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun insert(vurdering: Vurdering) {
        val sql =
            """
            INSERT INTO vurdering (id, deltaker_id, opprettet_av_arrangor_ansatt_id, vurderingstype, begrunnelse, gyldig_fra)
            VALUES (:id, :deltaker_id, :opprettet_av_arrangor_ansatt_id, :vurderingstype, :begrunnelse, :gyldig_fra)
            ON CONFLICT (id) DO UPDATE SET
                opprettet_av_arrangor_ansatt_id = :opprettet_av_arrangor_ansatt_id, 
                vurderingstype = :vurderingstype, 
                begrunnelse = :begrunnelse, 
                gyldig_fra = :gyldig_fra
            """.trimIndent()

        val params = mapOf(
            "id" to vurdering.id,
            "deltaker_id" to vurdering.deltakerId,
            "opprettet_av_arrangor_ansatt_id" to vurdering.opprettetAvArrangorAnsattId,
            "vurderingstype" to vurdering.vurderingstype.name,
            "begrunnelse" to vurdering.begrunnelse,
            "gyldig_fra" to vurdering.gyldigFra,
        )

        val query = queryOf(sql, params)

        Database.query { session -> session.update(query) }
    }

    fun insert(forslag: Forslag) {
        val sql =
            """
            INSERT INTO forslag(id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
            VALUES (:id, :deltaker_id, :arrangoransatt_id, :opprettet, :begrunnelse, :endring, :status)
            ON CONFLICT (id) DO NOTHING;
            """.trimIndent()

        Database.query { session ->
            session.update(
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
    }

    fun insert(endringFraArrangor: EndringFraArrangor) {
        val sql =
            """
            INSERT INTO endring_fra_arrangor (id, deltaker_id, arrangor_ansatt_id, opprettet, endring)
            VALUES (:id, :deltaker_id, :arrangor_ansatt_id, :opprettet, :endring)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        Database.query { session ->
            session.update(
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
    }

    fun insert(importertFraArena: ImportertFraArena) {
        val sql =
            """
            INSERT INTO importert_fra_arena(deltaker_id, importert_dato, deltaker_ved_import)
            VALUES (:deltaker_id, :importert_dato, :deltaker_ved_import)
            ON CONFLICT (deltaker_id) DO NOTHING
            """.trimIndent()

        Database.query { session ->
            session.update(
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
    }

    fun <T> insertAll(vararg values: T) {
        values.forEach {
            when (it) {
                is NavAnsatt -> insert(it)
                is NavBruker -> insert(it)
                is NavEnhet -> NavEnhetRepository().upsert(it)
                is Arrangor -> ArrangorRepository().upsert(it)
                is Tiltakstype -> TiltakstypeRepository().upsert(it)
                is Deltakerliste -> insert(it)
                is Deltaker -> insert(it)
                is Vedtak -> insert(it)
                is Forslag -> insert(it)
                is DeltakerEndring -> insert(it)
                is EndringFraArrangor -> insert(it)
                is ImportertFraArena -> insert(it)
                is InnsokPaaFellesOppstart -> InnsokPaaFellesOppstartRepository().insert(it)
                is EndringFraTiltakskoordinator -> EndringFraTiltakskoordinatorRepository().insert(listOf(it))
                else -> NotImplementedError("insertAll for type ${it!!::class} er ikke implementert")
            }
        }
    }
}
