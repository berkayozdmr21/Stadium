package com.stadyum.infrastructure.web;

import com.stadyum.infrastructure.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "PUT", "POST", "DELETE")
                .allowedHeaders("Content-Type", "X-Hold-Token")
                .maxAge(3600);
    }

    /**
     * Arayüz backend ile aynı pakette yayınlandığında (Docker derlemesi) sayfa yolları
     * tek sayfa uygulamasının giriş dosyasına yönlendirilir.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/mac/{slug}").setViewName("forward:/index.html");
        registry.addViewController("/bilet/{code}").setViewName("forward:/index.html");
    }
}
