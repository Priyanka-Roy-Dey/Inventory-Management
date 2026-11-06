package com.inventory.api.item;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
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

	// Loaded in batches, so a page of items needs a few queries rather than one per item.
	@ElementCollection
	@CollectionTable(name = "item_attributes", joinColumns = @JoinColumn(name = "item_id"))
	@BatchSize(size = 100)
	private List<ItemAttribute> attributes = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "item_delivery_pincodes", joinColumns = @JoinColumn(name = "item_id"))
	@Column(name = "pincode", nullable = false, length = 6)
	@BatchSize(size = 100)
	private Set<String> deliveryPincodes = new HashSet<>();

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

	/**
	 * Replaces the item's attributes and delivery pincodes (A40).
	 */
	public void replaceDetails(Collection<ItemAttribute> newAttributes, Collection<String> newPincodes) {
		attributes.clear();
		attributes.addAll(newAttributes);
		deliveryPincodes.clear();
		deliveryPincodes.addAll(newPincodes);
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

	public List<ItemAttribute> getAttributes() {
		return attributes;
	}

	public Set<String> getDeliveryPincodes() {
		return deliveryPincodes;
	}

}
