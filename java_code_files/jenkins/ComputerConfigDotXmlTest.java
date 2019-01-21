/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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
package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import hudson.security.ACL;
import hudson.security.AccessDeniedException2;
import hudson.security.GlobalMatrixAuthorizationStrategy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author ogondza
 */
public class ComputerConfigDotXmlTest {

    @Rule public final JenkinsRule rule = new JenkinsRule();

    @Mock private StaplerRequest req;
    @Mock private StaplerResponse rsp;

    private Computer computer;
    private SecurityContext oldSecurityContext;

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);
        computer = spy(rule.createSlave().toComputer());
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        oldSecurityContext = ACL.impersonate(User.get("user").impersonate());
    }

    @After
    public void tearDown() {

        SecurityContextHolder.setContext(oldSecurityContext);
    }

    @Test(expected = AccessDeniedException2.class)
    public void configXmlGetShouldFailForUnauthorized() throws Exception {

        when(req.getMethod()).thenReturn("GET");

        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        computer.doConfigDotXml(req, rsp);
    }

    @Test(expected = AccessDeniedException2.class)
    public void configXmlPostShouldFailForUnauthorized() throws Exception {

        when(req.getMethod()).thenReturn("POST");

        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        computer.doConfigDotXml(req, rsp);
    }

    @Test
    public void configXmlGetShouldYieldNodeConfiguration() throws Exception {

        when(req.getMethod()).thenReturn("GET");

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        Computer.EXTENDED_READ.setEnabled(true);
        auth.add(Computer.EXTENDED_READ, "user");

        final OutputStream outputStream = captureOutput();

        computer.doConfigDotXml(req, rsp);

        final String out = outputStream.toString();
        assertThat(out, startsWith("<?xml version=\"1.1\" encoding=\"UTF-8\"?>"));
        assertThat(out, containsString("<name>slave0</name>"));
        assertThat(out, containsString("<description>dummy</description>"));
    }

    @Test
    public void configXmlPostShouldUpdateNodeConfiguration() throws Exception {

        when(req.getMethod()).thenReturn("POST");

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        auth.add(Computer.CONFIGURE, "user");

        when(req.getInputStream()).thenReturn(xmlNode("node.xml"));

        computer.doConfigDotXml(req, rsp);

        final Node updatedSlave = rule.jenkins.getNode("SlaveFromXML");
        assertThat(updatedSlave.getNodeName(), equalTo("SlaveFromXML"));
        assertThat(updatedSlave.getNumExecutors(), equalTo(42));
    }

    @Test
    @Issue("SECURITY-343")
    public void emptyNodeMonitorDataWithoutConnect() throws Exception {
        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        assertTrue(computer.getMonitorData().isEmpty());
    }

    @Test
    @Issue("SECURITY-343")
    public void populatedNodeMonitorDataWithConnect() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        auth.add(Computer.CONNECT, "user");

        assertFalse(computer.getMonitorData().isEmpty());
    }



    private OutputStream captureOutput() throws IOException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(rsp.getOutputStream()).thenReturn(new ServletOutputStream() {

            @Override
            public void write(int b) throws IOException {
                baos.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                throw new UnsupportedOperationException();
            }
        });

        return baos;
    }

    private ServletInputStream xmlNode(final String name) {

        class Stream extends ServletInputStream {

            private final InputStream inner;

            public Stream(final InputStream inner) {
                this.inner = inner;
            }

            @Override
            public int read() throws IOException {
                return inner.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        }

        return new Stream(Computer.class.getResourceAsStream(name));
    }
}
