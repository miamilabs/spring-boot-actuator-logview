package eu.hinsch.spring.boot.actuator.logview;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Arrays.asList;

@Component
@ConfigurationProperties(prefix = "endpoints.logview")
class EndpointConfiguration {
    private List<String> stylesheets = asList("https://maxcdn.bootstrapcdn.com/bootstrap/3.3.2/css/bootstrap.min.css",
            "https://maxcdn.bootstrapcdn.com/font-awesome/4.3.0/css/font-awesome.min.css");
    private String path;

    public List<String> getStylesheets() {
        return stylesheets;
    }

    public void setStylesheets(List<String> stylesheets) {
        this.stylesheets = stylesheets;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
