package com.zjcxph.imgapi.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityTaskConnectionsTest {
    @Test
    void lateUninterruptibleAcquisitionClosesConnectionWithoutReturningIt() throws Exception {
        var source=mock(DataSource.class);
        var connection=mock(Connection.class);
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var closed=new CountDownLatch(1);
        when(source.getConnection()).thenAnswer(call->{
            entered.countDown();
            for (;;) {
                try { release.await(); break; }
                catch (InterruptedException ignored) { /* simulate a driver ignoring interrupt */ }
            }
            return connection;
        });
        doAnswer(call->{closed.countDown();return null;}).when(connection).close();
        var budget=new QualityTaskConnections();
        try {
            assertThatThrownBy(()->budget.acquire(source,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(200)))
                    .isInstanceOf(QueryTimeoutException.class);
            assertThat(entered.getCount()).isZero();
            verify(connection,never()).close();
        } finally {release.countDown();}
        assertThat(closed.await(2,TimeUnit.SECONDS)).isTrue();
        verify(connection,times(1)).close();
    }

    @Test
    void successfulHandoffDoesNotCloseConnectionBeforeCaller() throws Exception {
        var source=mock(DataSource.class);
        var connection=mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var budget=new QualityTaskConnections();
        try(var result=budget.acquire(source,System.nanoTime()+TimeUnit.SECONDS.toNanos(2))) {
            assertThat(result).isSameAs(connection);
            verify(connection,never()).close();
        }
        verify(connection,times(1)).close();
    }
}
