package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.message.ConversationDetailResponse;
import com.example.recruitmentsystem.dto.message.ConversationDto;
import com.example.recruitmentsystem.entity.Conversation;
import com.example.recruitmentsystem.entity.Message;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.ConversationMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.MessageAttachmentMapper;
import com.example.recruitmentsystem.mapper.MessageMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import com.example.recruitmentsystem.service.message.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConversationService 单元测试。
 *
 * <p>覆盖：findOrCreate 二元组唯一 / listMine / open 自动已读 / listMessages 分页 / 鉴权拒绝。</p>
 */
class ConversationServiceTest {

    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private MessageAttachmentMapper attachmentMapper;
    private UserMapper userMapper;
    private JobMapper jobMapper;
    private SseEmitterManager sseManager;
    private ConversationServiceImpl svc;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(MessageMapper.class);
        attachmentMapper = mock(MessageAttachmentMapper.class);
        userMapper = mock(UserMapper.class);
        jobMapper = mock(JobMapper.class);
        sseManager = mock(SseEmitterManager.class);

        svc = new ConversationServiceImpl(conversationMapper, messageMapper, attachmentMapper,
                userMapper, jobMapper, sseManager);

        // 默认 attachment / job 查询为空
        when(attachmentMapper.selectByMessageIds(any())).thenReturn(Collections.emptyList());
        when(jobMapper.selectById(any())).thenReturn(null);
    }

    @Test
    void findOrCreate_hrInitiator_usesCandidateAsPeer() {
        User hr = new User();
        hr.setId(1L); hr.setRoleCode("HR"); hr.setStatus("ENABLED"); hr.setUsername("HR-1");
        User cand = new User();
        cand.setId(2L); cand.setRoleCode("CANDIDATE"); cand.setStatus("ENABLED"); cand.setUsername("C-2");
        when(userMapper.selectById(1L)).thenReturn(hr);
        when(userMapper.selectById(2L)).thenReturn(cand);
        when(conversationMapper.findByHrAndCandidate(1L, 2L)).thenReturn(null);

        ConversationDto dto = svc.findOrCreate(1L, "HR", 2L);

        // insert 是 mock，不会回填 id；DTO.id 来自 Conversation.getId() → null
        org.junit.jupiter.api.Assertions.assertNull(dto.getId());
        assertEquals(1L, dto.getHrUserId());
        assertEquals(2L, dto.getCandidateId());
        assertEquals(2L, dto.getPeerId());
        assertEquals("CANDIDATE", dto.getPeerRole());
        // 验证 conversationMapper.insert 被调用一次（新建）
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper, times(1)).insert(captor.capture());
        assertEquals(1L, captor.getValue().getHrUserId());
        assertEquals(2L, captor.getValue().getCandidateId());
    }

    @Test
    void findOrCreate_candidateInitiator_usesHrAsPeer() {
        User hr = new User();
        hr.setId(1L); hr.setRoleCode("HR"); hr.setStatus("ENABLED"); hr.setUsername("HR-1");
        User cand = new User();
        cand.setId(99L); cand.setRoleCode("CANDIDATE"); cand.setStatus("ENABLED");
        when(userMapper.selectById(1L)).thenReturn(hr);
        when(userMapper.selectById(99L)).thenReturn(cand);
        when(conversationMapper.findByHrAndCandidate(1L, 99L)).thenReturn(null);

        ConversationDto dto = svc.findOrCreate(99L, "CANDIDATE", 1L);

        assertEquals(1L, dto.getHrUserId());
        assertEquals(99L, dto.getCandidateId());
        assertEquals(1L, dto.getPeerId());
        assertEquals("HR", dto.getPeerRole());
    }

    @Test
    void findOrCreate_existing_returnsSameWithoutInsert() {
        Conversation existed = new Conversation();
        existed.setId(50L);
        existed.setHrUserId(1L);
        existed.setCandidateId(2L);
        existed.setLastMessageAt(LocalDateTime.now());
        when(conversationMapper.findByHrAndCandidate(1L, 2L)).thenReturn(existed);
        User hr = new User();
        hr.setId(1L); hr.setRoleCode("HR"); hr.setStatus("ENABLED");
        User peer = new User();
        peer.setId(2L); peer.setRoleCode("CANDIDATE"); peer.setStatus("ENABLED"); peer.setUsername("C-2");
        when(userMapper.selectById(1L)).thenReturn(hr);
        when(userMapper.selectById(2L)).thenReturn(peer);

        ConversationDto dto = svc.findOrCreate(1L, "HR", 2L);

        assertEquals(50L, dto.getId());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void findOrCreate_adminRole_rejected() {
        assertThrows(BusinessException.class,
                () -> svc.findOrCreate(99L, "ADMIN", 1L));
    }

    @Test
    void findOrCreate_peerRoleMismatch_rejected() {
        User notHr = new User();
        notHr.setId(5L); notHr.setRoleCode("CANDIDATE"); notHr.setStatus("ENABLED");
        when(userMapper.selectById(5L)).thenReturn(notHr);

        // 候选人视角想联系"HR"，但 5L 实际是候选人 → 拒
        assertThrows(BusinessException.class,
                () -> svc.findOrCreate(99L, "CANDIDATE", 5L));
    }

    @Test
    void open_nonParticipant_throws403() {
        Conversation c = new Conversation();
        c.setId(10L);
        c.setHrUserId(1L);
        c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        // requesterId=99 不在会话中
        assertThrows(BusinessException.class,
                () -> svc.open(99L, "HR", 10L, 50));
    }

    @Test
    void open_hrEnters_marksCandidateMessagesRead_pushesReadReceipt() {
        Conversation c = new Conversation();
        c.setId(10L);
        c.setHrUserId(1L);
        c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        User peer = new User();
        peer.setId(2L); peer.setRoleCode("CANDIDATE"); peer.setUsername("C-2");
        when(userMapper.selectById(2L)).thenReturn(peer);

        // 最近 1 条：候选人发的未读消息
        Message candMsg = new Message();
        candMsg.setId(100L);
        candMsg.setConversationId(10L);
        candMsg.setSenderId(2L);
        candMsg.setSenderRole(Message.ROLE_CANDIDATE);
        candMsg.setContent("你好");
        when(messageMapper.selectRecentByConversation(eq(10L), anyInt()))
                .thenReturn(new ArrayList<>(List.of(candMsg)));
        when(messageMapper.markPeerMessagesRead(10L, Message.ROLE_HR)).thenReturn(1);

        ConversationDetailResponse resp = svc.open(1L, "HR", 10L, 50);

        assertNotNull(resp);
        assertEquals(10L, resp.getConversation().getId());
        assertEquals(1, resp.getMessages().size());
        assertEquals(100L, resp.getMessages().get(0).getId());
        // 候选人侧 read_receipt 推送被调用
        verify(sseManager, times(1)).pushToUser(eq(2L), eq("read_receipt"), any());
    }

    @Test
    void listMessages_nonParticipant_throws403() {
        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        assertThrows(BusinessException.class,
                () -> svc.listMessages(99L, "HR", 10L, 1, 20));
    }

    @Test
    void unreadStats_hr_returnsCountFromMapper() {
        when(messageMapper.countMyUnread(1L, "HR")).thenReturn(7);
        var resp = svc.unreadStats(1L, "HR");
        assertEquals(7, resp.getTotal());
    }

    @Test
    void unreadStats_admin_returnsZero() {
        var resp = svc.unreadStats(99L, "ADMIN");
        assertEquals(0, resp.getTotal());
    }

    @Test
    void listMine_hr_returnsEnrichedList() {
        Conversation c1 = new Conversation();
        c1.setId(1L); c1.setHrUserId(1L); c1.setCandidateId(2L);
        Conversation c2 = new Conversation();
        c2.setId(2L); c2.setHrUserId(1L); c2.setCandidateId(3L);
        when(conversationMapper.listMine(1L)).thenReturn(List.of(c1, c2));
        User peer2 = new User();
        peer2.setId(2L); peer2.setRoleCode("CANDIDATE"); peer2.setUsername("C-2");
        User peer3 = new User();
        peer3.setId(3L); peer3.setRoleCode("CANDIDATE"); peer3.setUsername("C-3");
        when(userMapper.selectById(2L)).thenReturn(peer2);
        when(userMapper.selectById(3L)).thenReturn(peer3);
        when(messageMapper.selectRecentByConversation(anyLong(), eq(1))).thenReturn(Collections.emptyList());
        when(messageMapper.countUnread(anyLong(), anyString())).thenReturn(0);

        var result = svc.listMine(1L, "HR");
        assertEquals(2, result.size());
        assertEquals("C-2", result.get(0).getPeerName());
        assertEquals("C-3", result.get(1).getPeerName());
    }
}
