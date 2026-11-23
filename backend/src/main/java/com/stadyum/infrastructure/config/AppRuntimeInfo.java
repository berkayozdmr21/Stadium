package com.stadyum.infrastructure.config;

import com.stadyum.application.port.RuntimeInfo;
import org.springframework.stereotype.Component;

@Component
class AppRuntimeInfo implements RuntimeInfo {

    private final AppProperties props;

    AppRuntimeInfo(AppProperties props) {
        this.props = props;
    }

    @Override
    public boolean demoCrowdEnabled() {
        return props.crowd().enabled();
    }
}
