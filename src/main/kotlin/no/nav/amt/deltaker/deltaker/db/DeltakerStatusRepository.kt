package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Query
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.db.DbUtils.nullWhenNearNow
import no.nav.amt.deltaker.deltaker.db.DbUtils.sqlPlaceholders
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

object DeltakerStatusRepository {
    /**
     * Setter inn en ny rad i tabellen `deltaker_status`.
     *
     * - Feltet `gyldig_fra` settes enten til verdien fra [DeltakerStatus.gyldigFra], eller til
     *   `CURRENT_TIMESTAMP` i databasen dersom tidspunktet er "nær nok" nåværende tidspunkt
     *   (se [nullWhenNearNow]).
     *
     * - Feltet `created_at` settes tilsvarende, basert på [DeltakerStatus.opprettet].
     *
     * - Konflikter på primærnøkkelen `id` ignoreres (`ON CONFLICT (id) DO NOTHING`).
     *
     * @param deltakerStatus status-objektet som skal lagres i databasen.
     * @param deltakerId ID-en til deltakeren statusen tilhører.
     * @return en ferdig parametrisert [Query] som kan kjøres mot databasen.
     */
    fun lagreStatus(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        val sql =
            """
            INSERT INTO deltaker_status (
                id, 
                deltaker_id, 
                type, 
                aarsak, 
                gyldig_fra, 
                created_at
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :type, 
                :aarsak, 
                COALESCE(:gyldig_fra, CURRENT_TIMESTAMP), 
                COALESCE(:created_at, CURRENT_TIMESTAMP)
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerStatus.id,
            "deltaker_id" to deltakerId,
            "type" to deltakerStatus.type.name,
            "aarsak" to toPGObject(deltakerStatus.aarsak),
            "gyldig_fra" to nullWhenNearNow(deltakerStatus.gyldigFra),
            "created_at" to nullWhenNearNow(deltakerStatus.opprettet),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun deaktiverTidligereStatuser(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        val sql =
            """
            UPDATE deltaker_status
            SET 
                gyldig_til = CURRENT_TIMESTAMP,
                modified_at = CURRENT_TIMESTAMP
            WHERE 
                deltaker_id = :deltaker_id 
                AND id != :id 
                AND gyldig_til IS NULL
            """.trimIndent()

        return Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf("id" to deltakerStatus.id, "deltaker_id" to deltakerId),
                ),
            )
        }
    }

    fun slettTidligereFremtidigeStatuser(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        val sql =
            """
            DELETE FROM deltaker_status
            WHERE 
                deltaker_id = :deltaker_id 
                AND id != :id 
                AND gyldig_til IS NULL
                AND gyldig_fra > CURRENT_TIMESTAMP
            """.trimIndent()

        return Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf("id" to deltakerStatus.id, "deltaker_id" to deltakerId),
                ),
            )
        }
    }

    fun getDeltakerStatuser(deltakerId: UUID): List<DeltakerStatus> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM deltaker_status WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map(::deltakerStatusRowMapper).asList,
        )
    }

    fun slettStatus(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltaker_status WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
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
    fun getAvsluttendeDeltakerStatuserForOppdatering(deltakerIdListe: List<UUID>): List<DeltakerStatusMedDeltakerId> {
        if (deltakerIdListe.isEmpty()) return emptyList()

        val sql =
            """
            SELECT ds.* 
            FROM deltaker_status ds 
            WHERE 
                ds.deltaker_id IN (${sqlPlaceholders(deltakerIdListe.size)})
                AND ds.gyldig_til IS NULL 
                AND ds.gyldig_fra < current_date + interval '1 day' 
                AND ds.type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET') 
                AND EXISTS ( 
                    SELECT 1 
                    FROM deltaker_status AS inner_ds 
                    WHERE 
                        inner_ds.deltaker_id = ds.deltaker_id 
                        AND inner_ds.gyldig_til IS NULL 
                        AND inner_ds.type = 'DELTAR'
                )
            """.trimIndent()

        val query = queryOf(
            sql,
            *deltakerIdListe.toTypedArray(),
        ).map {
            DeltakerStatusMedDeltakerId(
                deltakerId = it.uuid("deltaker_id"),
                deltakerStatus = deltakerStatusRowMapper(it),
            )
        }.asList

        return Database.query { session -> session.run(query) }
    }

    fun deltakerStatusRowMapper(row: Row) = DeltakerStatus(
        id = row.uuid("id"),
        type = row.string("type").let { t -> DeltakerStatus.Type.valueOf(t) },
        aarsak = row.stringOrNull("aarsak")?.let { aarsak -> objectMapper.readValue(aarsak) },
        gyldigFra = row.localDateTime("gyldig_fra"),
        gyldigTil = row.localDateTimeOrNull("gyldig_til"),
        opprettet = row.localDateTime("created_at"),
    )
}
