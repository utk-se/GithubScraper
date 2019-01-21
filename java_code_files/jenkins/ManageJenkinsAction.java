/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * Adds the "Manage Jenkins" link to the top page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=100) @Symbol("manageJenkins")
public class ManageJenkinsAction implements RootAction {
    public String getIconFileName() {
        if (Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER))
            return "gear2.png";
        else
            return null;
    }

    public String getDisplayName() {
        return Messages.ManageJenkinsAction_DisplayName();
    }

    public String getUrlName() {
        return "/manage";
    }
}
