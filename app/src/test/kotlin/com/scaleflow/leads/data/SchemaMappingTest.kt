package com.scaleflow.leads.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaMappingTest {
    @Test
    fun detectsStandardCrmColumns() {
        val headers = listOf("Business Name", "Phone Number", "Website URL", "Call Status", "Call Notes", "Follow-Up Date")
        val schema = SchemaMapping.detect(headers)
        assertEquals("Business Name", schema.nameCol)
        assertEquals("Phone Number", schema.phoneCol)
        assertEquals("Website URL", schema.websiteCol)
        assertEquals("Call Status", schema.statusCol)
        assertEquals("Call Notes", schema.notesCol)
        assertEquals("Follow-Up Date", schema.followUpCol)
    }
}
