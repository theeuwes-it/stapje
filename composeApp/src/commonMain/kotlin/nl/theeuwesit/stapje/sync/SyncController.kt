package nl.theeuwesit.stapje.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.Repository

/** Inlogstatus en synchronisatie, als Compose-state voor de schermen. */
class SyncController(
    private val client: SupabaseClient,
    private val repo: Repository,
    /** Waar Google na het inloggen naar terugstuurt; null = standaard (Android-deeplink). */
    private val redirectUrl: String?,
    private val now: () -> LocalDateTime,
) {
    var signedIn by mutableStateOf(false)
        private set
    var email by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var lastSynced by mutableStateOf<LocalDateTime?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val mutex = Mutex()

    /** Blijft lopen; roept [onSignedIn] aan zodra er (opnieuw) een sessie is. */
    suspend fun observeSession(onSignedIn: suspend () -> Unit) {
        client.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val wasSignedIn = signedIn
                    signedIn = true
                    Cloud.clearLoginError()
                    error = null
                    email = status.session.user?.email
                    if (!wasSignedIn) onSignedIn()
                }

                is SessionStatus.NotAuthenticated -> {
                    signedIn = false
                    email = null
                }

                else -> Unit
            }
        }
    }

    /** Blijft lopen; toont fouten bij de terugkeer na inloggen. */
    suspend fun observeLoginErrors() {
        Cloud.loginError.collect { message -> if (message != null) error = message }
    }

    suspend fun signIn() {
        error = null
        Cloud.clearLoginError()
        try {
            val url = redirectUrl
            if (url != null) client.auth.signInWith(Google, redirectUrl = url) else client.auth.signInWith(Google)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = "Inloggen is niet gelukt: ${e.message ?: e::class.simpleName}"
        }
    }

    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Lokaal is de sessie toch weg; niets aan te doen.
        }
        signedIn = false
        email = null
        lastSynced = null
    }

    /** @return true als er lokaal gegevens zijn veranderd (scherm moet herladen). */
    suspend fun syncNow(): Boolean {
        if (!signedIn) return false
        return mutex.withLock {
            syncing = true
            try {
                val changed = CloudSync.sync(client, repo)
                lastSynced = now()
                error = null
                changed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = "Synchroniseren is niet gelukt. Controleer je internetverbinding."
                false
            } finally {
                syncing = false
            }
        }
    }
}
