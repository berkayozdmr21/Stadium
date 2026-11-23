package com.perde.api;

import com.perde.application.port.RuntimeInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
class MetaController {

    record Meta(boolean demoCrowd) {
    }

    private final RuntimeInfo runtime;

    MetaController(RuntimeInfo runtime) {
        this.runtime = runtime;
    }

    @GetMapping
    Meta meta() {
        return new Meta(runtime.demoCrowdEnabled());
    }
}
