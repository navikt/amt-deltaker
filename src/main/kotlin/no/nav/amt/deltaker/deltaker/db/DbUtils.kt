package no.nav.amt.deltaker.deltaker.db

import java.time.Duration
import java.time.LocalDateTime

object DbUtils {
    /**
     * Returnerer `null` dersom [dateTime] er "nær nok" nåværende tidspunkt, ellers returneres
     * den opprinnelige verdien.
     *
     * Brukes for å signalisere at en tidsverdi kan erstattes med `CURRENT_TIMESTAMP`
     * i databasen, i stedet for å sette tidspunktet eksplisitt fra applikasjonen.
     *
     * "Nær nok" defineres som at differansen mellom [dateTime] og [LocalDateTime.now()]
     * er mindre enn eller lik [graceInSeconds] sekunder.
     *
     * @param dateTime tidspunktet som skal vurderes.
     * @param graceInSeconds antall sekunder tidsforskjellen kan være for å anses som "nær nå".
     *                       Standard er 5 sekunder.
     * @return `null` dersom tidspunktet er innenfor gitt toleranse fra nå,
     *         ellers [dateTime].
     */
    fun nullWhenNearNow(dateTime: LocalDateTime, graceInSeconds: Long = 5): LocalDateTime? = if (Duration
            .between(dateTime, LocalDateTime.now())
            .abs() <= Duration.ofSeconds(graceInSeconds)
    ) {
        null
    } else {
        dateTime
    }
}
