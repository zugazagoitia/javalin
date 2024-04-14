package io.javalin.community.ssl

import io.javalin.Javalin
import io.javalin.community.ssl.certs.Server
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException


@Tag("integration")
class TlsConfigTest : IntegrationTestClass() {
    @Test
    fun `test that a Modern TLS config does not allow old protocols`() {

        val protocols = TlsConfig.OLD.protocols.subtract(TlsConfig.MODERN.protocols.asIterable().toSet())

        // remove modern protocols from old protocols, so that ONLY unsupported protocols are left
        val client = clientWithTlsConfig(TlsConfig(TlsConfig.MODERN.cipherSuites, protocols.toTypedArray()))
        val securePort = ports.getAndIncrement()
        val https = HTTPS_URL_WITH_PORT.apply(securePort)
        createTestApp { config: SslConfig ->
            config.insecure = false
            config.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            config.securePort = securePort
            config.tlsConfig = TlsConfig.MODERN
        }.start().let { _ ->
            //Should fail with SSLHandshakeException because of the old protocols
            Assertions.assertThrows(SSLHandshakeException::class.java) {
                client.newCall(
                    Request.Builder().url(
                        https
                    ).build()
                ).execute()
            }
        }
    }

    @Test
    fun `test that a Modern TLS config does not allow old cipher suites`() {
        val cipherSuites = TlsConfig.OLD.cipherSuites.subtract(TlsConfig.MODERN.cipherSuites.asIterable().toSet())
        // remove modern cipher suites from old cipher suites, so that we can test ONLY the old cipher suites
        val client = clientWithTlsConfig(TlsConfig(cipherSuites.toTypedArray(), TlsConfig.MODERN.protocols))
        val securePort = ports.getAndIncrement()
        val https = HTTPS_URL_WITH_PORT.apply(securePort)
        createTestApp { config: SslConfig ->
            config.insecure = false
            config.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            config.securePort = securePort
            config.tlsConfig = TlsConfig.MODERN
        }.start().let { _ ->
            //Should fail with SSLHandshakeException because of the old cipher suites
            Assertions.assertThrows(SSLHandshakeException::class.java) {
                client.newCall(
                    Request.Builder().url(
                        https
                    ).build()
                ).execute()
            }
        }
    }

    @Test
    fun `test that an Intermediate TLS config does not allow old protocols`() {
        val protocols = TlsConfig.OLD.protocols.subtract(TlsConfig.INTERMEDIATE.protocols.asIterable().toSet())
        // remove intermediate protocols from old protocols, so that ONLY unsupported protocols are left
        val client = clientWithTlsConfig(TlsConfig(TlsConfig.INTERMEDIATE.cipherSuites, protocols.toTypedArray()))
        val securePort = ports.getAndIncrement()
        val https = HTTPS_URL_WITH_PORT.apply(securePort)
        createTestApp { config: SslConfig ->
            config.insecure = false
            config.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            config.securePort = securePort
            config.tlsConfig = TlsConfig.INTERMEDIATE
        }.start().let { _ ->
            //Should fail with SSLHandshakeException because of the old protocols
            Assertions.assertThrows(UnknownServiceException::class.java) {
                client.newCall(
                    Request.Builder().url(https).build()
                ).execute()
            }
        }
    }

    @Test
    fun `test that an Intermediate TLS config does not allow old cipher suites`() {
        val cipherSuites = TlsConfig.OLD.cipherSuites.subtract(TlsConfig.INTERMEDIATE.cipherSuites.asIterable().toSet())
        // remove intermediate cipher suites from old cipher suites, so that we can test ONLY the old cipher suites
        val client = clientWithTlsConfig(TlsConfig(cipherSuites.toTypedArray(), TlsConfig.INTERMEDIATE.protocols))
        val securePort = ports.getAndIncrement()
        val https = HTTPS_URL_WITH_PORT.apply(securePort)
        createTestApp { config: SslConfig ->
            config.insecure = false
            config.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            config.securePort = securePort
            config.tlsConfig = TlsConfig.INTERMEDIATE
        }.start().let { _ ->
            //Should fail with SSLHandshakeException because of the old cipher suites
            Assertions.assertThrows(SSLHandshakeException::class.java) {
                client.newCall(
                    Request.Builder().url(
                        https
                    ).build()
                ).execute()
            }
        }
    }

    @Test
    fun `test that TLS protocols can be reloaded at runtime`() {
        // First, we create a server supporting only TLSv1.3 and a client supporting only TLSv1.2
        val client = clientWithTlsConfig(TlsConfig(TlsConfig.OLD.cipherSuites, arrayOf("TLSv1.2")))
        val securePort = ports.getAndIncrement()
        val https = HTTPS_URL_WITH_PORT.apply(securePort)
        val plugin = SslPlugin {
            it.insecure = false
            it.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            it.securePort = securePort
            it.tlsConfig = TlsConfig(TlsConfig.MODERN.cipherSuites, arrayOf("TLSv1.3"))
        }
        val app = Javalin.create { javalinConfig: JavalinConfig ->
            javalinConfig.showJavalinBanner = false
            javalinConfig.registerPlugin(plugin)
            javalinConfig.router.mount {
                it.get("/", { ctx: Context -> ctx.result(SUCCESS) })
            }
        }.start()
        //Should fail with an exception because of the old protocol
        try {
            client.newCall(
                Request.Builder().url(
                    https
                ).build()
            ).execute()
        } catch (e: Exception) {
            Assertions.assertTrue(e.message!!.contains("protocol"))
        }


        // Then, we reload the server to support TLSv1.2
        plugin.reload {
            it.pemFromString(Server.CERTIFICATE_AS_STRING, Server.NON_ENCRYPTED_KEY_AS_STRING)
            it.tlsConfig = TlsConfig.MODERN // Support for TLSv1.2
        }
        client.connectionPool.evictAll()

        // Now, the client should be able to connect
        Assertions.assertDoesNotThrow {
            client.newCall(
                Request.Builder().url(
                    https
                ).build()
            ).execute()
        }

    }

    companion object {

        private fun clientWithTlsConfig(config: TlsConfig): OkHttpClient {


            val spec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .cipherSuites(*config.cipherSuites)
                .tlsVersions(*config.protocols)
                .build()

            return listOf(spec).let {
                OkHttpClient.Builder()
                    .connectionSpecs(it)
                    .build()
            }
        }
    }
}
