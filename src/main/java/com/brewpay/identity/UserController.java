package com.brewpay.identity;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return users.create(request);
  }

  @GetMapping
  public List<UserResponse> list() {
    return users.list();
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable UUID id) {
    return users.get(id);
  }
}
