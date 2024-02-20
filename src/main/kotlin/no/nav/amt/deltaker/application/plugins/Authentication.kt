package no.nav.amt.deltaker.application.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.amt.deltaker.Environment
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureAuthentication(environment: Environment) {
    val jwkProvider = JwkProviderBuilder(URI(environment.jwkKeysUrl).toURL())
        .cached(5, 12, TimeUnit.HOURS)
        .build()

    install(Authentication) {
        jwt("SYSTEM") {
            verifier(jwkProvider, environment.jwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                val appid: String = credentials.payload.getClaim("azp").asString()
                val app = environment.preAuthorizedApp.firstOrNull { it.clientId == appid }
                if (app?.appName != "amt-deltaker-bff") {
                    application.log.warn("App-id $appid med navn ${app?.appName} har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
    }
}
