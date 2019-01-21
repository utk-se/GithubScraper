package jenkins.slaves;

import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.Secret;

import hudson.Util;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.ResponseImpl;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves the JNLP file.
 *
 * The client can request an encrypted payload (with JNLP MAC code as the key) or if the client has a suitable permission,
 * it can request a plain text payload.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.560
 */
public class EncryptedSlaveAgentJnlpFile implements HttpResponse {

    private static final Logger LOG = Logger.getLogger(EncryptedSlaveAgentJnlpFile.class.getName());

    /**
     * The object that owns the Jelly view that renders JNLP file.
     * This is typically a {@link SlaveComputer} and if so we'll use {@link SlaveComputer#getJnlpMac()}
     * to determine the secret HMAC code.
     */
    private final AccessControlled it;
    /**
     * Name of the view that renders JNLP file that belongs to {@link #it}.
     */
    private final String viewName;
    /**
     * Name of the agent, which is used to determine secret HMAC code if {@link #it}
     * is not a {@link SlaveComputer}.
     */
    private final String slaveName;

    /**
     * Permission that allows plain text access. Checked against {@link #it}.
     */
    private final Permission connectPermission;

    public EncryptedSlaveAgentJnlpFile(AccessControlled it, String viewName, String slaveName, Permission connectPermission) {
        this.it = it;
        this.viewName = viewName;
        this.connectPermission = connectPermission;
        this.slaveName = slaveName;
    }

    @Override
    public void generateResponse(StaplerRequest req, final StaplerResponse res, Object node) throws IOException, ServletException {
        RequestDispatcher view = req.getView(it, viewName);
        if ("true".equals(req.getParameter("encrypt"))) {
            final CapturingServletOutputStream csos = new CapturingServletOutputStream();
            StaplerResponse temp = new ResponseImpl(req.getStapler(), new HttpServletResponseWrapper(res) {
                @Override public ServletOutputStream getOutputStream() throws IOException {
                    return csos;
                }
                @Override public PrintWriter getWriter() throws IOException {
                    throw new IllegalStateException();
                }
            });
            view.forward(req, temp);

            byte[] iv = new byte[128/8];
            new SecureRandom().nextBytes(iv);

            byte[] jnlpMac;
            if(it instanceof SlaveComputer) {
                jnlpMac = Util.fromHexString(((SlaveComputer)it).getJnlpMac());
            } else {
                jnlpMac = JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(slaveName.getBytes("UTF-8"));
            }
            SecretKey key = new SecretKeySpec(jnlpMac, 0, /* export restrictions */ 128 / 8, "AES");
            byte[] encrypted;
            try {
                Cipher c = Secret.getCipher("AES/CFB8/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
                encrypted = c.doFinal(csos.getBytes());
            } catch (GeneralSecurityException x) {
                throw new IOException(x);
            }
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(iv);
            res.getOutputStream().write(encrypted);
        } else {
            it.checkPermission(connectPermission);
            view.forward(req, res);
        }
    }


    /**
     * A {@link ServletOutputStream} that captures all the data rather than writing to a client.
     */
    private static class CapturingServletOutputStream extends ServletOutputStream { 

        private ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // we are always ready to write so we just call once to say we are ready.
            try {
                // should we do this on a separate thread to avoid deadlocks?
                writeListener.onWritePossible();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to notify WriteListener.onWritePossible", e);
            }
        }

        @Override
        public void write(int b) throws IOException {
            baos.write(b);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            baos.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            baos.write(b, off, len);
        }
        
        /** 
         * Get the data that has been written to this ServletOutputStream.
         * @return the data that has been written to this ServletOutputStream.
         */
        byte[] getBytes() {
            return baos.toByteArray();
        }
    }
}
