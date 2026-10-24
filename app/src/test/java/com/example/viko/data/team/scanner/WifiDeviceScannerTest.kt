package com.example.viko.data.team.scanner

import com.example.viko.domain.team.model.NearbyDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WifiDeviceScannerTest {
    @Test fun registrationDiscoversBadgeAndDhcpChangeDoesNotDuplicateIt() = runTest {
        val scanner = WifiDeviceScanner({ true }, { 0 })
        val first = NearbyDevice("WR-001122334455", "ViKo", "192.168.1.20")
        val moved = first.copy(address = "192.168.1.21")
        scanner.record(first)
        scanner.record(moved)
        assertEquals(listOf(moved), scanner.scan().first())
        assertTrue(scanner.confirmAvailable(moved).isSuccess)
        assertTrue(scanner.confirmAvailable(first).isFailure)
    }

    @Test fun expiredBadgeCannotBeEnrolledFromAnOldScan() = runTest {
        var now = 0L
        val scanner = WifiDeviceScanner({ true }, { now })
        val device = NearbyDevice("WR-001122334455", "ViKo", "192.168.1.20")
        scanner.record(device)
        now = WifiDeviceScanner.FRESH_MS
        assertTrue(scanner.scan().first().isEmpty())
        assertTrue(scanner.confirmAvailable(device).isFailure)
    }

    @Test fun losingWifiHidesCachedBadgesAndPreventsEnrollment() = runTest {
        var connected = true
        val scanner = WifiDeviceScanner({ connected }, { 0 })
        val device = NearbyDevice("WR-001122334455", "ViKo", "192.168.1.20")
        scanner.record(device)
        connected = false
        assertTrue(scanner.scan().first().isEmpty())
        assertTrue(scanner.confirmAvailable(device).isFailure)
    }

    @Test fun addressReusedByDifferentBadgesDoesNotMergeTheirIdentities() = runTest {
        val scanner = WifiDeviceScanner({ true }, { 0 })
        scanner.record(NearbyDevice("WR-001122334455", "ViKo A", "192.168.1.20"))
        scanner.record(NearbyDevice("WR-001122334466", "ViKo B", "192.168.1.20"))
        assertEquals(2, scanner.scan().first().size)
    }
}
