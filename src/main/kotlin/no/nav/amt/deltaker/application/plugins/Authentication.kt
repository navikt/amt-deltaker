package no.nav.amt.deltaker.application.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.auth.AuthenticationException
import java.net.URI
import java.util.UUID
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
                if (!erMaskinTilMaskin(credentials)) {
                    application.log.warn("Token med sluttbrukerkontekst har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                val appid: String = credentials.payload.getClaim("azp").asString()
                val app = environment.preAuthorizedApp.firstOrNull { it.clientId == appid }
                if (app?.appName !in listOf("amt-deltaker-bff", "amt-distribusjon", "amt-tiltaksarrangor-bff")) {
                    application.log.warn("App-id $appid med navn ${app?.appName} har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }

        jwt("EXTERNAL-SYSTEM") {
            verifier(jwkProvider, environment.jwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                if (!erMaskinTilMaskin(credentials)) {
                    application.log.warn("Token med sluttbrukerkontekst har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                val appid: String = credentials.payload.getClaim("azp").asString()
                val app = environment.preAuthorizedApp.firstOrNull { it.clientId == appid }
                if (app?.appName !in listOf("veilarboppfolging", "tiltakspenger-tiltak")) {
                    application.log.warn("App-id $appid med navn ${app?.appName} har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
        jwt("VEILEDER") {
            verifier(jwkProvider, environment.jwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                credentials["NAVident"] ?: run {
                    application.log.warn("Ikke tilgang. Mangler claim 'NAVident'.")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
    }
}

fun erMaskinTilMaskin(credentials: JWTCredential): Boolean {
    val sub: String = credentials.payload.getClaim("sub").asString()
    val oid: String = credentials.payload.getClaim("oid").asString()
    return sub == oid
}

fun ApplicationCall.getNavAnsattAzureId(): UUID = this
    .principal<JWTPrincipal>()
    ?.get("oid")
    ?.let { UUID.fromString(it) }
    ?: throw AuthenticationException("NavAnsattAzureId mangler i JWTPrincipal")
