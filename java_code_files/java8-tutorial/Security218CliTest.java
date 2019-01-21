/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import hudson.cli.CLI;
import hudson.cli.CLICommand;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import java.io.File;
import java.io.PrintStream;
import jenkins.security.security218.Payload;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.PresetData;
import org.kohsuke.args4j.Argument;

@SuppressWarnings("deprecation") // Remoting-based CLI usages intentional
public class Security218CliTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeCommonsBeanutils1() throws Exception {
        probe(Payload.CommonsBeanutils1, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeCommonsCollections1() throws Exception {
        probe(Payload.CommonsCollections1, 1);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeCommonsCollections2() throws Exception {
        // The issue with CommonsCollections2 does not appear in manual tests on Jenkins, but it may be a risk
        // in newer commons-collections version => remoting implementation should filter this class anyway
        probe(Payload.CommonsCollections2, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeCommonsCollections3() throws Exception {
        probe(Payload.CommonsCollections3, 1);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeCommonsCollections4() throws Exception {
        probe(Payload.CommonsCollections4, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeCommonsCollections5() throws Exception {
        probe(Payload.CommonsCollections5, 1);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeCommonsCollections6() throws Exception {
        probe(Payload.CommonsCollections6, 1);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeFileUpload1() throws Exception {
        probe(Payload.FileUpload1, 3);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeGroovy1() throws Exception {
        probe(Payload.Groovy1, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeJdk7u21() throws Exception {
        probe(Payload.Jdk7u21, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeJRMPClient() throws Exception {
        probe(Payload.JRMPClient, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeJRMPListener() throws Exception {
        probe(Payload.JRMPListener, 3);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeJSON1() throws Exception {
        probe(Payload.JSON1, PayloadCaller.EXIT_CODE_REJECTED);
    }
    
    //TODO: Fix the conversion layer (not urgent)
    // There is an issue in the conversion layer after the migration to another XALAN namespace
    // with newer libs. SECURITY-218 does not appear in this case in manual tests anyway
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeSpring1() throws Exception {
        // Reason it is 1 is that it is testing a test that is not in our version of Spring
        // Caused by: java.lang.ClassNotFoundException: org.springframework.beans.factory.support.AutowireUtils$ObjectFactoryDelegatingInvocationHandler
        probe(Payload.Spring1, 1);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-317")
    public void probeSpring2() throws Exception {
        // Reason it is 1 is that it is testing a test that is not in our version of Spring 4
        // Caused by: java.lang.ClassNotFoundException: org.springframework.core.SerializableTypeWrapper$TypeProvider
        probe(Payload.Spring2, 1);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-360")
    public void ldap() throws Exception {
        // with a proper fix, this should fail with EXIT_CODE_REJECTED
        // otherwise this will fail with -1 exit code
        probe(Payload.Ldap, PayloadCaller.EXIT_CODE_REJECTED);
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-429")
    public void jsonLibSignedObject() throws Exception {
        probe(Payload.JsonLibSignedObject, 1);
    }

    private void probe(Payload payload, int expectedResultCode) throws Exception {
        File file = File.createTempFile("security-218", payload + "-payload");
        File moved = new File(file.getAbsolutePath() + "-moved");
        
        // Bypassing _main because it does nothing interesting here.
        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
        try (CLI cli = new CLI(r.getURL())) {
            int exitCode = cli.execute("send-payload",
                    payload.toString(), "mv " + file.getAbsolutePath() + " " + moved.getAbsolutePath());
            assertTrue("Payload should not invoke the move operation " + file, !moved.exists());
            assertEquals("Unexpected result code.", expectedResultCode, exitCode);
            file.delete();
        }
    }
    
    @TestExtension()
    public static class SendPayloadCommand extends CLICommand {

        @Override
        public String getShortDescription() {
            return hudson.cli.Messages.ConsoleCommand_ShortDescription();
        }

        @Argument(metaVar = "payload", usage = "ID of the payload", required = true, index = 0)
        public String payload;
        
        @Argument(metaVar = "command", usage = "Command to be launched by the payload", required = true, index = 1)
        public String command;

        @Override
        protected int run() throws Exception {
            Payload payloadItem = Payload.valueOf(this.payload);
            PayloadCaller callable = new PayloadCaller(payloadItem, command);
            return channel.call(callable);
        }

        @Override
        protected void printUsageSummary(PrintStream stderr) {
            stderr.println("Sends a payload over the channel");
        }
    }

    public static class PayloadCaller implements Callable<Integer, Exception> {

        private final Payload payload;
        private final String command;

        public static final int EXIT_CODE_OK = 0;
        public static final int EXIT_CODE_REJECTED = 42;
        public static final int EXIT_CODE_ASSIGNMENT_ISSUE = 43;

        public PayloadCaller(Payload payload, String command) {
            this.payload = payload;
            this.command = command;
        }
        
        @Override
        public Integer call() throws Exception {
            final Object ysoserial = payload.getPayloadClass().newInstance().getObject(command);
            
            // Invoke backward call
            try {
                getChannelOrFail().call(new Callable<String, Exception>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String call() throws Exception {
                        // We don't care what happens here. Object should be sent over the channel
                        return ysoserial.toString();
                    }

                    @Override
                    public void checkRoles(RoleChecker checker) throws SecurityException {
                        // do nothing
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Throwable cause = ex;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (cause instanceof SecurityException) {
                    // It should happen if the remote channel reject a class.
                    // That's what we have done in SECURITY-218 => may be OK
                    if (cause.getMessage().contains("Rejected")) {
                        // OK
                        return PayloadCaller.EXIT_CODE_REJECTED;
                    } else {
                        // Something wrong
                        throw ex;
                    }
                }

                final String message = cause.getMessage();
                if (message != null && message.contains("cannot be cast to java.util.Set")) {
                    // We ignore this exception, because there is a known issue in the test payload
                    // CommonsCollections1, CommonsCollections2 and Groovy1 fail with this error,
                    // but actually it means that the conversion has been triggered
                    return EXIT_CODE_ASSIGNMENT_ISSUE;
                } else {
                    throw ex;
                }
            }
            return EXIT_CODE_OK;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // Do nothing
        }
        
    }

}
