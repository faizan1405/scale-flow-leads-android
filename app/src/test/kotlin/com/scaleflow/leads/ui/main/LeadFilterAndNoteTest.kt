package com.scaleflow.leads.ui.main

import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.appendNotePreservingCase
import com.scaleflow.leads.data.formatLastCallTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LeadFilterAndNoteTest {

    private val headers = listOf(
        "Date", "Last Call", "Name", "Numbers", "Lead Type", "Follow-Ups", "Assined to", "Faizan", "Saurabh"
    )
    private val schema = SchemaMapping.detect(headers)

    private val sampleRows = listOf(
        LeadRow(
            rowIndex = 2,
            fields = mapOf(
                "Name" to "Prakash Mandal",
                "Numbers" to "7479767878",
                "Lead Type" to "Qualified ", // trailing space from real sheet!
                "Follow-Ups" to "1 done. ",
                "Assined to" to "Faizan",
                "Faizan" to "Interested in website"
            )
        ),
        LeadRow(
            rowIndex = 3,
            fields = mapOf(
                "Name" to "Dhanesh Damor",
                "Numbers" to "8435004495",
                "Lead Type" to "Qualified",
                "Follow-Ups" to "2 done.",
                "Assined to" to "Saurabh",
                "Saurabh" to "Will call tomorrow"
            )
        ),
        LeadRow(
            rowIndex = 4,
            fields = mapOf(
                "Name" to "Prakash Sharma",
                "Numbers" to "9876543210",
                "Lead Type" to "lost",
                "Follow-Ups" to "Lost",
                "Assined to" to "Saurabh"
            )
        ),
        LeadRow(
            rowIndex = 5,
            fields = mapOf(
                "Name" to "Ganesan",
                "Numbers" to "7358044321",
                "Lead Type" to "New",
                "Follow-Ups" to "",
                "Assined to" to ""
            )
        )
    )

    @Test
    fun combinedSearchAndAssignedAndStatusAndFollowUpFiltersWorkSimultaneously() {
        // Search "Prakash" + Assigned "Faizan" + Status "Qualified" + Follow-up "1 done."
        val res = LeadFilterEngine.filterAndSort(
            rows = sampleRows,
            schema = schema,
            searchQuery = "prakash",
            selectedStatusFilter = "Qualified",
            selectedAssigneeFilter = "Faizan",
            selectedFollowUpFilter = "1 done.",
            callerProfile = "",
            sortBy = "Sheet order"
        )
        assertEquals(listOf(2), res.map { it.rowIndex })

        // Same search "Prakash" + Assigned "Saurabh" should return ONLY row 4!
        val res2 = LeadFilterEngine.filterAndSort(
            rows = sampleRows,
            schema = schema,
            searchQuery = "prakash",
            selectedStatusFilter = "All",
            selectedAssigneeFilter = "Saurabh",
            selectedFollowUpFilter = "All",
            callerProfile = "",
            sortBy = "Sheet order"
        )
        assertEquals(listOf(4), res2.map { it.rowIndex })
    }

    @Test
    fun searchByExactGoogleSheetRowNumberWorks() {
        val byHash = LeadFilterEngine.filterAndSort(
            rows = sampleRows,
            schema = schema,
            searchQuery = "#3",
            selectedStatusFilter = "All",
            selectedAssigneeFilter = "All",
            selectedFollowUpFilter = "All",
            callerProfile = "",
            sortBy = "Sheet order"
        )
        assertEquals(listOf(3), byHash.map { it.rowIndex })
    }

    @Test
    fun myLeadsCallerProfileFilterWorks() {
        val myLeads = LeadFilterEngine.filterAndSort(
            rows = sampleRows,
            schema = schema,
            searchQuery = "",
            selectedStatusFilter = "All",
            selectedAssigneeFilter = "My Leads",
            selectedFollowUpFilter = "All",
            callerProfile = "Saurabh",
            sortBy = "Sheet order"
        )
        assertEquals(listOf(3, 4), myLeads.map { it.rowIndex })
    }

    @Test
    fun followUpsExcludedFromCallNotesAndNamePhoneLocked() {
        assertFalse(schema.noteColumnOptions.contains("Follow-Ups"))
        assertTrue(schema.noteColumnOptions.contains("Faizan"))
        assertTrue(schema.noteColumnOptions.contains("Saurabh"))
        assertTrue(schema.isLockedColumn("Name"))
        assertTrue(schema.isLockedColumn("Numbers"))
        assertFalse(schema.isLockedColumn("Lead Type"))
        assertFalse(schema.isLockedColumn("Follow-Ups"))
    }

    @Test
    fun noteAppendsOnNewLineWithoutTimestampAndPreservesExactCase() {
        assertEquals("xyz", appendNotePreservingCase("", "xyz"))
        assertEquals("XYZ", appendNotePreservingCase("", "XYZ"))
        assertEquals("Old note\nnew lowercase note", appendNotePreservingCase("Old note", "new lowercase note"))
        assertEquals("Old note\nMixed Case Note", appendNotePreservingCase("Old note\n", "Mixed Case Note"))
    }

    @Test
    fun lastCallTimestampMatchesExactFormat() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 14, 45, 0)
        }
        assertEquals("02:45 PM, 24 Nov 2026", formatLastCallTimestamp(cal.time))
    }

    @Test
    fun followUpDateAndExtraNotesAreExcludedFromCallersAndDetectedProperly() {
        val fullHeaders = listOf(
            "Date", "Last Call", "Name", "Numbers", "Lead Type", "Follow-Ups",
            "Assined to", "Faizan", "Saurabh", "Arshi", "Follow up Date", "Extra notes"
        )
        val fullSchema = SchemaMapping.detect(fullHeaders)
        assertEquals("Follow-Ups", fullSchema.followUpCol)
        assertEquals("Follow up Date", fullSchema.followUpDateCol)
        assertEquals("Extra notes", fullSchema.extraNotesCol)
        assertTrue(fullSchema.isDateColumn("Follow up Date"))
        assertTrue(fullSchema.isDateColumn("Date"))
        assertTrue(fullSchema.isDateColumn("Last Call"))
        assertFalse(fullSchema.noteColumnOptions.contains("Follow up Date"))
        assertFalse(fullSchema.noteColumnOptions.contains("Extra notes"))
        assertEquals(listOf("Faizan", "Saurabh", "Arshi"), fullSchema.noteColumnOptions)
    }
}
