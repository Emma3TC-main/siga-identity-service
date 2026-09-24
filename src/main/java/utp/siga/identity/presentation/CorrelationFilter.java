package utp.siga.identity.presentation;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class CorrelationFilter extends OncePerRequestFilter {
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  public static UUID current() {
    UUID id = CURRENT.get();
    return id == null ? UUID.randomUUID() : id;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    UUID id;
    try {
      id = UUID.fromString(req.getHeader("X-Correlation-ID"));
    } catch (Exception e) {
      id = UUID.randomUUID();
    }
    CURRENT.set(id);
    res.setHeader("X-Correlation-ID", id.toString());
    res.setHeader("Cache-Control", "no-store");
    try {
      chain.doFilter(req, res);
    } finally {
      CURRENT.remove();
    }
  }
}
