package com.brewpay.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

  private final CollectionService collections;

  public CollectionController(CollectionService collections) {
    this.collections = collections;
  }

  @GetMapping
  public List<CollectionResponse> list() {
    return collections.list();
  }

  @GetMapping("/{id}")
  public CollectionResponse get(@PathVariable UUID id) {
    return collections.get(id);
  }
}
