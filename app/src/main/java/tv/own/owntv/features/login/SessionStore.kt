package tv.own.owntv.features.login

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The signed-in Xtream account. */
data class Session(val username: String, val password: String)

/** What MainActivity routes on: still reading, nobody signed in, or signed in. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: Session) : SessionState
}

private val Context.sessionDataStore by preferencesDataStore(name = "brand_session")

/**
 * 8K PRO: keeps the login between launches.
 *
 * Stored in a Preferences DataStore. The username is plain text; the password is encrypted with
 * AES-256-GCM under a key that lives in the Android Keystore (hardware-backed where the device has it)
 * and never leaves it, so the DataStore file on its own does not reveal the password.
 *
 * If the key is gone (app data restored onto another device, Keystore reset) the password cannot be
 * decrypted: the session then reads as logged out and the user simply signs in again.
 */
class SessionStore(context: Context) {
    private val store = context.applicationContext.sessionDataStore

    val state: Flow<SessionState> = store.data.map { prefs ->
        val user = prefs[KEY_USERNAME]
        val sealed = prefs[KEY_PASSWORD]
        if (user.isNullOrEmpty() || sealed.isNullOrEmpty()) return@map SessionState.LoggedOut
        val password = runCatching { KeystoreCipher.decrypt(sealed) }
            .onFailure { Log.w(TAG, "stored password unreadable, signing out", it) }
            .getOrNull()
            ?: return@map SessionState.LoggedOut
        SessionState.LoggedIn(Session(user, password))
    }

    suspend fun current(): Session? = (state.first() as? SessionState.LoggedIn)?.session

    suspend fun save(session: Session) {
        val sealed = KeystoreCipher.encrypt(session.password)
        store.edit {
            it[KEY_USERNAME] = session.username
            it[KEY_PASSWORD] = sealed
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        const val TAG = "SessionStore"
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password_enc")
    }
}

/** AES/GCM with a non-exportable Android Keystore key. Output: Base64(iv ‖ ciphertext+tag). */
private object KeystoreCipher {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "brand_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }
}
