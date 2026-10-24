package tv.own.owntv.features.profiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the cold-start ordering between DataStore and Room. */
class ProfileLaunchGateTest {
    @Test
    fun `only one unlocked profile bypasses the launch gate`() {
        assertFalse(profileGateRequired(listOf(fakeProfile(1L))))
        assertTrue(profileGateRequired(listOf(fakeProfile(1L).copy(pinHash = "locked"))))
        assertTrue(profileGateRequired(listOf(fakeProfile(1L), fakeProfile(2L))))
    }

    @Test
    fun `single unlocked profile enters shell immediately without authentication`() {
        assertTrue(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = false,
            ),
        )
    }

    @Test
    fun `active locked id arriving before Room cannot compose the shell`() {
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loading,
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(emptyList()),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertTrue(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = 42L,
                gateRequired = true,
            ),
        )
    }

    @Test
    fun `loaded empty result cannot enter shell even when stale id is already active`() {
        assertFalse(shellMayCompose(ProfileLoadState.Loaded(emptyList()), 7L, authenticatedProfileId = 7L, gateRequired = false))
    }

    @Test
    fun `an active id absent from a loaded list cannot enter shell`() {
        assertFalse(
            shellMayCompose(
                ProfileLoadState.Loaded(listOf(fakeProfile(1L))),
                activeProfileId = 7L,
                authenticatedProfileId = 7L,
                gateRequired = false,
            ),
        )
    }

    @Test
    fun `authenticated profile A cannot authorize automatically selected locked profile B`() {
        val profiles = ProfileLoadState.Loaded(listOf(fakeProfile(2L).copy(pinHash = "locked")))
        val session = ProfileGateSessionViewModel()
        session.authenticateProfile(1L)

        // Deleting A clears the session before the repository selects B. Even if a caller observes
        // the new active id before that clear, the profile-bound policy still rejects stale A auth.
        session.invalidateAuthentication()
        assertFalse(shellMayCompose(profiles, 2L, session.authenticatedProfileId, gateRequired = true))
        assertFalse(shellMayCompose(profiles, 2L, authenticatedProfileId = 1L, gateRequired = true))
    }

    private fun fakeProfile(id: Long) = tv.own.owntv.core.database.entity.ProfileEntity(
        id = id,
        name = "Test",
        avatarColor = 0,
        avatarId = 0,
    )
}
