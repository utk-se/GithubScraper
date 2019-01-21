package jadx.gui.treemodel;

import com.android.apksig.ApkVerifier;
import jadx.api.ResourceType;
import jadx.gui.JadxWrapper;
import jadx.gui.utils.CertificateManager;
import jadx.gui.utils.NLS;
import jadx.gui.utils.Utils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;
import java.security.cert.Certificate;
import java.util.List;
import java.util.stream.Collectors;

public class ApkSignature extends JNode {

	private static final Logger log = LoggerFactory.getLogger(ApkSignature.class);
	private static final ImageIcon CERTIFICATE_ICON = Utils.openIcon("certificate_obj");

	private final transient File openFile;
	private String content = null;

	public static ApkSignature getApkSignature(JadxWrapper wrapper) {
		// Only show the ApkSignature node if an AndroidManifest.xml is present.
		// Without a manifest the Google ApkVerifier refuses to work.
		if (!wrapper.getResources().stream().anyMatch(r -> "AndroidManifest.xml".equals(r.getName()))) {
			return null;
		}
		File openFile = wrapper.getOpenFile();
		return new ApkSignature(openFile);
	}

	public ApkSignature(File openFile) {
		this.openFile = openFile;
	}

	@Override
	public JClass getJParent() {
		return null;
	}

	@Override
	public Icon getIcon() {
		return CERTIFICATE_ICON;
	}

	@Override
	public String makeString() {
		return "APK signature";
	}

	@Override
	public String getContent() {
		if (content != null)
			return this.content;
		ApkVerifier verifier = new ApkVerifier.Builder(openFile).build();
		try {
			ApkVerifier.Result result = verifier.verify();
			StringEscapeUtils.Builder builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4);
			builder.append("<h1>APK signature verification result:</h1>");

			builder.append("<p><b>");
			if (result.isVerified()) {
				builder.escape(NLS.str("apkSignature.verificationSuccess"));
			} else {
				builder.escape(NLS.str("apkSignature.verificationFailed"));
			}
			builder.append("</b></p>");

			final String err = NLS.str("apkSignature.errors");
			final String warn = NLS.str("apkSignature.warnings");
			final String sigSucc = NLS.str("apkSignature.signatureSuccess");
			final String sigFail = NLS.str("apkSignature.signatureFailed");

			writeIssues(builder, err, result.getErrors());
			writeIssues(builder, warn, result.getWarnings());

			if (result.getV1SchemeSigners().size() > 0) {
				builder.append("<h2>");
				builder.escape(String.format(result.isVerifiedUsingV1Scheme() ? sigSucc : sigFail, 1));
				builder.append("</h2>\n");

				builder.append("<blockquote>");
				for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
					builder.append("<h3>");
					builder.escape(NLS.str("apkSignature.signer"));
					builder.append(" ");
					builder.escape(signer.getName());
					builder.append(" (");
					builder.escape(signer.getSignatureFileName());
					builder.append(")");
					builder.append("</h3>");
					writeCertificate(builder, signer.getCertificate());
					writeIssues(builder, err, signer.getErrors());
					writeIssues(builder, warn, signer.getWarnings());
				}
				builder.append("</blockquote>");
			}
			if (result.getV2SchemeSigners().size() > 0) {
				builder.append("<h2>");
				builder.escape(String.format(result.isVerifiedUsingV2Scheme() ? sigSucc : sigFail, 2));
				builder.append("</h2>\n");

				builder.append("<blockquote>");
				for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
					builder.append("<h3>");
					builder.escape(NLS.str("apkSignature.signer"));
					builder.append(" ");
					builder.append(Integer.toString(signer.getIndex() + 1));
					builder.append("</h3>");
					writeCertificate(builder, signer.getCertificate());
					writeIssues(builder, err, signer.getErrors());
					writeIssues(builder, warn, signer.getWarnings());
				}
				builder.append("</blockquote>");
			}
			this.content = builder.toString();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			StringEscapeUtils.Builder builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4);
			builder.append("<h1>");
			builder.escape(NLS.str("apkSignature.exception"));
			builder.append("</h1><pre>");
			builder.escape(ExceptionUtils.getStackTrace(e));
			builder.append("</pre>");
			return builder.toString();
		}
		return this.content;
	}

	private void writeCertificate(StringEscapeUtils.Builder builder, Certificate cert) {
		CertificateManager certMgr = new CertificateManager(cert);
		builder.append("<blockquote><pre>");
		builder.escape(certMgr.generateHeader());
		builder.append("</pre><pre>");
		builder.escape(certMgr.generatePublicKey());
		builder.append("</pre><pre>");
		builder.escape(certMgr.generateSignature());
		builder.append("</pre><pre>");
		builder.append(certMgr.generateFingerprint());
		builder.append("</pre></blockquote>");
	}

	private void writeIssues(StringEscapeUtils.Builder builder, String issueType, List<ApkVerifier.IssueWithParams> issueList) {
		if (issueList.size() > 0) {
			builder.append("<h3>");
			builder.escape(issueType);
			builder.append("</h3>");
			builder.append("<blockquote>");
			// Unprotected Zip entry issues are very common, handle them separately
			List<ApkVerifier.IssueWithParams> unprotIssues = issueList.stream().filter(i ->
					i.getIssue() == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY).collect(Collectors.toList());
			if (unprotIssues.size() > 0) {
				builder.append("<h4>");
				builder.escape(NLS.str("apkSignature.unprotectedEntry"));
				builder.append("</h4><blockquote>");
				for (ApkVerifier.IssueWithParams issue : unprotIssues) {
					builder.escape((String) issue.getParams()[0]);
					builder.append("<br>");
				}
				builder.append("</blockquote>");
			}
			List<ApkVerifier.IssueWithParams> remainingIssues = issueList.stream().filter(i ->
					i.getIssue() != ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY).collect(Collectors.toList());
			if (remainingIssues.size() > 0) {
				builder.append("<pre>\n");
				for (ApkVerifier.IssueWithParams issue : remainingIssues) {
					builder.escape(issue.toString());
					builder.append("\n");
				}
				builder.append("</pre>\n");
			}
			builder.append("</blockquote>");
		}

	}


}
