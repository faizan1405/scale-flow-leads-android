package com.scaleflow.leads.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaMappingTest {
    @Test
    fun detectsScaleFlowLeadsColumns() {
        val headers = listOf("Date", "Last Call", "Name", "Numbers", "Lead Type", "Follow-Ups", "Assined to", "Faizan", "Saurabh")
        val schema = SchemaMapping.detect(headers)
        assertEquals("Name", schema.nameCol)
        assertEquals("Numbers", schema.phoneCol)
        assertEquals("Lead Type", schema.statusCol)
        assertEquals("Follow-Ups", schema.followUpCol)
        assertEquals("Last Call", schema.lastCallCol)
        assertEquals("Assined to", schema.assignedCol)
    }
}
