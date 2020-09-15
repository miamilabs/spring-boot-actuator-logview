package eu.hinsch.spring.boot.actuator.logview;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.File;

@ManagementContextConfiguration
public class LogViewEndpointAutoconfig {

    public static final String LOGGING_FILE = "logging.file";
    public static final String LOGGING_PATH = "logging.file.path";
    public static final String ENDPOINTS_LOGVIEW_PATH = "endpoints.logview.path";

    @ConditionalOnProperty(LOGGING_FILE)
    @ConditionalOnMissingBean(LogViewEndpoint.class)
    @Bean
    public LogViewEndpoint logViewEndpointWithDefaultFile(Environment environment, EndpointConfiguration configuration) {
        String logDirectory = new File(environment.getRequiredProperty(LOGGING_FILE)).getParentFile().getAbsolutePath();
        return new LogViewEndpoint(logDirectory, configuration.getStylesheets());
    }

    @ConditionalOnProperty(LOGGING_PATH)
    @ConditionalOnMissingBean(LogViewEndpoint.class)
    @Bean
    public LogViewEndpoint logViewEndpointWithDefaultPath(Environment environment, EndpointConfiguration configuration) {
        return new LogViewEndpoint(environment.getRequiredProperty(LOGGING_PATH), configuration.getStylesheets());
    }

    @ConditionalOnProperty(ENDPOINTS_LOGVIEW_PATH)
    @ConditionalOnMissingBean(LogViewEndpoint.class)
    @Bean
    public LogViewEndpoint logViewEndpointWithDeviatingPath(Environment environment, EndpointConfiguration configuration) {
        return new LogViewEndpoint(configuration.getPath(), configuration.getStylesheets());
    }
}
