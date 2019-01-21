/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package hudson.os;

import hudson.Functions;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

// adapted from:
// https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/
public class WindowsUtil {
    private static final Pattern NEEDS_QUOTING = Pattern.compile("[\\s\"]");

    /**
     * Quotes an argument while escaping special characters interpreted by CreateProcess.
     */
    public static @Nonnull String quoteArgument(@Nonnull String argument) {
        if (!NEEDS_QUOTING.matcher(argument).find()) return argument;
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        int end = argument.length();
        for (int i = 0; i < end; i++) {
            int nrBackslashes = 0;
            while (i < end && argument.charAt(i) == '\\') {
                i++;
                nrBackslashes++;
            }

            if (i == end) {
                // backslashes at the end of the argument must be escaped so the terminate quote isn't
                nrBackslashes = nrBackslashes * 2;
            } else if (argument.charAt(i) == '"') {
                // backslashes preceding a quote all need to be escaped along with the quote
                nrBackslashes = nrBackslashes * 2 + 1;
            }
            // else backslashes have no special meaning and don't need to be escaped here

            for (int j = 0; j < nrBackslashes; j++) {
                sb.append('\\');
            }

            if (i < end) {
                sb.append(argument.charAt(i));
            }
        }
        return sb.append('"').toString();
    }

    private static final Pattern CMD_METACHARS = Pattern.compile("[()%!^\"<>&|]");

    /**
     * Quotes an argument while escaping special characters suitable for use as an argument to {@code cmd.exe}.
     */
    public static @Nonnull String quoteArgumentForCmd(@Nonnull String argument) {
        return CMD_METACHARS.matcher(quoteArgument(argument)).replaceAll("^$0");
    }

    /**
     * Executes a command and arguments using {@code cmd.exe /C ...}.
     */
    public static @Nonnull Process execCmd(String... argv) throws IOException {
        String command = Arrays.stream(argv).map(WindowsUtil::quoteArgumentForCmd).collect(Collectors.joining(" "));
        return Runtime.getRuntime().exec(new String[]{"cmd.exe", "/C", command});
    }

    /**
     * Creates an NTFS junction point if supported. Similar to symbolic links, NTFS provides junction points which
     * provide different features than symbolic links.
     * @param junction NTFS junction point to create
     * @param target target directory to junction
     * @return the newly created junction point
     * @throws IOException if the call to mklink exits with a non-zero status code
     * @throws InterruptedException if the call to mklink is interrupted before completing
     * @throws AssertionError if this method is called on a non-Windows platform
     */
    public static @Nonnull File createJunction(@Nonnull File junction, @Nonnull File target) throws IOException, InterruptedException {
        assertTrue(Functions.isWindows());
        Process mklink = execCmd("mklink", "/J", junction.getAbsolutePath(), target.getAbsolutePath());
        int result = mklink.waitFor();
        if (result != 0) {
            String stderr = IOUtils.toString(mklink.getErrorStream());
            String stdout = IOUtils.toString(mklink.getInputStream());
            throw new IOException("Process exited with " + result + "\nStandard Output:\n" + stdout + "\nError Output:\n" + stderr);
        }
        return junction;
    }
}
