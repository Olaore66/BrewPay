package com.brewpay.platform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

  @GetMapping("/health")
  public Health status() {
    return new Health("UP");
  }

  public record Health(String status) {}
}
