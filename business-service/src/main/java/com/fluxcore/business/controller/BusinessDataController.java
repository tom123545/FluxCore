package com.fluxcore.business.controller;

import com.fluxcore.business.dto.BusinessDataResponse;
import com.fluxcore.business.service.BusinessApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/business-data")
public class BusinessDataController {
    private final BusinessApplicationService applicationService;

    public BusinessDataController(BusinessApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{businessType}/{businessId}")
    public BusinessDataResponse getBusinessData(@PathVariable String businessType,
                                                @PathVariable String businessId) {
        return applicationService.getBusinessData(businessType, businessId);
    }
}
