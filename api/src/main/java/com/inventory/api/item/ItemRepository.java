package com.inventory.api.item;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

	Optional<Item> findByIdAndDeletedAtIsNull(Long id);

	@Query("select distinct i.category from Item i where i.deletedAt is null order by i.category")
	List<String> findActiveCategories();

	@Query("select distinct a from Item i join i.attributes a where i.deletedAt is null")
	List<ItemAttribute> findActiveAttributes();

}
