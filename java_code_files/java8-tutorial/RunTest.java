/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.localizer.LocaleProvider;
import org.mockito.Mockito;


public class RunTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Issue("JENKINS-15816")
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test public void timezoneOfID() throws Exception {
        TimeZone origTZ = TimeZone.getDefault();
        try {
            final Run r;
            String id;
            TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
            ExecutorService svc = Executors.newSingleThreadExecutor();
            try {
                r = svc.submit(new Callable<Run>() {
                    @Override public Run call() throws Exception {
                        return new Run(new StubJob(), 1234567890) {};
                    }
                }).get();
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                id = r.getId();
                assertEquals(id, svc.submit(new Callable<String>() {
                    @Override public String call() throws Exception {
                        return r.getId();
                    }
                }).get());
            } finally {
                svc.shutdown();
            }
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            svc = Executors.newSingleThreadExecutor();
            try {
                assertEquals(id, r.getId());
                assertEquals(id, svc.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return r.getId();
                    }
                }).get());
            } finally {
                svc.shutdown();
            }
        } finally {
            TimeZone.setDefault(origTZ);
        }
    }
    

    private List<? extends Run<?, ?>.Artifact> createArtifactList(String... paths) throws Exception {
        Run r = new Run(new StubJob(), 0) {};
        Run.ArtifactList list = r.new ArtifactList();
        for (String p : paths) {
            list.add(r.new Artifact(p, p, p, String.valueOf(p.length()), "n" + list.size()));  // Assuming all test inputs don't need urlencoding
        }
        list.computeDisplayName();
        return list;
    }

    @Test
    public void artifactListDisambiguation1() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/j.xml");
        assertEquals(a.get(0).getDisplayPath(), "c.xml");
        assertEquals(a.get(1).getDisplayPath(), "g.xml");
        assertEquals(a.get(2).getDisplayPath(), "j.xml");
    }

    @Test
    public void artifactListDisambiguation2() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/g.xml");
        assertEquals(a.get(0).getDisplayPath(), "c.xml");
        assertEquals(a.get(1).getDisplayPath(), "f/g.xml");
        assertEquals(a.get(2).getDisplayPath(), "i/g.xml");
    }

    @Test
    public void artifactListDisambiguation3() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a.xml", "a/a.xml");
        assertEquals(a.get(0).getDisplayPath(), "a.xml");
        assertEquals(a.get(1).getDisplayPath(), "a/a.xml");
    }

    @Issue("JENKINS-26777")
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void getDurationString() throws IOException {
      LocaleProvider providerToRestore = LocaleProvider.getProvider();
      try {
        // This test expects English texts.
        LocaleProvider.setProvider(new LocaleProvider() {
            @Override
            public Locale get() {
                return Locale.ENGLISH;
            }
        });
        
        Run r = new Run(new StubJob(), 0) {};
        assertEquals("Not started yet", r.getDurationString());
        r.onStartBuilding();
        String msg;
        msg = r.getDurationString();
        assertTrue(msg, msg.endsWith(" and counting"));
        r.onEndBuilding();
        msg = r.getDurationString();
        assertFalse(msg, msg.endsWith(" and counting"));
      } finally {
        LocaleProvider.setProvider(providerToRestore);
      }
    }

    @Issue("JENKINS-27441")
    @Test
    public void getLogReturnsAnEmptyListWhenCalledWith0() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = tmp.newFolder();
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "utf-8");
        w.println("dummy");
        w.close();
        List<String> logLines = r.getLog(0);
        assertTrue(logLines.isEmpty());
    }

    @Test
    public void getLogReturnsAnRightOrder() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = tmp.newFolder();
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "utf-8");
        for (int i = 0; i < 20; i++) {
            w.println("dummy" + i);
        }

        w.close();
        List<String> logLines = r.getLog(10);
        assertFalse(logLines.isEmpty());

        for (int i = 1; i < 10; i++) {
            assertEquals("dummy" + (10+i), logLines.get(i));
        }
        int truncatedCount = 10* ("dummyN".length() + System.getProperty("line.separator").length()) - 2;
        assertEquals("[...truncated "+truncatedCount+" B...]", logLines.get(0));
    }

    @Test
    public void getLogReturnsAllLines() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = tmp.newFolder();
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "utf-8");
        w.print("a1\nb2\n\nc3");
        w.close();
        List<String> logLines = r.getLog(10);
        assertFalse(logLines.isEmpty());

        assertEquals("a1", logLines.get(0));
        assertEquals("b2", logLines.get(1));
        assertEquals("", logLines.get(2));
        assertEquals("c3", logLines.get(3));
    }

    @Test
    public void compareRunsFromSameJobWithDifferentNumbers() throws Exception {
        final Jenkins group = Mockito.mock(Jenkins.class);
        final Job j = Mockito.mock(Job.class);

        Mockito.when(j.getParent()).thenReturn(group);
        Mockito.when(group.getFullName()).thenReturn("j");
        Mockito.when(j.assignBuildNumber()).thenReturn(1, 2);

        Run r1 = new Run(j) {};
        Run r2 = new Run(j) {};

        final Set<Run> treeSet = new TreeSet<>();
        treeSet.add(r1);
        treeSet.add(r2);

        assertTrue(r1.compareTo(r2) < 0);
        assertTrue(treeSet.size() == 2);
    }

    @Issue("JENKINS-42319")
    @Test
    public void compareRunsFromDifferentParentsWithSameNumber() throws Exception {
        final Jenkins group1 = Mockito.mock(Jenkins.class);
        final Jenkins group2 = Mockito.mock(Jenkins.class);
        final Job j1 = Mockito.mock(Job.class);
        final Job j2 = Mockito.mock(Job.class);
        Mockito.when(j1.getParent()).thenReturn(group1);
        Mockito.when(j2.getParent()).thenReturn(group2);
        Mockito.when(group1.getFullName()).thenReturn("g1");
        Mockito.when(group2.getFullName()).thenReturn("g2");
        Mockito.when(j1.assignBuildNumber()).thenReturn(1);
        Mockito.when(j2.assignBuildNumber()).thenReturn(1);

        Run r1 = new Run(j1) {};
        Run r2 = new Run(j2) {};

        final Set<Run> treeSet = new TreeSet<>();
        treeSet.add(r1);
        treeSet.add(r2);

        assertTrue(r1.compareTo(r2) != 0);
        assertTrue(treeSet.size() == 2);
    }
}
