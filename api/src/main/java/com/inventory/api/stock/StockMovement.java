package com.inventory.api.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_id", nullable = false, updatable = false)
	private Long itemId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private MovementType type;

	@Column(name = "quantity_change", nullable = false, updatable = false)
	private int quantityChange;

	@Column(name = "quantity_before", nullable = false, updatable = false)
	private int quantityBefore;

	@Column(name = "quantity_after", nullable = false, updatable = false)
	private int quantityAfter;

	@Column(length = 500, updatable = false)
	private String reason;

	// The username rather than a foreign key, so the history still reads correctly if the user is removed.
	@Column(name = "created_by", nullable = false, length = 50, updatable = false)
	private String createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StockMovement() {
	}

	public StockMovement(Long itemId, MovementType type, int quantityChange, int quantityBefore, int quantityAfter,
			String reason, String createdBy) {
		this.itemId = itemId;
		this.type = type;
		this.quantityChange = quantityChange;
		this.quantityBefore = quantityBefore;
		this.quantityAfter = quantityAfter;
		this.reason = reason;
		this.createdBy = createdBy;
	}

	public Long getId() {
		return id;
	}

	public Long getItemId() {
		return itemId;
	}

	public MovementType getType() {
		return type;
	}

	public int getQuantityChange() {
		return quantityChange;
	}

	public int getQuantityBefore() {
		return quantityBefore;
	}

	public int getQuantityAfter() {
		return quantityAfter;
	}

	public String getReason() {
		return reason;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
