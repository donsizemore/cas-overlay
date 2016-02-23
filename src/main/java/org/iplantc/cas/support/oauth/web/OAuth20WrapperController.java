package org.iplantc.cas.support.oauth.web;

import org.apache.http.HttpStatus;
import org.iplantc.cas.support.oauth.OAuthConstants;
import org.iplantc.cas.support.oauth.OAuthUtils;
import org.jasig.cas.support.oauth.web.BaseOAuthWrapperController;
import org.jasig.cas.support.oauth.web.OAuth20ProfileController;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OAuth20WrapperController extends BaseOAuthWrapperController implements InitializingBean {
    private AbstractController authorizeController;

    private AbstractController callbackAuthorizeController;

    private AbstractController accessTokenController;

    private AbstractController profileController;

    @Override
    public void afterPropertiesSet() throws Exception {
        authorizeController = new OAuth20AuthorizeController(servicesManager, loginUrl);
        callbackAuthorizeController = new OAuth20CallbackAuthorizeController();
        accessTokenController = new OAuth20AccessTokenController(servicesManager, ticketRegistry, timeout);
        profileController = new OAuth20ProfileController(ticketRegistry);
    }

    @Override
    protected ModelAndView internalHandleRequest(final String method, final HttpServletRequest request,
                                                 final HttpServletResponse response) throws Exception {

        // authorize
        if (OAuthConstants.AUTHORIZE_URL.equals(method)) {
            return authorizeController.handleRequest(request, response);
        }
        // callback on authorize
        if (OAuthConstants.CALLBACK_AUTHORIZE_URL.equals(method)) {
            return callbackAuthorizeController.handleRequest(request, response);
        }
        //get access token
        if (OAuthConstants.ACCESS_TOKEN_URL.equals(method)) {
            return accessTokenController.handleRequest(request, response);
        }
        // get profile
        if (OAuthConstants.PROFILE_URL.equals(method)) {
            return profileController.handleRequest(request, response);
        }

        // else error
        logger.error("Unknown method : {}", method);
        OAuthUtils.writeTextError(response, OAuthConstants.INVALID_REQUEST, HttpStatus.SC_OK);
        return null;
    }
}
