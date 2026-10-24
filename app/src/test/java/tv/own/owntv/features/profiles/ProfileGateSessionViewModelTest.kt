package tv.own.owntv.features.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProfileGateSessionViewModel unit tests (docs/internationalization.md, "Unit tests → Profile gate").
 *
 * Behavioural coverage of the session transitions. The structural guarantee — no `SavedStateHandle`
 * and no `rememberSaveable` for the authentication flag — is enforced by the class simply not
 * referencing either; the class is intentionally tiny so that absence is reviewable at a glance.
 */
class ProfileGateSessionViewModelTest {

    @Test
    fun `gate starts without an authenticated profile`() {
        val vm = ProfileGateSessionViewModel()
        assertNull(vm.authenticatedProfileId)
        assertFalse(vm.addingProfile)
        assertFalse(vm.switchProfileRequested)
    }

    @Test
    fun `authentication is recorded for the selected profile and clears a pending switch`() {
        val vm = ProfileGateSessionViewModel()
        vm.requestSwitchProfile()
        vm.authenticateProfile(42L)
        assertEquals(42L, vm.authenticatedProfileId)
        assertFalse(vm.switchProfileRequested)
    }

    @Test
    fun `requestSwitchProfile clears authentication and forces the chooser open`() {
        val vm = ProfileGateSessionViewModel()
        vm.authenticateProfile(42L)
        vm.requestSwitchProfile()
        assertNull(vm.authenticatedProfileId)
        assertTrue(vm.switchProfileRequested)
    }

    @Test
    fun `cancelSwitchProfileRequest restores only the unchanged active profile`() {
        val vm = ProfileGateSessionViewModel()
        vm.requestSwitchProfile()
        vm.cancelSwitchProfileRequest(42L)
        assertEquals(42L, vm.authenticatedProfileId)
        assertFalse(vm.switchProfileRequested)
    }

    @Test
    fun `add-profile flow opens and cancels back to the gate without authenticating`() {
        val vm = ProfileGateSessionViewModel()
        vm.startAddingProfile()
        assertTrue(vm.addingProfile)
        assertFalse(vm.switchProfileRequested)
        vm.cancelAddingProfile()
        assertFalse(vm.addingProfile)
        assertNull(vm.authenticatedProfileId)
    }

    @Test
    fun `completing add-profile onboarding authenticates as the new profile`() {
        val vm = ProfileGateSessionViewModel()
        vm.startAddingProfile()
        vm.completeAddingProfile(84L)
        assertFalse(vm.addingProfile)
        assertEquals(84L, vm.authenticatedProfileId)
    }

    @Test
    fun `deleting an unrelated profile does not invalidate authentication`() {
        val vm = ProfileGateSessionViewModel()
        vm.authenticateProfile(10L)

        vm.invalidateIfDeletingActiveProfile(deletedProfileId = 20L, activeProfileId = 10L)

        assertEquals(10L, vm.authenticatedProfileId)
    }

    @Test
    fun `deleting the active profile invalidates authentication`() {
        val vm = ProfileGateSessionViewModel()
        vm.authenticateProfile(10L)

        vm.invalidateIfDeletingActiveProfile(deletedProfileId = 10L, activeProfileId = 10L)

        assertNull(vm.authenticatedProfileId)
    }

    @Test
    fun `deleting while active profile is still loading does not invalidate authentication`() {
        val vm = ProfileGateSessionViewModel()
        vm.authenticateProfile(10L)

        vm.invalidateIfDeletingActiveProfile(deletedProfileId = 10L, activeProfileId = null)

        assertEquals(10L, vm.authenticatedProfileId)
    }

    @Test
    fun `authentication is invalidated when the active profile changes but not while id is loading`() {
        val vm = ProfileGateSessionViewModel()
        vm.authenticateProfile(10L)
        vm.invalidateIfNotProfile(null)
        assertEquals(10L, vm.authenticatedProfileId)
        vm.invalidateIfNotProfile(20L)
        assertNull(vm.authenticatedProfileId)
    }
}