package com.tyndalehouse.step.guice;

import com.tyndalehouse.step.core.service.AppManagerService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Map;

/**
 * Intercepts and works out whether STEP has finished the installation process
 */
@Singleton
public class SetupRedirectFilter implements Filter {
    //sourced from step.core.properties, identifies the version of the currently running application
    private String runningAppVersion;
    private AppManagerService appManager;

    /**
     * @param runningAppVersion the version of the running application
     */
    @Inject
    public SetupRedirectFilter(@Named(AppManagerService.APP_VERSION) final String runningAppVersion,
                               AppManagerService appManager) {
        this.runningAppVersion = runningAppVersion;
        this.appManager = appManager;
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        String installedVersion = appManager.getAppVersion();
        //server installations always going forward
        Map<String, String[]> inputParms = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : inputParms.entrySet()) {
            String key = entry.getKey();
            String[] value = entry.getValue();
            for (int i = 0; i < value.length; i++) {
                String checkValue = value[i].toLowerCase();
                if (checkValue.contains("script")) {
                    checkValue = checkValue.replaceAll("\\s+", "");
                    if (checkValue.contains("<script>") || checkValue.contains("</script>")) {
                        System.out.println("XSS attack detected: " + key + "=" + value[i] + " uri: " + ((HttpServletRequestWrapper) request).getRequestURI());
                        return;
                    }
                }
                if (checkValue.contains("<") || checkValue.contains(">") || checkValue.contains("%3c") || checkValue.contains("%3e") ||
                        checkValue.contains("&lt") || checkValue.contains("&gt") ||
                        checkValue.contains("#6") || checkValue.contains("#0") || checkValue.contains("#x") || checkValue.contains("\u003c"))
                    System.out.println("XSS check: " + key + "=" + value[i] + " uri: " + ((HttpServletRequestWrapper) request).getRequestURI());
            }
        }
        if (!appManager.isLocal() || (installedVersion != null && installedVersion.equals(runningAppVersion))) {
            // do nothing
        } else {
            //set the version up one - installer will have taken care of upgrades... hopefully.
            appManager.setAndSaveAppVersion(runningAppVersion);
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing to destroy
    }
}
