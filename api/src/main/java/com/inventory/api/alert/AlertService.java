package com.inventory.api.alert;

import com.inventory.api.outbox.LowStockOutbox.Cause;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the LOW_STOCK events written by INV-10. Interim source (A41): INV-17 to INV-19 move alert history to
 * DynamoDB behind API Gateway.
 */
@Service
public class AlertService {

	private static final int DEFAULT_SIZE = 20;

	private static final int MAX_SIZE = 100;

	private static final String FROM = """
			FROM outbox_events e
			JOIN items i ON i.id = e.aggregate_id
			WHERE e.event_type = 'LOW_STOCK' AND i.deleted_at IS NULL
			""";

	private static final String SELECT = """
			SELECT e.event_id::text AS id, e.aggregate_id AS item_id, e.payload ->> 'sku' AS sku,
			       e.payload ->> 'name' AS item_name, (e.payload ->> 'quantity')::int AS quantity,
			       (e.payload ->> 'reorderLevel')::int AS reorder_level, e.payload ->> 'cause' AS cause,
			       e.created_at, i.quantity AS current_quantity, i.reorder_level AS current_reorder_level
			""";

	private final JdbcClient jdbc;

	public AlertService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public AlertPage search(String search, String cause, Boolean stillLow, Integer page, Integer size) {
		int pageNumber = page == null ? 0 : page;
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageNumber < 0) {
			throw badRequest("page: must be 0 or more");
		}
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			throw badRequest("size: must be between 1 and " + MAX_SIZE);
		}

		StringBuilder where = new StringBuilder();
		Map<String, Object> params = new LinkedHashMap<>();
		if (StringUtils.hasText(search)) {
			where.append(" AND (lower(e.payload ->> 'sku') LIKE :pattern OR lower(e.payload ->> 'name') LIKE :pattern)");
			params.put("pattern", "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%");
		}
		if (StringUtils.hasText(cause)) {
			where.append(" AND e.payload ->> 'cause' = :cause");
			params.put("cause", parseCause(cause.trim()).name());
		}
		if (Boolean.TRUE.equals(stillLow)) {
			where.append(" AND i.quantity <= i.reorder_level");
		}

		long total = jdbc.sql("SELECT count(*) " + FROM + where).params(params).query(Long.class).single();
		List<AlertResponse> alerts = jdbc
			.sql(SELECT + FROM + where + " ORDER BY e.created_at DESC, e.id DESC LIMIT :limit OFFSET :offset")
			.params(params)
			.param("limit", pageSize)
			.param("offset", (long) pageNumber * pageSize)
			.query((rs, row) -> {
				int currentQuantity = rs.getInt("current_quantity");
				int currentReorderLevel = rs.getInt("current_reorder_level");
				return new AlertResponse(rs.getString("id"), rs.getLong("item_id"), rs.getString("sku"),
						rs.getString("item_name"), rs.getInt("quantity"), rs.getInt("reorder_level"),
						rs.getString("cause"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
						currentQuantity, currentReorderLevel, currentQuantity <= currentReorderLevel);
			})
			.list();
		return new AlertPage(new ArrayList<>(alerts), pageNumber, pageSize, total,
				(int) ((total + pageSize - 1) / pageSize));
	}

	private static Cause parseCause(String cause) {
		try {
			return Cause.valueOf(cause);
		}
		catch (IllegalArgumentException ex) {
			throw badRequest("cause: must be one of "
					+ Arrays.stream(Cause.values()).map(Enum::name).collect(Collectors.joining(", ")));
		}
	}

	// % and _ are wildcards in LIKE; the user means them literally. Backslash is Postgres's default escape.
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
