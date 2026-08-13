package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.service.serviceimpl.UserSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void markChanged_BindsPostgresTimestamp() {
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(42L)))
            .thenReturn(1);
        UserSyncServiceImpl service = new UserSyncServiceImpl(jdbcTemplate);

        service.markChanged(42L);

        ArgumentCaptor<Object> timestamp = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
            anyString(), timestamp.capture(), org.mockito.ArgumentMatchers.eq(42L)
        );
        assertInstanceOf(Timestamp.class, timestamp.getValue());
    }
}
