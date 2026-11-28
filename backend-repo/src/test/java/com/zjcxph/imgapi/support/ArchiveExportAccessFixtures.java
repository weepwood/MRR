package com.zjcxph.imgapi.support;

import com.zjcxph.imgapi.service.ArchiveExportExecutionAccess;
import java.util.function.IntSupplier;
import static org.mockito.Mockito.*;

/** For pre-existing isolated state/IO tests that do not create auth fixtures. */
public final class ArchiveExportAccessFixtures {
    private ArchiveExportAccessFixtures() { }
    public static ArchiveExportExecutionAccess allow() {
        var access = mock(ArchiveExportExecutionAccess.class);
        when(access.publish(any(), any())).thenAnswer(call -> call.<IntSupplier>getArgument(1).getAsInt());
        return access;
    }
}
