package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.theme.AccentCycle
import com.dharmachakra.fba_android.ui.theme.Brand
import com.dharmachakra.fba_android.ui.theme.OrderOnly
import com.dharmachakra.fba_android.ui.theme.PageTheme
import com.dharmachakra.fba_android.ui.theme.RemasterGold
import com.dharmachakra.fba_android.ui.theme.accentFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AccentCycleTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(hour: Int, minute: Int = 0, zone: ZoneId = london) =
        ZonedDateTime.of(2026, 11, 8, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun modesMatchTokensOrderAndStartOnFbaBlue() {
        assertEquals(10, AccentCycle.modes.size)
        assertEquals(Brand.FBA_BLUE, AccentCycle.modes.first())
        assertEquals(Brand.FBA_BLUE, AccentCycle.default)
        assertEquals(Brand.FBA_PURPLE, AccentCycle.modes.last())
    }

    @Test fun oneModePerHourWholeHourStable() {
        assertEquals(Brand.FBA_BLUE, AccentCycle.modeFor(at(0)))
        assertEquals(Brand.FBA_BLUE, AccentCycle.modeFor(at(0, 59)))
        assertEquals(Brand.FBA_GREEN, AccentCycle.modeFor(at(1)))
        assertEquals(Brand.FBA_PURPLE, AccentCycle.modeFor(at(9)))
        assertEquals(Brand.FBA_BLUE, AccentCycle.modeFor(at(10)))      // wraps
        assertEquals(Brand.ORDER_ONLY_BLUE, AccentCycle.modeFor(at(22)))
    }

    @Test fun usesUkTimeWhateverTheDeviceZone() {
        // 09:00 in New York is 14:00 in London → index 4 (Chestnut)
        val ny = at(9, zone = ZoneId.of("America/New_York"))
        assertEquals(Brand.FBA_CHESTNUT, AccentCycle.modeFor(ny))
    }

    @Test fun millisToNextHour() {
        assertEquals(3_600_000L, AccentCycle.millisToNextHour(at(3)))
        assertEquals(60_000L, AccentCycle.millisToNextHour(at(3, 59)))
    }

    @Test fun pageThemesOverrideTheHourlyAccent() {
        val hourly = Brand.FBA_LILAC.set
        assertEquals(hourly, accentFor(PageTheme.HOURLY, hourly))
        assertEquals(RemasterGold, accentFor(PageTheme.REMASTERED, hourly))
        assertEquals(OrderOnly, accentFor(PageTheme.ORDER_ONLY, hourly))
        assertEquals(0xFF006AA3, OrderOnly.deep)
        assertEquals(0xFF8A6A1F, RemasterGold.deep)
    }
}
