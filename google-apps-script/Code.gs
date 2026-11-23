/**
 * Scale Flow Leads — Google Sheets Real-Time Sync Bridge (Apps Script)
 *
 * SETUP (2 minutes):
 * 1. Open your Google Sheet CRM.
 * 2. Click Extensions -> Apps Script.
 * 3. Delete any existing code and paste this entire file.
 * 4. (Optional) Set a secret API_KEY below so only your app can access the sheet.
 * 5. Click Deploy -> New deployment -> Select type: Web app.
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 6. Click Deploy, authorize when prompted, and copy the Web App URL.
 * 7. Paste that URL (and API key if set) into the Scale Flow Leads Android app.
 */

const CONFIG = {
  // Optional: set a short secret PIN/password (e.g. "scaleflow2026") and enter
  // the same key in the Android app Settings. Leave "" for no key check.
  API_KEY: "",
  // Optional: lock to a specific tab name, or leave "" to default to the first tab.
  DEFAULT_SHEET: ""
};

function doGet(e) {
  try {
    const params = (e && e.parameter) ? e.parameter : {};
    if (!isAuthorized(params.key)) {
      return jsonResponse({ ok: false, error: "Unauthorized: invalid API key" });
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheetNames = ss.getSheets().map(function(s) { return s.getName(); });
    const targetName = params.sheet || CONFIG.DEFAULT_SHEET || sheetNames[0];
    const sheet = ss.getSheetByName(targetName) || ss.getSheets()[0];

    const data = readSheetData(sheet);
    return jsonResponse({
      ok: true,
      spreadsheetName: ss.getName(),
      sheetName: sheet.getName(),
      sheetNames: sheetNames,
      headers: data.headers,
      rows: data.rows,
      updatedAt: new Date().toISOString()
    });
  } catch (err) {
    return jsonResponse({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(15000);
    const raw = (e && e.postData && e.postData.contents) ? e.postData.contents : "{}";
    const body = JSON.parse(raw);

    if (!isAuthorized(body.key)) {
      return jsonResponse({ ok: false, error: "Unauthorized: invalid API key" });
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheetNames = ss.getSheets().map(function(s) { return s.getName(); });
    const targetName = body.sheet || CONFIG.DEFAULT_SHEET || sheetNames[0];
    const sheet = ss.getSheetByName(targetName) || ss.getSheets()[0];
    const action = body.action || "update";

    const headers = getHeaders(sheet);
    if (headers.length === 0) {
      return jsonResponse({ ok: false, error: "Sheet has no header row in row 1" });
    }

    if (action === "update") {
      const rowIndex = Number(body.rowIndex);
      if (!rowIndex || rowIndex < 2) {
        return jsonResponse({ ok: false, error: "Invalid rowIndex" });
      }
      const fields = body.fields || {};
      headers.forEach(function(header, idx) {
        if (Object.prototype.hasOwnProperty.call(fields, header)) {
          sheet.getRange(rowIndex, idx + 1).setValue(fields[header]);
        }
      });
      SpreadsheetApp.flush();
      return jsonResponse({
        ok: true,
        action: "update",
        rowIndex: rowIndex,
        updatedAt: new Date().toISOString()
      });
    }

    if (action === "appendNote") {
      const rowIndex = Number(body.rowIndex);
      if (!rowIndex || rowIndex < 2) {
        return jsonResponse({ ok: false, error: "Invalid rowIndex" });
      }
      const noteColName = body.noteColumn;
      const noteText = (body.noteText || "").trim();
      const statusColName = body.statusColumn;
      const statusValue = body.statusValue;

      if (noteColName && noteText) {
        const noteColIdx = headers.indexOf(noteColName);
        if (noteColIdx !== -1) {
          const cell = sheet.getRange(rowIndex, noteColIdx + 1);
          const existing = String(cell.getDisplayValue() || "").trim();
          const stamp = Utilities.formatDate(new Date(), ss.getSpreadsheetTimeZone(), "dd MMM, hh:mm a");
          const entry = "[" + stamp + "] " + noteText;
          const updated = existing ? (entry + "\n" + existing) : entry;
          cell.setValue(updated);
        }
      }

      if (statusColName && statusValue !== undefined && statusValue !== null && statusValue !== "") {
        const statusColIdx = headers.indexOf(statusColName);
        if (statusColIdx !== -1) {
          sheet.getRange(rowIndex, statusColIdx + 1).setValue(statusValue);
        }
      }

      SpreadsheetApp.flush();
      return jsonResponse({
        ok: true,
        action: "appendNote",
        rowIndex: rowIndex,
        updatedAt: new Date().toISOString()
      });
    }

    if (action === "append") {
      const fields = body.fields || {};
      const newRow = headers.map(function(header) {
        return Object.prototype.hasOwnProperty.call(fields, header) ? fields[header] : "";
      });
      sheet.appendRow(newRow);
      SpreadsheetApp.flush();
      const newRowIndex = sheet.getLastRow();
      return jsonResponse({
        ok: true,
        action: "append",
        rowIndex: newRowIndex,
        updatedAt: new Date().toISOString()
      });
    }

    return jsonResponse({ ok: false, error: "Unknown action: " + action });
  } catch (err) {
    return jsonResponse({ ok: false, error: String(err) });
  } finally {
    try { lock.releaseLock(); } catch (_) {}
  }
}

function isAuthorized(key) {
  if (!CONFIG.API_KEY) return true;
  return String(key || "") === String(CONFIG.API_KEY);
}

function getHeaders(sheet) {
  const lastCol = sheet.getLastColumn();
  if (lastCol === 0) return [];
  return sheet.getRange(1, 1, 1, lastCol).getDisplayValues()[0].map(function(h) {
    return String(h || "").trim();
  });
}

function readSheetData(sheet) {
  const lastRow = sheet.getLastRow();
  const lastCol = sheet.getLastColumn();
  if (lastRow === 0 || lastCol === 0) {
    return { headers: [], rows: [] };
  }

  const allValues = sheet.getRange(1, 1, lastRow, lastCol).getDisplayValues();
  const headers = allValues[0].map(function(h) { return String(h || "").trim(); });
  const rows = [];

  for (let r = 1; r < allValues.length; r++) {
    const rowValues = allValues[r];
    const isEmpty = rowValues.every(function(v) { return String(v || "").trim() === ""; });
    if (isEmpty) continue;

    const fields = {};
    for (let c = 0; c < headers.length; c++) {
      if (headers[c]) {
        fields[headers[c]] = rowValues[c] || "";
      }
    }
    rows.push({
      rowIndex: r + 1,
      fields: fields
    });
  }

  return { headers: headers.filter(Boolean), rows: rows };
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
