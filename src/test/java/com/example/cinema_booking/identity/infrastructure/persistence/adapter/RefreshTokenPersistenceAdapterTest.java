package com.example.cinema_booking.identity.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.out.RefreshTokenHasherPort;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.security.Sha256RefreshTokenHasher;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenHasherPort refreshTokenHasherPort;

  private RefreshTokenPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new RefreshTokenPersistenceAdapter(
            refreshTokenRepository, userRepository, refreshTokenHasherPort);
  }

  @Test
  void saveHashesRawTokenAndPersistsEntityLinkedToUser() {
    UserEntity userReference = new UserEntity();
    userReference.setId(1L);
    OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(30);
    when(userRepository.getReferenceById(1L)).thenReturn(userReference);
    when(refreshTokenHasherPort.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");

    adapter.save(1L, "raw-refresh-token", expiresAt);

    ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
    verify(refreshTokenRepository).save(captor.capture());
    RefreshTokenEntity saved = captor.getValue();
    assertThat(saved.getUser()).isSameAs(userReference);
    assertThat(saved.getTokenHash()).isEqualTo("hashed-refresh-token");
    assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void saveNeverPersistsRawTokenAsHash() {
    when(userRepository.getReferenceById(any(Long.class))).thenReturn(new UserEntity());
    when(refreshTokenHasherPort.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");

    adapter.save(1L, "raw-refresh-token", OffsetDateTime.now().plusDays(30));

    ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
    verify(refreshTokenRepository).save(captor.capture());
    assertThat(captor.getValue().getTokenHash()).isNotEqualTo("raw-refresh-token");
  }

  @Test
  void saveDoesNotThrowForFullLength86CharacterOpaqueRefreshToken() {
    RefreshTokenPersistenceAdapter realAdapter =
        new RefreshTokenPersistenceAdapter(
            refreshTokenRepository, userRepository, new Sha256RefreshTokenHasher());
    when(userRepository.getReferenceById(1L)).thenReturn(new UserEntity());
    String eightySixCharToken = "a".repeat(86);

    assertThatCode(
            () -> realAdapter.save(1L, eightySixCharToken, OffsetDateTime.now().plusDays(30)))
        .doesNotThrowAnyException();
  }
}
