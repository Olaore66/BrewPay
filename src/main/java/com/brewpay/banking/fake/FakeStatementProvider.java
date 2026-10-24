package com.brewpay.banking.fake;

import com.brewpay.banking.StatementLine;
import com.brewpay.banking.StatementProvider;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * Local stand-in for a provider statement. A test adds lines. A merchant request cannot. An empty
 * script returns no lines.
 */
@Service
public class FakeStatementProvider implements StatementProvider {

  private final ConcurrentHashMap<UUID, List<StatementLine>> lines = new ConcurrentHashMap<>();

  public void addLine(UUID organizationId, StatementLine line) {
    lines.computeIfAbsent(organizationId, ignored -> new CopyOnWriteArrayList<>()).add(line);
  }

  public void clear() {
    lines.clear();
  }

  @Override
  public List<StatementLine> fetch(UUID organizationId) {
    return List.copyOf(lines.getOrDefault(organizationId, List.of()));
  }
}
