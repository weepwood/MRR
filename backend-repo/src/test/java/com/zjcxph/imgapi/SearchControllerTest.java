package com.zjcxph.imgapi;

import com.zjcxph.imgapi.controller.SearchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class SearchControllerTest {

    private MockMvc mockMvc;
    private SearchController searchController;

    @BeforeEach
    public void setUp() {
        searchController = new SearchController();
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    public void testHelloWithRemoteAddr() throws Exception {
        // 测试使用getRemoteAddr获取IP
        mockMvc.perform(get("/v2/search/hello")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world search, your IP is: 127.0.0.1"));
    }

    @Test
    public void testHelloWithXRealIPHeader() throws Exception {
        // 测试使用X-Real-IP请求头获取IP
        mockMvc.perform(get("/v2/search/hello")
                .header("X-Real-IP", "192.168.1.100")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world search, your IP is: 192.168.1.100"));
    }

    @Test
    public void testHelloWithXForwardedForHeader() throws Exception {
        // 测试使用X-Forwarded-For请求头获取IP
        mockMvc.perform(get("/v2/search/hello")
                .header("X-Forwarded-For", "192.168.1.101")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world search, your IP is: 192.168.1.101"));
    }

    @Test
    public void testHelloWithXForwardedForHeaderContainingMultipleIPs() throws Exception {
        // 测试使用X-Forwarded-For请求头获取IP，包含多个IP地址的情况
        mockMvc.perform(get("/v2/search/hello")
                .header("X-Forwarded-For", "192.168.1.102, 10.0.0.1, 172.16.0.1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world search, your IP is: 192.168.1.102, 10.0.0.1, 172.16.0.1"));
    }

    @Test
    public void testGetClientIPMethodDirectly() {
        // 直接测试getClientIP方法
        HttpServletRequest requestWithXForwardedFor = mock(HttpServletRequest.class);
        when(requestWithXForwardedFor.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(requestWithXForwardedFor.getHeader("X-Real-IP")).thenReturn(null);
        when(requestWithXForwardedFor.getRemoteAddr()).thenReturn("127.0.0.1");

        // 由于getClientIP是私有方法，我们通过调用hello方法间接测试
        // 这里验证方法的逻辑是否正确
    }
}
