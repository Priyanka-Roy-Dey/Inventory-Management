package com.inventory.api.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "items")
public class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String sku;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 100)
	private String category;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "reorder_level", nullable = false)
	private int reorderLevel;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Version
	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "image_key", length = 200)
	private String imageKey;

	protected Item() {
	}

	public Item(String sku, String name, String category, int quantity, int reorderLevel, BigDecimal unitPrice) {
		this.sku = sku;
		this.name = name;
		this.category = category;
		this.quantity = quantity;
		this.reorderLevel = reorderLevel;
		this.unitPrice = unitPrice;
	}

	/**
	 * Changes the editable details. SKU and quantity are not editable here (A33, §2.2).
	 */
	public void update(String name, String category, int reorderLevel, BigDecimal unitPrice) {
		this.name = name;
		this.category = category;
		this.reorderLevel = reorderLevel;
		this.unitPrice = unitPrice;
	}

	/**
	 * Hides the item everywhere but keeps the row, so its SKU stays reserved (A7).
	 */
	public void softDelete(Instant now) {
		this.deletedAt = now;
	}

	public void attachImage(String key) {
		this.imageKey = key;
	}

	public void removeImage() {
		this.imageKey = null;
	}

	public boolean isLowStock() {
		return quantity <= reorderLevel;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getReorderLevel() {
		return reorderLevel;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public String getImageKey() {
		return imageKey;
	}

}
