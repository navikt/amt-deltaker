package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Query
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.db.DbUtils.nullWhenNearNow
import no.nav.amt.deltaker.deltaker.model.AVSLUTTENDE_STATUSER
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.ArenaKode
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.time.LocalDate
import java.util.UUID

class DeltakerRepository {
    private fun rowMapper(row: Row): Deltaker {
        val status = row.string("ds.type").let { DeltakerStatus.Type.valueOf(it) }
        val deltaker = Deltaker(
            id = row.uuid("d.id"),
            navBruker = NavBruker(
                personId = row.uuid("d.person_id"),
                personident = row.string("nb.personident"),
                fornavn = row.string("nb.fornavn"),
                mellomnavn = row.stringOrNull("nb.mellomnavn"),
                etternavn = row.string("nb.etternavn"),
                navVeilederId = row.uuidOrNull("nb.nav_veileder_id"),
                navEnhetId = row.uuidOrNull("nb.nav_enhet_id"),
                telefon = row.stringOrNull("nb.telefonnummer"),
                epost = row.stringOrNull("nb.epost"),
                erSkjermet = row.boolean("nb.er_skjermet"),
                adresse = row.stringOrNull("nb.adresse")?.let { objectMapper.readValue(it) },
                adressebeskyttelse = row.stringOrNull("nb.adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
                oppfolgingsperioder = row.stringOrNull("nb.oppfolgingsperioder")?.let { objectMapper.readValue(it) } ?: emptyList(),
                innsatsgruppe = row.stringOrNull("nb.innsatsgruppe")?.let { Innsatsgruppe.valueOf(it) },
            ),
            deltakerliste = DeltakerlisteRepository.rowMapper(row),
            startdato = row.localDateOrNull("d.startdato"),
            sluttdato = row.localDateOrNull("d.sluttdato"),
            dagerPerUke = row.floatOrNull("d.dager_per_uke"),
            deltakelsesprosent = row.floatOrNull("d.deltakelsesprosent"),
            bakgrunnsinformasjon = row.stringOrNull("d.bakgrunnsinformasjon"),
            deltakelsesinnhold = row.stringOrNull("d.innhold")?.let { objectMapper.readValue(it) },
            status = DeltakerStatus(
                id = row.uuid("ds.id"),
                type = row.string("ds.type").let { DeltakerStatus.Type.valueOf(it) },
                aarsak = row.stringOrNull("ds.aarsak")?.let { objectMapper.readValue(it) },
                gyldigFra = row.localDateTime("ds.gyldig_fra"),
                gyldigTil = row.localDateTimeOrNull("ds.gyldig_til"),
                opprettet = row.localDateTime("ds.created_at"),
            ),
            vedtaksinformasjon = row.localDateTimeOrNull("v.opprettet")?.let { opprettet ->
                Vedtaksinformasjon(
                    fattet = row.localDateTimeOrNull("v.fattet"),
                    fattetAvNav = row.boolean("v.fattet_av_nav"),
                    opprettet = opprettet,
                    opprettetAv = row.uuid("v.opprettet_av"),
                    opprettetAvEnhet = row.uuid("v.opprettet_av_enhet"),
                    sistEndret = row.localDateTime("v.sist_endret"),
                    sistEndretAv = row.uuid("v.sist_endret_av"),
                    sistEndretAvEnhet = row.uuid("v.sist_endret_av_enhet"),
                )
            },
            sistEndret = row.localDateTime("d.modified_at"),
            kilde = Kilde.valueOf(row.string("d.kilde")),
            erManueltDeltMedArrangor = row.boolean("d.er_manuelt_delt_med_arrangor"),
            opprettet = row.localDateTime("d.created_at"),
        )

        return if (status == DeltakerStatus.Type.FEILREGISTRERT) {
            deltaker.copy(
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = null,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
            )
        } else {
            deltaker
        }
    }

    private fun deltakerStatusRowMapper(row: Row): DeltakerStatus = DeltakerStatus(
        id = row.uuid("id"),
        type = row.string("type").let { t -> DeltakerStatus.Type.valueOf(t) },
        aarsak = row.stringOrNull("aarsak")?.let { aarsak -> objectMapper.readValue(aarsak) },
        gyldigFra = row.localDateTime("gyldig_fra"),
        gyldigTil = row.localDateTimeOrNull("gyldig_til"),
        opprettet = row.localDateTime("created_at"),
    )

    fun upsert(
        deltaker: Deltaker,
        fremtidigStatus: DeltakerStatus? = null,
        transactionalSession: TransactionalSession,
    ) {
        val sql =
            """
            insert into deltaker(
                id, person_id, deltakerliste_id, startdato, sluttdato, dager_per_uke, 
                deltakelsesprosent, bakgrunnsinformasjon, innhold, kilde, modified_at,
                er_manuelt_delt_med_arrangor
            )
            values (
                :id, :person_id, :deltakerlisteId, :startdato, :sluttdato, :dagerPerUke, 
                :deltakelsesprosent, :bakgrunnsinformasjon, :innhold, :kilde, :modified_at,
                :er_manuelt_delt_med_arrangor
            )
            on conflict (id) do update set 
                person_id          = :person_id,
                startdato            = :startdato,
                sluttdato            = :sluttdato,
                dager_per_uke        = :dagerPerUke,
                deltakelsesprosent   = :deltakelsesprosent,
                bakgrunnsinformasjon = :bakgrunnsinformasjon,
                innhold              = :innhold,
                kilde                = :kilde,
                modified_at          = :modified_at,
                er_manuelt_delt_med_arrangor = :er_manuelt_delt_med_arrangor
            """.trimIndent()

        val parameters = mapOf(
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
            "modified_at" to deltaker.sistEndret,
            "er_manuelt_delt_med_arrangor" to deltaker.erManueltDeltMedArrangor,
        )

        transactionalSession.update(queryOf(sql, parameters))

        lagreStatus(deltaker, transactionalSession)
        fremtidigStatus?.let { transactionalSession.update(insertStatusQuery(it, deltaker.id)) }
    }

    fun lagreStatus(deltaker: Deltaker, transactionalSession: TransactionalSession) {
        transactionalSession.update(insertStatusQuery(deltaker.status, deltaker.id))

        val erNyStatusAktiv = deltaker.status.gyldigFra.toLocalDate() <= LocalDate.now()

        if (erNyStatusAktiv) {
            transactionalSession.update(deaktiverTidligereStatuserQuery(deltaker.status, deltaker.id))
        } else {
            // Dette skjer aldri for arenadeltakelser
            transactionalSession.update(slettTidligereFremtidigeStatuserQuery(deltaker.status, deltaker.id))
        }
    }

    fun upsert(deltaker: Deltaker, nesteStatus: DeltakerStatus? = null) = Database.query { session ->
        session.transaction { upsert(deltaker, nesteStatus, it) }
    }

    fun get(id: UUID) = Database.query {
        val sql = getDeltakerSql("where d.id = :id and ds.gyldig_til is null and ds.gyldig_fra < CURRENT_TIMESTAMP")

        val query = queryOf(sql, mapOf("id" to id)).map(::rowMapper).asSingle
        it.run(query)?.let { d -> Result.success(d) }
            ?: Result.failure(NoSuchElementException("Ingen deltaker med id $id"))
    }

    fun getMany(deltakerIder: List<UUID>) = Database.query {
        val sql = getDeltakerSql(
            """ where ds.gyldig_til is null
                and ds.gyldig_fra < CURRENT_TIMESTAMP
                and d.id = any(:ider)
            """.trimMargin(),
        )

        val query = queryOf(sql, mapOf("ider" to deltakerIder.toTypedArray())).map(::rowMapper).asList

        it.run(query)
    }

    fun getFlereForPerson(personIdent: String, deltakerlisteId: UUID) = Database.query {
        val sql = getDeltakerSql(
            """ where nb.personident = :personident 
                    and d.deltakerliste_id = :deltakerliste_id 
                    and ds.gyldig_til is null
                    and ds.gyldig_fra < CURRENT_TIMESTAMP
            """.trimMargin(),
        )

        val query = queryOf(
            sql,
            mapOf(
                "personident" to personIdent,
                "deltakerliste_id" to deltakerlisteId,
            ),
        ).map(::rowMapper).asList
        it.run(query)
    }

    fun getDeltakereForDeltakerliste(deltakerlisteId: UUID) = Database.query {
        val sql = getDeltakerSql(
            """ where d.deltakerliste_id = :deltakerliste_id 
                    and ds.gyldig_til is null
                    and ds.gyldig_fra < CURRENT_TIMESTAMP
            """.trimMargin(),
        )

        val query = queryOf(
            sql,
            mapOf(
                "deltakerliste_id" to deltakerlisteId,
            ),
        ).map(::rowMapper).asList
        it.run(query)
    }

    fun getDeltakerIderForTiltakstype(tiltakstype: ArenaKode) = Database.query { session ->
        val sql =
            """ 
                select d.id as "d.id"
                from deltaker d
                    join deltakerliste dl on d.deltakerliste_id = dl.id
                    join tiltakstype t on t.id = dl.tiltakstype_id
                where t.type = :tiltakstype;
            """.trimMargin()

        val query = queryOf(
            sql,
            mapOf(
                "tiltakstype" to tiltakstype.name,
            ),
        ).map {
            it.uuid("d.id")
        }.asList
        session.run(query)
    }

    fun getFlereForPerson(personIdent: String) = Database.query {
        val sql = getDeltakerSql(
            """ where nb.personident = :personident
                    and ds.gyldig_til is null
                    and ds.gyldig_fra < CURRENT_TIMESTAMP
            """.trimMargin(),
        )

        val query = queryOf(
            sql,
            mapOf(
                "personident" to personIdent,
            ),
        ).map(::rowMapper).asList
        it.run(query)
    }

    fun getDeltakerStatuser(deltakerId: UUID) = Database.query { session ->
        val sql =
            """
            select * from deltaker_status where deltaker_id = :deltaker_id
            """.trimIndent()

        val query = queryOf(sql, mapOf("deltaker_id" to deltakerId))
            .map(::deltakerStatusRowMapper)
            .asList

        session.run(query)
    }

    /**
     * Henter alle deltakerstatuser som representerer en avslutning
     * (f.eks. `AVBRUTT`, `FULLFORT` eller `HAR_SLUTTET`) og som er gyldige for oppdatering.
     *
     * Spørringen fungerer slik:
     * - Først finner vi alle deltakere som har en aktiv status av typen `DELTAR` (dvs. uten `gyldig_til`).
     * - Deretter henter vi alle statuser knyttet til disse deltakerne som:
     *   - ikke har en avslutningsdato (`gyldig_til IS NULL`),
     *   - har startet (`gyldig_fra <= dagens dato`),
     *   - og er en av de avsluttende statusene (`AVBRUTT`, `FULLFORT`, `HAR_SLUTTET`).
     *
     * @return en liste av [DeltakerStatusMedDeltakerId] som inneholder både deltaker-id og
     *         den tilhørende avsluttende statusen som bør oppdateres.
     */
    fun getAvsluttendeDeltakerStatuserForOppdatering() = Database.query { session ->
        val sql =
            """
            WITH aktive_deltagelser AS (
                SELECT deltaker_id  
                FROM deltaker_status
                WHERE 
                    gyldig_til is null
                    AND type = 'DELTAR'
            )
            
            SELECT * 
            FROM deltaker_status ds
            JOIN aktive_deltagelser ad ON ds.deltaker_id = ad.deltaker_id
            WHERE 
                gyldig_til IS NULL
                AND gyldig_fra::date <= current_date
                AND type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET')
            """.trimIndent()

        val query = queryOf(sql)
            .map {
                DeltakerStatusMedDeltakerId(
                    deltakerId = it.uuid("deltaker_id"),
                    deltakerStatus = deltakerStatusRowMapper(it),
                )
            }.asList

        session.run(query)
    }

    fun deleteDeltakerOgStatus(deltakerId: UUID) = Database.query { session ->
        session.transaction { tx ->
            tx.update(slettStatus(deltakerId))
            tx.update(slettDeltaker(deltakerId))
        }
    }

    fun skalHaStatusDeltar(): List<Deltaker> = Database.query { session ->
        val sql = getDeltakerSql(
            """ where ds.gyldig_til is null
                and ds.gyldig_fra < CURRENT_TIMESTAMP
                and ds.type = :status
                and d.startdato <= CURRENT_DATE
                and (d.sluttdato is null or d.sluttdato >= CURRENT_DATE)
            """.trimMargin(),
        )

        val query = queryOf(sql, mapOf("status" to DeltakerStatus.Type.VENTER_PA_OPPSTART.name))
            .map(::rowMapper)
            .asList

        session.run(query)
    }

    fun skalHaAvsluttendeStatus(): List<Deltaker> = Database.query { session ->
        val deltakerstatuser = listOf(
            DeltakerStatus.Type.VENTER_PA_OPPSTART.name,
            DeltakerStatus.Type.DELTAR.name,
        )

        val sql = getDeltakerSql(
            """ where ds.gyldig_til is null
                and ds.gyldig_fra < CURRENT_TIMESTAMP
                and ds.type in (${deltakerstatuser.joinToString { "?" }})
                and d.sluttdato < CURRENT_DATE
            """.trimMargin(),
        )

        val query = queryOf(
            sql,
            *deltakerstatuser.toTypedArray(),
        ).map(::rowMapper)
            .asList

        session.run(query)
    }

    fun deltarPaAvsluttetDeltakerliste(): List<Deltaker> = Database.query { session ->
        val avsluttendeDeltakerStatuser = AVSLUTTENDE_STATUSER.map { it.name }
        val avsluttendeDeltakerlisteStatuser = listOf(
            Deltakerliste.Status.AVSLUTTET.name,
            Deltakerliste.Status.AVBRUTT.name,
            Deltakerliste.Status.AVLYST.name,
        )
        val sql = getDeltakerSql(
            """ where ds.gyldig_til is null
                and ds.gyldig_fra < CURRENT_TIMESTAMP
                and ds.type not in (${avsluttendeDeltakerStatuser.joinToString { "?" }})
                and dl.status in (${avsluttendeDeltakerlisteStatuser.joinToString { "?" }})
            """.trimMargin(),
        )

        val query = queryOf(
            sql,
            *avsluttendeDeltakerStatuser.toTypedArray(),
            *avsluttendeDeltakerlisteStatuser.toTypedArray(),
        ).map(::rowMapper)
            .asList

        session.run(query)
    }

    fun getDeltakereMedStatus(statusType: DeltakerStatus.Type) = Database.query { session ->
        val sql =
            """
                select d.id as "d.id"
                from deltaker d
                    join deltaker_status ds on d.id = ds.deltaker_id
                where ds.type = :status_type
                and ds.gyldig_til is null
                and ds.gyldig_fra < CURRENT_TIMESTAMP
            """.trimMargin()

        val query = queryOf(
            sql,
            mapOf(
                "status_type" to statusType.name,
            ),
        ).map {
            it.uuid("d.id")
        }.asList
        session.run(query)
    }

    private fun slettStatus(deltakerId: UUID): Query {
        val sql =
            """
            delete from deltaker_status
            where deltaker_id = :deltaker_id;
            """.trimIndent()

        val params = mapOf(
            "deltaker_id" to deltakerId,
        )

        return queryOf(sql, params)
    }

    private fun slettDeltaker(deltakerId: UUID): Query {
        val sql =
            """
            delete from deltaker
            where id = :deltaker_id;
            """.trimIndent()

        val params = mapOf(
            "deltaker_id" to deltakerId,
        )

        return queryOf(sql, params)
    }

    /**
     * Oppretter en [Query] for å sette inn en ny rad i tabellen `deltaker_status`.
     *
     * - Feltet `gyldig_fra` settes enten til verdien fra [DeltakerStatus.gyldigFra], eller til
     *   `CURRENT_TIMESTAMP` i databasen dersom tidspunktet er "nær nok" nåværende tidspunkt
     *   (se [nullWhenNearNow]).
     *
     * - Feltet `created_at` settes tilsvarende, basert på [DeltakerStatus.opprettet].
     *
     * - Konflikter på primærnøkkelen `id` ignoreres (`ON CONFLICT (id) DO NOTHING`).
     *
     * @param status statusobjektet som skal lagres i databasen.
     * @param deltakerId ID-en til deltakeren statusen tilhører.
     * @return en ferdig parametrisert [Query] som kan kjøres mot databasen.
     */
    private fun insertStatusQuery(status: DeltakerStatus, deltakerId: UUID): Query {
        val sql =
            """
            INSERT INTO deltaker_status(id, deltaker_id, type, aarsak, gyldig_fra, created_at)
            VALUES (:id, :deltaker_id, :type, :aarsak, COALESCE(:gyldig_fra, CURRENT_TIMESTAMP), COALESCE(:created_at, CURRENT_TIMESTAMP))
            ON CONFLICT (id) DO NOTHING;
            """.trimIndent()

        val params = mapOf(
            "id" to status.id,
            "deltaker_id" to deltakerId,
            "type" to status.type.name,
            "aarsak" to toPGObject(status.aarsak),
            "gyldig_fra" to nullWhenNearNow(status.gyldigFra),
            "created_at" to nullWhenNearNow(status.opprettet),
        )

        return queryOf(sql, params)
    }

    private fun deaktiverTidligereStatuserQuery(status: DeltakerStatus, deltakerId: UUID): Query {
        val sql =
            """
            update deltaker_status
            set gyldig_til = current_timestamp, modified_at = current_timestamp
            where deltaker_id = :deltaker_id 
              and id != :id 
              and gyldig_til is null;
            """.trimIndent()

        return queryOf(sql, mapOf("id" to status.id, "deltaker_id" to deltakerId))
    }

    private fun slettTidligereFremtidigeStatuserQuery(status: DeltakerStatus, deltakerId: UUID): Query {
        val sql =
            """
            delete from deltaker_status
            where deltaker_id = :deltaker_id 
              and id != :id 
              and gyldig_til is null
              and gyldig_fra > CURRENT_TIMESTAMP;
            """.trimIndent()

        return queryOf(sql, mapOf("id" to status.id, "deltaker_id" to deltakerId))
    }

    private fun getDeltakerSql(where: String = "") = """
        SELECT 
            d.id as "d.id",
            d.person_id as "d.person_id",
            d.deltakerliste_id as "d.deltakerliste_id",
            d.startdato as "d.startdato",
            d.sluttdato as "d.sluttdato",
            d.dager_per_uke as "d.dager_per_uke",
            d.deltakelsesprosent as "d.deltakelsesprosent",
            d.bakgrunnsinformasjon as "d.bakgrunnsinformasjon",
            d.innhold as "d.innhold",
            d.modified_at as "d.modified_at",
            d.kilde as "d.kilde",
            d.er_manuelt_delt_med_arrangor as "d.er_manuelt_delt_med_arrangor",
            d.created_at as "d.created_at",
            nb.personident as "nb.personident",
            nb.fornavn as "nb.fornavn",
            nb.mellomnavn as "nb.mellomnavn",
            nb.etternavn as "nb.etternavn",
            nb.nav_veileder_id as "nb.nav_veileder_id",
            nb.nav_enhet_id as "nb.nav_enhet_id",
            nb.telefonnummer as "nb.telefonnummer",
            nb.epost as "nb.epost",
            nb.er_skjermet as "nb.er_skjermet",
            nb.adresse as "nb.adresse",
            nb.adressebeskyttelse as "nb.adressebeskyttelse",
            nb.oppfolgingsperioder as "nb.oppfolgingsperioder",
            nb.innsatsgruppe as "nb.innsatsgruppe",
            ds.id as "ds.id",
            ds.deltaker_id as "ds.deltaker_id",
            ds.type as "ds.type",
            ds.aarsak as "ds.aarsak",
            ds.gyldig_fra as "ds.gyldig_fra",
            ds.gyldig_til as "ds.gyldig_til",
            ds.created_at as "ds.created_at",
            ds.modified_at as "ds.modified_at",
            dl.id as "dl.id",
            dl.navn as "dl.navn",
            dl.status as "dl.status",
            dl.start_dato as "dl.start_dato",
            dl.slutt_dato as "dl.slutt_dato",
            dl.oppstart as "dl.oppstart",
            dl.apent_for_pamelding as "dl.apent_for_pamelding",
            dl.oppmote_sted as "dl.oppmote_sted",
            a.navn as "a.navn",
            a.id as "a.id",
            a.organisasjonsnummer as "a.organisasjonsnummer",
            a.overordnet_arrangor_id as "a.overordnet_arrangor_id",
            t.id as "t.id",
            t.navn as "t.navn",
            t.tiltakskode as "t.tiltakskode",
            t.type as "t.type",
            t.innsatsgrupper as "t.innsatsgrupper",
            t.innhold as "t.innhold",
            v.fattet as "v.fattet",
            v.fattet_av_nav as "v.fattet_av_nav",
            v.created_at as "v.opprettet",
            v.opprettet_av as "v.opprettet_av",
            v.opprettet_av_enhet as "v.opprettet_av_enhet",
            v.modified_at as "v.sist_endret",
            v.sist_endret_av as "v.sist_endret_av",
            v.sist_endret_av_enhet as "v.sist_endret_av_enhet"
        FROM 
            deltaker d 
            JOIN nav_bruker nb ON d.person_id = nb.person_id
            JOIN deltaker_status ds ON d.id = ds.deltaker_id
            JOIN deltakerliste dl ON d.deltakerliste_id = dl.id
            JOIN arrangor a ON a.id = dl.arrangor_id
            JOIN tiltakstype t ON t.id = dl.tiltakstype_id
            LEFT JOIN vedtak v ON d.id = v.deltaker_id and v.gyldig_til is null
            $where
      """
}
