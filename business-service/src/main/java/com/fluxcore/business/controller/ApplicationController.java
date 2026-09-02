package com.fluxcore.business.controller;

import com.fluxcore.business.dto.ApplicationResponse;
import com.fluxcore.business.dto.CreateContractApplicationRequest;
import com.fluxcore.business.dto.CreatePurchaseApplicationRequest;
import com.fluxcore.business.service.BusinessApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/applications")
public class ApplicationController {
    private final BusinessApplicationService applicationService;

    public ApplicationController(BusinessApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createPurchase(@Valid @RequestBody CreatePurchaseApplicationRequest request) {
        return applicationService.createPurchase(request);
    }

    @PostMapping("/contract")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createContract(@Valid @RequestBody CreateContractApplicationRequest request) {
        return applicationService.createContract(request);
    }
}
