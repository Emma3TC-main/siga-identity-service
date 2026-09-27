package utp.siga.identity.application.port.in;

import java.util.UUID;
import utp.siga.identity.application.dto.Api;

public interface AuthenticationUseCase {
  Api.LoginResult login(Api.Login input, String ip);

  Api.EnrollmentResult enroll(Api.Enrollment input);

  Api.Tokens verify(Api.Mfa input);

  Api.Tokens refresh(Api.Refresh input);

  void logout(UUID userId, UUID familyId);
}
