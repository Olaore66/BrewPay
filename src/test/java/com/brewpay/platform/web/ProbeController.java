package com.brewpay.platform.web;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test-only routes. This class is not on the production classpath. */
@RestController
class ProbeController {

  @GetMapping("/api/v1/probe/missing")
  void missing() {
    throw new PlatformException(ErrorCode.NOT_FOUND, "Account was not found.");
  }

  @GetMapping("/api/v1/probe/boom")
  void boom() {
    throw new IllegalStateException("secret database password");
  }

  @PostMapping("/api/v1/probe")
  void create(@Valid @RequestBody ProbeRequest request) {}

  record ProbeRequest(@NotBlank String name) {}
}
