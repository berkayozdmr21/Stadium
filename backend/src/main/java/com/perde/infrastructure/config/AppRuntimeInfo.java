package com.perde.infrastructure.config;

import com.perde.application.port.RuntimeInfo;
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
