package com.brewpay.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final SessionService sessions;

  public SessionController(SessionService sessions) {
    this.sessions = sessions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
    return sessions.login(request);
  }

  @DeleteMapping("/current")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke() {
    sessions.revokeCurrent();
  }
}
