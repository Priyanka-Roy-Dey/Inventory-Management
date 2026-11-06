package com.inventory.api.importing;

import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.outbox.LowStockOutbox;
import com.inventory.api.outbox.LowStockOutbox.Cause;
import com.inventory.api.stock.MovementType;
import com.inventory.api.stock.StockMovement;
import com.inventory.api.stock.StockMovementRepository;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Synchronous CSV import (A42): checks every row, then creates, updates or restores items by SKU in one
 * transaction. INV-21 can later move this behind a presigned upload and a queue, as in the design note.
 */
@Service
public class CsvImportService {

	// The required columns (A6), in the order errors are reported.
	static final List<String> COLUMNS = List.of("sku", "name", "category", "quantity", "reorder_level", "unit_price");

	static final int MAX_ROWS = 50_000;

	private static final Pattern SKU = Pattern.compile("[A-Za-z0-9._-]+");

	private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,9}");

	private static final Pattern PRICE = Pattern.compile("\\d{1,8}(\\.\\d{1,2})?");

	private static final String MOVEMENT_REASON = "CSV import";

	private final ItemRepository items;

	private final StockMovementRepository movements;

	private final LowStockOutbox outbox;

	public CsvImportService(ItemRepository items, StockMovementRepository movements, LowStockOutbox outbox) {
		this.items = items;
		this.movements = movements;
		this.outbox = outbox;
	}

	@Transactional
	public ImportReport importFile(MultipartFile file, String username) {
		String content = read(file);
		List<RowError> errors = new ArrayList<>();
		// A repeated SKU is an update: the last valid row wins (A5).
		Map<String, ImportRow> rows = new LinkedHashMap<>();
		int dataRows = 0;
		try (CSVParser parser = CSVParser.parse(new StringReader(content), CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setTrim(true)
			.setIgnoreEmptyLines(true)
			.get())) {
			Map<String, String> columns = checkHeader(parser.getHeaderNames());
			for (CSVRecord record : parser) {
				if (++dataRows > MAX_ROWS) {
					throw badRequest("The file has more than " + String.format("%,d", MAX_ROWS) + " rows");
				}
				long rowNumber = record.getRecordNumber() + 1;
				String sku = value(record, columns, "sku").toUpperCase(Locale.ROOT);
				String problem = problemWith(record, columns, parser.getHeaderNames().size());
				if (problem != null) {
					errors.add(new RowError(rowNumber, sku.isEmpty() ? null : sku, problem));
				}
				else {
					rows.put(sku, toRow(record, columns, sku));
				}
			}
		}
		catch (IOException | IllegalArgumentException | IllegalStateException ex) {
			throw badRequest("The file is not valid CSV: " + ex.getMessage());
		}

		int created = 0;
		int updated = 0;
		int restored = 0;
		for (ImportRow row : rows.values()) {
			switch (apply(row, username)) {
				case CREATED -> created++;
				case UPDATED -> updated++;
				case RESTORED -> restored++;
			}
		}
		return new ImportReport(dataRows, created + updated + restored, created, updated, restored, errors);
	}

	private enum Outcome {

		CREATED, UPDATED, RESTORED

	}

	private Outcome apply(ImportRow row, String username) {
		Item existing = items.findBySku(row.sku()).orElse(null);
		if (existing == null) {
			Item item = items.saveAndFlush(new Item(row.sku(), row.name(), row.category(), row.quantity(),
					row.reorderLevel(), row.unitPrice()));
			if (item.isLowStock()) {
				outbox.record(item.getId(), Cause.CREATED);
			}
			return Outcome.CREATED;
		}
		boolean wasDeleted = existing.getDeletedAt() != null;
		boolean wasLow = !wasDeleted && existing.isLowStock();
		int before = existing.getQuantity();
		existing.restore();
		existing.update(row.name(), row.category(), row.reorderLevel(), row.unitPrice());
		if (row.quantity() != before) {
			existing.setQuantityFromImport(row.quantity());
			movements.save(new StockMovement(existing.getId(), MovementType.ADJUST, row.quantity() - before, before,
					row.quantity(), MOVEMENT_REASON, username));
		}
		// The outbox reads the item row, so write the changes first.
		items.saveAndFlush(existing);
		if (!wasLow && existing.isLowStock()) {
			Cause cause = wasDeleted ? Cause.CREATED
					: row.quantity() != before ? Cause.STOCK_MOVEMENT : Cause.REORDER_LEVEL_RAISED;
			outbox.record(existing.getId(), cause);
		}
		return wasDeleted ? Outcome.RESTORED : Outcome.UPDATED;
	}

	private static String read(MultipartFile file) {
		try {
			String content = new String(file.getBytes(), StandardCharsets.UTF_8);
			// Spreadsheet programs often start UTF-8 files with a byte order mark.
			if (content.startsWith("﻿")) {
				content = content.substring(1);
			}
			if (content.isBlank()) {
				throw badRequest("The file is empty");
			}
			return content;
		}
		catch (IOException ex) {
			throw badRequest("The file could not be read");
		}
	}

	// Header names, ignoring case and spaces, mapped to the names the parser uses.
	private static Map<String, String> checkHeader(List<String> headerNames) {
		Map<String, String> columns = new LinkedHashMap<>();
		for (String header : headerNames) {
			columns.put(header.trim().toLowerCase(Locale.ROOT), header);
		}
		List<String> missing = COLUMNS.stream().filter(column -> !columns.containsKey(column)).toList();
		if (!missing.isEmpty()) {
			throw badRequest("Missing columns: " + String.join(", ", missing));
		}
		return columns;
	}

	private static String value(CSVRecord record, Map<String, String> columns, String column) {
		String header = columns.get(column);
		return record.isSet(header) ? record.get(header).trim() : "";
	}

	// The first problem with the row, in column order, or null if it can be imported.
	private static String problemWith(CSVRecord record, Map<String, String> columns, int expectedFields) {
		if (record.size() != expectedFields) {
			return "expected " + expectedFields + " fields but found " + record.size();
		}
		String sku = value(record, columns, "sku");
		if (sku.isEmpty()) {
			return "sku: is required";
		}
		if (sku.length() > 50) {
			return "sku: must be at most 50 characters";
		}
		if (!SKU.matcher(sku).matches()) {
			return "sku: may contain only letters, digits, '.', '_' and '-'";
		}
		for (String[] text : new String[][] { { "name", "200" }, { "category", "100" } }) {
			String field = value(record, columns, text[0]);
			if (field.isEmpty()) {
				return text[0] + ": is required";
			}
			if (field.length() > Integer.parseInt(text[1])) {
				return text[0] + ": must be at most " + text[1] + " characters";
			}
		}
		for (String number : List.of("quantity", "reorder_level")) {
			if (!WHOLE_NUMBER.matcher(value(record, columns, number)).matches()) {
				return number + ": must be a whole number of 0 or more";
			}
		}
		if (!PRICE.matcher(value(record, columns, "unit_price")).matches()) {
			return "unit_price: must be 0 or more with at most 2 decimal places";
		}
		return null;
	}

	private static ImportRow toRow(CSVRecord record, Map<String, String> columns, String sku) {
		return new ImportRow(sku, value(record, columns, "name"), value(record, columns, "category"),
				Integer.parseInt(value(record, columns, "quantity")),
				Integer.parseInt(value(record, columns, "reorder_level")),
				new BigDecimal(value(record, columns, "unit_price")).setScale(2));
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
