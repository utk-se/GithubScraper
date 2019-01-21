/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.security;

import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import jenkins.util.InterceptingExecutorService;
import org.acegisecurity.Authentication;

/**
 * Uses {@link ACL#impersonate(Authentication)} for all tasks.
 * @see SecurityContextExecutorService
 * @since 2.51
 */
public final class ImpersonatingExecutorService extends InterceptingExecutorService {

    private final Authentication authentication;

    /**
     * Creates a wrapper service.
     * @param base the base service
     * @param authentication for example {@link ACL#SYSTEM}
     */
    public ImpersonatingExecutorService(ExecutorService base, Authentication authentication) {
        super(base);
        this.authentication = authentication;
    }

    @Override
    protected Runnable wrap(final Runnable r) {
        return new Runnable() {
            @Override
            public void run() {
                try (ACLContext ctxt = ACL.as(authentication)) {
                    r.run();
                }
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> r) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                try (ACLContext ctxt = ACL.as(authentication)) {
                    return r.call();
                }
            }
        };
    }

}
