package com.fluxcore.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fluxcore.approval.dto.ApprovalTaskQueryItem;
import com.fluxcore.approval.mapper.ApprovalTaskMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalTaskQueryServiceTest {
    @Mock private ApprovalTaskMapper taskMapper;

    private ApprovalTaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalTaskQueryService(taskMapper);
    }

    @Test
    void findTodo_shouldQueryPendingTasksForTrimmedAssignee() {
        List<ApprovalTaskQueryItem> expected = List.of(new ApprovalTaskQueryItem());
        when(taskMapper.selectTodoByAssignee("U2001")).thenReturn(expected);

        List<ApprovalTaskQueryItem> actual = service.findTodo(" U2001 ");

        assertSame(expected, actual);
        verify(taskMapper).selectTodoByAssignee("U2001");
    }

    @Test
    void findDone_shouldQueryNonPendingTasks() {
        List<ApprovalTaskQueryItem> expected = List.of(new ApprovalTaskQueryItem());
        when(taskMapper.selectDoneByAssignee("U2001")).thenReturn(expected);

        List<ApprovalTaskQueryItem> actual = service.findDone("U2001");

        assertSame(expected, actual);
        verify(taskMapper).selectDoneByAssignee("U2001");
    }

    @Test
    void findTodo_withoutAssignee_shouldReturnBadRequest() {
        ApprovalQueryException exception = assertThrows(ApprovalQueryException.class,
                () -> service.findTodo("  "));

        assertEquals("ASSIGNEE_ID_REQUIRED", exception.getCode());
        assertEquals(400, exception.getStatus().value());
    }
}
