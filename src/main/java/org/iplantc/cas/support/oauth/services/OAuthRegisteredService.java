package org.iplantc.cas.support.oauth.services;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.jasig.cas.services.RegexRegisteredService;

public final class OAuthRegisteredService extends RegexRegisteredService {

    private static final long serialVersionUID = 6784839055053605375L;

    private String clientSecret;
    private String clientId;
    private Boolean bypassApprovalPrompt = false;

    public String getClientId() {
        return this.clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return this.clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Boolean isBypassApprovalPrompt() {
        return this.bypassApprovalPrompt;
    }

    public void setBypassApprovalPrompt(final Boolean bypassApprovalPrompt) {
        this.bypassApprovalPrompt = bypassApprovalPrompt;
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder(this);
        builder.appendSuper(super.toString());
        builder.append("clientId", getClientId());
        builder.append("approvalPrompt", isBypassApprovalPrompt());
        return builder.toString();
    }
}
