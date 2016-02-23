package org.iplantc.cas.support.oauth.web;

import org.apache.commons.lang.StringUtils;
import org.iplantc.cas.support.oauth.OAuthConstants;
import org.iplantc.cas.support.oauth.OAuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

public final class OAuth20CallbackAuthorizeController extends AbstractController {

    private final Logger logger = LoggerFactory.getLogger(OAuth20CallbackAuthorizeController.class);

    @Override
    protected ModelAndView handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        // get CAS ticket
        final String ticket = request.getParameter(OAuthConstants.TICKET);
        logger.debug("{} : {}", OAuthConstants.TICKET, ticket);

        // retrieve callback url from session
        final HttpSession session = request.getSession();
        String callbackUrl = (String) session.getAttribute(OAuthConstants.OAUTH20_CALLBACKURL);
        logger.debug("{} : {}", OAuthConstants.OAUTH20_CALLBACKURL, callbackUrl);
        session.removeAttribute(OAuthConstants.OAUTH20_CALLBACKURL);

        if (StringUtils.isBlank(callbackUrl)) {
            logger.error("{} is missing from the session and can not be retrieved.", OAuthConstants.OAUTH20_CALLBACKURL);
            return new ModelAndView(OAuthConstants.ERROR_VIEW);
        }
        // and state
        final String state = (String) session.getAttribute(OAuthConstants.OAUTH20_STATE);
        logger.debug("{} : {}", OAuthConstants.OAUTH20_STATE, state);
        session.removeAttribute(OAuthConstants.OAUTH20_STATE);

        // return callback url with code & state
        callbackUrl = OAuthUtils.addParameter(callbackUrl, OAuthConstants.CODE, ticket);
        if (state != null) {
            callbackUrl = OAuthUtils.addParameter(callbackUrl, OAuthConstants.STATE, state);
        }
        logger.debug("{} : {}", OAuthConstants.OAUTH20_CALLBACKURL, callbackUrl);

        final Map<String, Object> model = new HashMap<String, Object>();
        model.put("callbackUrl", callbackUrl);

        final Boolean bypassApprovalPrompt = (Boolean) session.getAttribute(OAuthConstants.BYPASS_APPROVAL_PROMPT);
        logger.debug("bypassApprovalPrompt: {}", bypassApprovalPrompt);
        session.removeAttribute(OAuthConstants.BYPASS_APPROVAL_PROMPT);

        // Clients that auto-approve do not need authorization.
        if (bypassApprovalPrompt != null && bypassApprovalPrompt) {
            logger.debug("Redirect to callback based on bypassApprovalPrompt");
            return OAuthUtils.redirectTo(callbackUrl);
        }

        // retrieve service name from session
        final String serviceName = (String) session.getAttribute(OAuthConstants.OAUTH20_SERVICE_NAME);
        logger.debug("serviceName : {}", serviceName);
        model.put("serviceName", serviceName);

        return new ModelAndView(OAuthConstants.CONFIRM_VIEW, model);
    }
}
