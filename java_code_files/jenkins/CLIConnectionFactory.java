package hudson.cli;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;

/**
 * Fluent-API to instantiate {@link CLI}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CLIConnectionFactory {
    URL jenkins;
    ExecutorService exec;
    String httpsProxyTunnel;
    String authorization;

    /**
     * Top URL of the Jenkins to connect to.
     */
    public CLIConnectionFactory url(URL jenkins) {
        this.jenkins = jenkins;
        return this;
    }

    public CLIConnectionFactory url(String jenkins) throws MalformedURLException {
        return url(new URL(jenkins));
    }
    
    /**
     * This {@link ExecutorService} is used to execute closures received from the server.
     * Used only in Remoting mode.
     */
    public CLIConnectionFactory executorService(ExecutorService es) {
        this.exec = es;
        return this;
    }

    /**
     * Configures the HTTP proxy that we use for making a plain TCP/IP connection.
     * "host:port" that points to an HTTP proxy or null.
     */
    public CLIConnectionFactory httpsProxyTunnel(String value) {
        this.httpsProxyTunnel = value;
        return this;
    }

    /**
     * For CLI connection that goes through HTTP, sometimes you need
     * to pass in the custom authentication header (before Jenkins even get to authenticate
     * the CLI channel.) This method lets you specify the value of this header.
     */
    public CLIConnectionFactory authorization(String value) {
        this.authorization = value;
        return this;
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Currently unused.
     */
    public CLIConnectionFactory basicAuth(String username, String password) {
        return basicAuth(username+':'+password);
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Cf. {@code BasicHeaderApiTokenAuthenticator}.
     */
    public CLIConnectionFactory basicAuth(String userInfo) {
        return authorization("Basic " + new String(Base64.encodeBase64((userInfo).getBytes())));
    }

    /**
     * @deprecated Specific to Remoting-based protocol.
     */
    @Deprecated
    public CLI connect() throws IOException, InterruptedException {
        return new CLI(this);
    }
}
