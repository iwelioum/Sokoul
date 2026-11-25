package com.sokoul.server

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** Hand-built RS256 tokens and a JWKS document from a locally generated key pair. */
class TestJwt(val kid: String = "test-key") {
    val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    val jwks: String
        get() {
            val pub = keyPair.public as RSAPublicKey
            return """{"keys":[{"kid":"$kid","kty":"RSA","alg":"RS256","use":"sig",""" +
                """"n":"${b64(unsigned(pub.modulus))}","e":"${b64(unsigned(pub.publicExponent))}"}]}"""
        }

    fun token(payloadJson: String, alg: String = "RS256", kid: String = this.kid, signWith: KeyPair = keyPair): String {
        val header = b64("""{"alg":"$alg","kid":"$kid","typ":"JWT"}""".toByteArray())
        val payload = b64(payloadJson.toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(signWith.private)
            update("$header.$payload".toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "$header.$payload.${b64(signature)}"
    }

    companion object {
        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        private fun unsigned(n: BigInteger): ByteArray =
            n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
    }
}
