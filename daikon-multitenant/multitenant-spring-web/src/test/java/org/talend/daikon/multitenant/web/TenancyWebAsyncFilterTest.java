// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.daikon.multitenant.web;

import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptorAdapter;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.talend.daikon.multitenant.context.TenancyContext;
import org.talend.daikon.multitenant.context.TenancyContextHolder;

/**
 * @author agonzalez
 */
@RunWith(MockitoJUnitRunner.class)
public class TenancyWebAsyncFilterTest {

    @Mock
    private TenancyContext tenancyContext;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AsyncWebRequest asyncWebRequest;

    private WebAsyncManager asyncManager;

    private JoinableThreadFactory threadFactory;

    private MockFilterChain filterChain;

    private TenancyWebAsyncFilter filter;

    @Before
    public void setUp() {
        when(asyncWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(request);
        when(request.getRequestURI()).thenReturn("/");
        filterChain = new MockFilterChain();

        threadFactory = new JoinableThreadFactory();
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setThreadFactory(threadFactory);

        asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.setAsyncWebRequest(asyncWebRequest);
        asyncManager.setTaskExecutor(executor);
        when(request.getAttribute(WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE)).thenReturn(asyncManager);

        filter = new TenancyWebAsyncFilter();
    }

    @After
    public void clearTenancyContext() {
        TenancyContextHolder.clearContext();
    }

    @Test
    public void doFilterInternalRegistersTenancyContextCallableProcessor() throws Exception {
        TenancyContextHolder.setContext(tenancyContext);
        asyncManager.registerCallableInterceptors(new CallableProcessingInterceptorAdapter() {

            @Override
            public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) throws Exception {
                assertNotSame(tenancyContext, TenancyContextHolder.getContext());
            }
        });
        filter.doFilterInternal(request, response, filterChain);

        VerifyingCallable verifyingCallable = new VerifyingCallable();
        asyncManager.startCallableProcessing(verifyingCallable);
        threadFactory.join();
        assertSame(tenancyContext, asyncManager.getConcurrentResult());
    }

    @Test
    public void doFilterInternalRegistersTenancyContextCallableProcessorContextUpdated() throws Exception {
        TenancyContextHolder.setContext(TenancyContextHolder.createEmptyContext());
        asyncManager.registerCallableInterceptors(new CallableProcessingInterceptorAdapter() {

            @Override
            public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) throws Exception {
                assertNotSame(tenancyContext, TenancyContextHolder.getContext());
            }
        });
        filter.doFilterInternal(request, response, filterChain);
        TenancyContextHolder.setContext(tenancyContext);

        VerifyingCallable verifyingCallable = new VerifyingCallable();
        asyncManager.startCallableProcessing(verifyingCallable);
        threadFactory.join();
        assertSame(tenancyContext, asyncManager.getConcurrentResult());
    }

    private static final class JoinableThreadFactory implements ThreadFactory {

        private Thread t;

        public Thread newThread(Runnable r) {
            t = new Thread(r);
            return t;
        }

        public void join() throws InterruptedException {
            t.join();
        }
    }

    private static class VerifyingCallable implements Callable<TenancyContext> {

        public TenancyContext call() throws Exception {
            return TenancyContextHolder.getContext();
        }

    }
}