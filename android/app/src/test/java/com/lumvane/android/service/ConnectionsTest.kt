package com.lumvane.android.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionsTest {
    private fun conn(id: String, up: Long, down: Long, pkgs: List<String> = listOf("com.example.app"), uid: Int = 10100) =
        ConnectionInfo(id = id, upload = up, download = down, uid = uid, packages = pkgs)

    @Test
    fun parsesCoreJson() {
        val raw = """{"active":[{"id":"a","created_at":5,"upload":10,"download":20,"network":"tcp","dest":"192.0.2.1:443",
            "domain":"example.com","outbound":"proxy","chain":["proxy"],"rule":"final","uid":10100,
            "packages":["com.example.b","com.example.a"],"future_field":1}],"closed":[]}"""
        val s = assertNotNull(ConnectionsParser.parse(raw))
        val c = s.active.single()
        assertEquals(5, c.createdAt)
        assertEquals("example.com", c.host)
        assertEquals("com.example.a", c.owner, "shared uid: the first package by name")
        assertNull(ConnectionsParser.parse("nope"))
        assertEquals("uid:1000", ConnectionInfo(id = "x", uid = 1000).owner)
        assertNull(ConnectionInfo(id = "x").owner)
        assertEquals("192.0.2.1:443", ConnectionInfo(id = "x", dest = "192.0.2.1:443").host)
    }

    @Test
    fun countsOnlyGrowthAcrossSnapshots() {
        val acc = AppTrafficAccumulator()
        acc.add(ConnectionsSnapshot(active = listOf(conn("a", 100, 1000), conn("b", 5, 5, listOf("com.example.other")))))
        acc.add(ConnectionsSnapshot(active = listOf(conn("a", 150, 1500)), closed = listOf(conn("b", 7, 9, listOf("com.example.other")))))
        val out = acc.drain()
        assertEquals(150L to 1500L, out["com.example.app"])
        assertEquals(7L to 9L, out["com.example.other"])
        assertTrue(acc.drain().isEmpty(), "drain empties the pending counts")
    }

    @Test
    fun closedConnectionInBothListsIsCountedOnce() {
        val acc = AppTrafficAccumulator()
        val c = conn("a", 10, 10)
        acc.add(ConnectionsSnapshot(active = listOf(c), closed = listOf(c)))
        acc.add(ConnectionsSnapshot(closed = listOf(c)))
        assertEquals(10L to 10L, acc.drain()["com.example.app"])
    }

    @Test
    fun forgottenConnectionsAndUnknownOwners() {
        val acc = AppTrafficAccumulator()
        acc.add(ConnectionsSnapshot(active = listOf(conn("a", 10, 10), conn("u", 50, 50, emptyList(), -1))))
        acc.drain()
        acc.add(ConnectionsSnapshot())
        acc.add(ConnectionsSnapshot(active = listOf(conn("a", 4, 4))))
        val out = acc.drain()
        assertEquals(4L to 4L, out["com.example.app"])
        assertEquals(1, out.size, "unknown owners are not recorded")
    }
}
