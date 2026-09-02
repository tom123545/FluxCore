package com.fluxcore.business.controller;

import com.fluxcore.business.service.BusinessApplicationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/applications")
public class InternalApplicationController {
    private final BusinessApplicationService applicationService;

    public InternalApplicationController(BusinessApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{applicationId}/submit")
    public void markSubmitted(@PathVariable long applicationId) {
        applicationService.markSubmitted(applicationId);
    }
}
