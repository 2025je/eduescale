package pe.eduescale.mobile.data

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.InflaterInputStream

class EduEscalePasswordRequiredException :
    IllegalArgumentException("Este archivo está protegido y requiere una contraseña.")

class EduEscaleInvalidPasswordException(cause: Throwable? = null) :
    IllegalArgumentException("La contraseña es incorrecta o el archivo está dañado.", cause)

object EduEscaleEncryption {
    private val MAGIC = "EDUSCENC".toByteArray(StandardCharsets.US_ASCII)
    private const val ENVELOPE_VERSION = 1
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE_BITS = 128
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val HEADER_SIZE = 8 + 1 + 4 + SALT_SIZE + NONCE_SIZE

    @Throws(IOException::class, IllegalArgumentException::class)
    fun openDecoded(source: InputStream, password: String? = null): InputStream {
        val input = BufferedInputStream(source)
        input.mark(HEADER_SIZE)
        val magic = ByteArray(MAGIC.size)
        var magicBytesRead = 0
        while (magicBytesRead < magic.size) {
            val count = input.read(magic, magicBytesRead, magic.size - magicBytesRead)
            if (count < 0) break
            magicBytesRead += count
        }
        input.reset()

        if (magicBytesRead != magic.size || !magic.contentEquals(MAGIC)) {
            return input
        }
        if (password.isNullOrEmpty()) {
            throw EduEscalePasswordRequiredException()
        }

        val header = ByteArray(HEADER_SIZE)
        readFully(input, header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        buffer.position(MAGIC.size)
        val envelopeVersion = buffer.get().toInt() and 0xff
        if (envelopeVersion != ENVELOPE_VERSION) {
            throw IllegalArgumentException(
                "Versión de cifrado no compatible: $envelopeVersion.",
            )
        }
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw IllegalArgumentException("El archivo tiene parámetros de cifrado no válidos.")
        }
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_SIZE).also { buffer.get(it) }

        try {
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            cipher.updateAAD(header)
            val decrypted = CipherInputStream(input, cipher)
            return AuthenticatedInputStream(InflaterInputStream(decrypted), decrypted)
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("No se pudo descifrar el archivo.", error)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(
                password.toByteArray(StandardCharsets.UTF_8),
                "HmacSHA256",
            ),
        )
        val blockInput = ByteBuffer.allocate(salt.size + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(salt)
            .putInt(1)
            .array()
        var u = mac.doFinal(blockInput)
        val result = u.copyOf()
        var iteration = 1
        while (iteration < iterations) {
            u = mac.doFinal(u)
            var index = 0
            while (index < result.size) {
                result[index] = (result[index].toInt() xor u[index].toInt()).toByte()
                index += 1
            }
            iteration += 1
        }
        return result
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) {
                throw IllegalArgumentException("El archivo cifrado está incompleto.")
            }
            offset += count
        }
    }

    private class AuthenticatedInputStream(
        input: InputStream,
        private val encryptedPayload: InputStream,
    ) : FilterInputStream(input) {
        private var authenticated = false

        override fun read(): Int {
            val value = try {
                super.read()
            } catch (error: IOException) {
                throw translateAuthenticationError(error)
            }
            if (value < 0) verifyAuthentication()
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = try {
                super.read(buffer, offset, length)
            } catch (error: IOException) {
                throw translateAuthenticationError(error)
            }
            if (count < 0) verifyAuthentication()
            return count
        }

        override fun close() {
            try {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (read(buffer) < 0) break
                }
            } finally {
                super.close()
            }
        }

        private fun verifyAuthentication() {
            if (authenticated) return
            try {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = encryptedPayload.read(buffer)
                    if (count < 0) break
                    if (count > 0) throw EduEscaleInvalidPasswordException()
                }
                authenticated = true
            } catch (error: IOException) {
                throw translateAuthenticationError(error)
            }
        }

        private fun translateAuthenticationError(error: IOException): RuntimeException =
            EduEscaleInvalidPasswordException(error)
    }
}
