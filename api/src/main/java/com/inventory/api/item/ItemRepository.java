package com.inventory.api.item;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findAllByDeletedAtIsNull(Sort sort);

	Optional<Item> findByIdAndDeletedAtIsNull(Long id);

}
