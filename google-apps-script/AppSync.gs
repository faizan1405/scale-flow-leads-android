/**
 * Scale Flow Leads — Google Sheets Fast Sync Bridge v1.6
 *
 * FAST REFRESH OPTIMIZATIONS:
 * - Batch-reads ALL column Data Validation dropdowns in 1 single API call (instead of ~50 calls!).
 * - Batch-writes row updates in 1 single setValues() call.
 * - Sends latest 100 History rows for fast payloads.
 * - Color-styled permanent "History" tab + unlimited future columns (skips hidden helper col J).
 *
 * HOW TO UPDATE (1 minute — keeps your exact same Web App URL):
 * 1. Open Google Sheet -> Extensions -> Apps Script -> click AppSync.gs.
 * 2. Replace everything in AppSync.gs with this code and press Ctrl + S.
 * 3. Click Deploy -> Manage deployments -> Edit (pencil) -> Version: "New version" -> Deploy.
 */

const APP_SYNC_CONFIG = {
  API_KEY: "",
  DEFAULT_SHEET: "Leads ",
  HISTORY_SHEET: "History",
  HISTORY_LIMIT: 100
};

function doGet(e) {
  try {
    const params = (e && e.parameter) ? e.parameter : {};
    if (!appSyncAuthorized_(params.key)) {
      return appSyncJson_({ ok: false, error: "Unauthorized: invalid API key" });
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const targetName = params.sheet || APP_SYNC_CONFIG.DEFAULT_SHEET;
    const sheet = (targetName && ss.getSheetByName(targetName)) || ss.getSheets()[0];

    const data = appSyncReadSheetFast_(sheet);
    const history = appSyncReadHistory_(ss, APP_SYNC_CONFIG.HISTORY_LIMIT);

    return appSyncJson_({
      ok: true,
      spreadsheetName: ss.getName(),
      sheetName: sheet.getName(),
      sheetNames: [sheet.getName()],
      headers: data.headers,
      dropdownOptions: data.dropdownOptions,
      rows: data.rows,
      history: history,
      updatedAt: new Date().toISOString()
    });
  } catch (err) {
    return appSyncJson_({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(15000);
    const raw = (e && e.postData && e.postData.contents) ? e.postData.contents : "{}";
    const body = JSON.parse(raw);

    if (!appSyncAuthorized_(body.key)) {
      return appSyncJson_({ ok: false, error: "Unauthorized: invalid API key" });
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const targetName = body.sheet || APP_SYNC_CONFIG.DEFAULT_SHEET;
    const sheet = (targetName && ss.getSheetByName(targetName)) || ss.getSheets()[0];
    const action = body.action || "update";

    if (action === "logHistory") {
      appSyncAppendHistoryRow_(ss, {
        caller: body.caller || "App User",
        rowIndex: Number(body.rowIndex) || 0,
        leadName: body.leadName || "",
        phone: body.phone || "",
        action: body.eventAction || "Call",
        fieldChanged: body.fieldChanged || "Call",
        oldValue: body.oldValue || "",
        newValue: body.newValue || "",
        callDuration: body.callDuration || ""
      });
      return appSyncJson_({ ok: true, action: "logHistory", updatedAt: new Date().toISOString() });
    }

    const colInfo = appSyncGetVisibleColumnsFast_(sheet);
    if (colInfo.headers.length === 0) {
      return appSyncJson_({ ok: false, error: "Sheet has no header row in row 1" });
    }

    if (action === "update") {
      const rowIndex = Number(body.rowIndex);
      if (!rowIndex || rowIndex < 2) {
        return appSyncJson_({ ok: false, error: "Invalid rowIndex" });
      }
      const fields = body.fields || {};
      const caller = body.caller || "App User";
      const leadName = body.leadName || "";
      const phone = body.phone || "";
      const callDuration = body.callDuration || "";

      const lastCol = sheet.getLastColumn();
      const rowRange = sheet.getRange(rowIndex, 1, 1, lastCol);
      const existingValues = rowRange.getValues()[0];
      const existingDisplay = rowRange.getDisplayValues()[0];
      const historyToLog = [];
      let rowDirty = false;

      colInfo.columns.forEach(function(col) {
        const header = col.header;
        if (Object.prototype.hasOwnProperty.call(fields, header)) {
          const newVal = String(fields[header] == null ? "" : fields[header]);
          const oldVal = String(existingDisplay[col.colIndex - 1] || "");
          existingValues[col.colIndex - 1] = newVal;
          rowDirty = true;

          if (newVal !== oldVal && header.toLowerCase() !== "last call") {
            historyToLog.push({
              caller: caller,
              rowIndex: rowIndex,
              leadName: leadName,
              phone: phone,
              action: "Updated",
              fieldChanged: header,
              oldValue: oldVal,
              newValue: newVal,
              callDuration: callDuration
            });
          }
        }
      });

      if (rowDirty) {
        rowRange.setValues([existingValues]);
      }
      historyToLog.forEach(function(h) { appSyncAppendHistoryRow_(ss, h); });

      return appSyncJson_({
        ok: true,
        action: "update",
        rowIndex: rowIndex,
        updatedAt: new Date().toISOString()
      });
    }

    return appSyncJson_({ ok: false, error: "Unknown action: " + action });
  } catch (err) {
    return appSyncJson_({ ok: false, error: String(err) });
  } finally {
    try { lock.releaseLock(); } catch (_) {}
  }
}

function appSyncAuthorized_(key) {
  if (!APP_SYNC_CONFIG.API_KEY) return true;
  return String(key || "") === String(APP_SYNC_CONFIG.API_KEY);
}

function appSyncGetVisibleColumnsFast_(sheet) {
  const lastCol = sheet.getLastColumn();
  if (lastCol === 0) return { headers: [], columns: [] };
  const rawHeaders = sheet.getRange(1, 1, 1, lastCol).getDisplayValues()[0];
  const headers = [];
  const columns = [];
  for (let c = 1; c <= lastCol; c++) {
    const h = String(rawHeaders[c - 1] || "").trim();
    if (!h) continue;
    if (sheet.isColumnHiddenByUser(c)) continue;
    headers.push(h);
    columns.push({ colIndex: c, header: h });
  }
  return { headers: headers, columns: columns };
}

/**
 * Single batched getDataValidations() call for the entire row 2 (48x fewer RPCs!).
 */
function appSyncReadDropdownOptionsFast_(sheet, columns, lastCol) {
  const result = {};
  if (sheet.getLastRow() < 2 || lastCol === 0) return result;
  try {
    const rules = sheet.getRange(2, 1, 1, lastCol).getDataValidations()[0];
    columns.forEach(function(col) {
      const rule = rules[col.colIndex - 1];
      if (!rule) return;
      try {
        const type = rule.getCriteriaType();
        const args = rule.getCriteriaValues();
        let vals = [];
        if (type === SpreadsheetApp.DataValidationCriteria.VALUE_IN_LIST && args && args.length > 0) {
          vals = args[0].map(function(v) { return String(v || "").trim(); }).filter(Boolean);
        } else if (type === SpreadsheetApp.DataValidationCriteria.VALUE_IN_RANGE && args && args.length > 0 && args[0].getDisplayValues) {
          const grid = args[0].getDisplayValues();
          for (let i = 0; i < grid.length; i++) {
            for (let j = 0; j < grid[i].length; j++) {
              const s = String(grid[i][j] || "").trim();
              if (s) vals.push(s);
            }
          }
        }
        if (vals.length > 0) result[col.header] = vals;
      } catch (_) {}
    });
  } catch (_) {}
  return result;
}

function appSyncReadSheetFast_(sheet) {
  const lastRow = sheet.getLastRow();
  const lastCol = sheet.getLastColumn();
  if (lastRow === 0 || lastCol === 0) {
    return { headers: [], dropdownOptions: {}, rows: [] };
  }

  const allValues = sheet.getRange(1, 1, lastRow, lastCol).getDisplayValues();
  const rawHeaders = allValues[0];
  const headers = [];
  const columns = [];
  for (let c = 1; c <= lastCol; c++) {
    const h = String(rawHeaders[c - 1] || "").trim();
    if (!h) continue;
    if (sheet.isColumnHiddenByUser(c)) continue;
    headers.push(h);
    columns.push({ colIndex: c, header: h });
  }

  const dropdownOptions = appSyncReadDropdownOptionsFast_(sheet, columns, lastCol);
  const rows = [];

  for (let r = 1; r < allValues.length; r++) {
    const rowValues = allValues[r];
    const fields = {};
    let hasAny = false;
    for (let k = 0; k < columns.length; k++) {
      const col = columns[k];
      const v = rowValues[col.colIndex - 1] || "";
      if (v !== "") hasAny = true;
      fields[col.header] = v;
    }
    if (!hasAny) continue;
    rows.push({ rowIndex: r + 1, fields: fields });
  }

  return { headers: headers, dropdownOptions: dropdownOptions, rows: rows };
}

function appSyncEnsureHistorySheet_(ss) {
  let hist = ss.getSheetByName(APP_SYNC_CONFIG.HISTORY_SHEET);
  if (!hist) {
    hist = ss.insertSheet(APP_SYNC_CONFIG.HISTORY_SHEET);
    hist.appendRow([
      "Timestamp",
      "Caller",
      "Row #",
      "Lead Name",
      "Phone",
      "Action",
      "Field Changed",
      "Old Value",
      "New Value",
      "Call Duration"
    ]);
    hist.getRange(1, 1, 1, 10)
      .setBackground("#1E3A8A")
      .setFontColor("#FFFFFF")
      .setFontWeight("bold")
      .setFontSize(11)
      .setHorizontalAlignment("center")
      .setVerticalAlignment("middle");
    hist.setRowHeight(1, 36);
    hist.setFrozenRows(1);
    const widths = [180, 110, 65, 170, 125, 95, 130, 180, 220, 120];
    widths.forEach(function(w, i) { hist.setColumnWidth(i + 1, w); });
  }
  return hist;
}

function appSyncAppendHistoryRow_(ss, entry) {
  const hist = appSyncEnsureHistorySheet_(ss);
  const ts = Utilities.formatDate(new Date(), ss.getSpreadsheetTimeZone(), "hh:mm a, dd MMM yyyy")
    .replace(" am,", " AM,")
    .replace(" pm,", " PM,");
  hist.appendRow([
    ts,
    entry.caller || "App User",
    entry.rowIndex || "",
    entry.leadName || "",
    entry.phone || "",
    entry.action || "Updated",
    entry.fieldChanged || "",
    entry.oldValue || "",
    entry.newValue || "",
    entry.callDuration || ""
  ]);

  const lastRow = hist.getLastRow();
  const rowRange = hist.getRange(lastRow, 1, 1, 10);
  const actionLower = String(entry.action || "").toLowerCase();
  const newValLower = String(entry.newValue || "").toLowerCase();

  let bg = "#F8FAFC";
  let actionFg = "#1E40AF";
  if (actionLower === "call") {
    bg = "#DCFCE7";
    actionFg = "#166534";
  } else if (newValLower.indexOf("lost") !== -1) {
    bg = "#FEE2E2";
    actionFg = "#B91C1C";
  } else if (newValLower.indexOf("won") !== -1 || newValLower.indexOf("qualified") !== -1) {
    bg = "#DBEAFE";
    actionFg = "#1D4ED8";
  } else if (newValLower.indexOf("proposal") !== -1 || newValLower.indexOf("follow") !== -1 || newValLower.indexOf("done") !== -1) {
    bg = "#FEF3C7";
    actionFg = "#B45309";
  }

  rowRange
    .setBackground(bg)
    .setFontColor("#0F172A")
    .setFontSize(10)
    .setVerticalAlignment("middle")
    .setWrap(true)
    .setBorder(true, true, true, true, true, true, "#CBD5E1", SpreadsheetApp.BorderStyle.SOLID);

  hist.getRange(lastRow, 6).setFontWeight("bold").setFontColor(actionFg).setHorizontalAlignment("center");
  hist.getRange(lastRow, 3).setHorizontalAlignment("center");
  hist.setRowHeight(lastRow, 32);
}

function appSyncReadHistory_(ss, limit) {
  const hist = ss.getSheetByName(APP_SYNC_CONFIG.HISTORY_SHEET);
  if (!hist) return [];
  const lastRow = hist.getLastRow();
  if (lastRow < 2) return [];
  const startRow = Math.max(2, lastRow - limit + 1);
  const count = lastRow - startRow + 1;
  const values = hist.getRange(startRow, 1, count, 10).getDisplayValues();
  const list = [];
  for (let i = values.length - 1; i >= 0; i--) {
    const r = values[i];
    list.push({
      timestamp: r[0] || "",
      caller: r[1] || "",
      rowIndex: Number(r[2]) || 0,
      leadName: r[3] || "",
      phone: r[4] || "",
      action: r[5] || "",
      fieldChanged: r[6] || "",
      oldValue: r[7] || "",
      newValue: r[8] || "",
      callDuration: r[9] || ""
    });
  }
  return list;
}

function appSyncJson_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
