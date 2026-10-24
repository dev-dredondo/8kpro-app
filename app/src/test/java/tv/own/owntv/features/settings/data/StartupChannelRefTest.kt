package tv.own.owntv.features.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.settings.StartupChannelRef

class StartupChannelRefTest {
    @Test
    fun `stable channel reference survives settings serialization`() {
        val original = StartupChannelRef(
            sourceId = 7L,
            remoteId = "stream-42",
            name = "News HD",
            itemId = 99L,
        )

        assertEquals(original, StartupChannelRef.fromJson(original.toJson().toString()))
    }

    @Test
    fun `invalid startup channel reference is ignored`() {
        assertNull(StartupChannelRef.fromJson(null))
        assertNull(StartupChannelRef.fromJson("{}"))
        assertNull(StartupChannelRef.fromJson("not-json"))
    }
}
