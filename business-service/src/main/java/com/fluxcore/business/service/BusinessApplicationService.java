package com.fluxcore.business.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fluxcore.business.dto.ApplicationResponse;
import com.fluxcore.business.dto.BusinessDataResponse;
import com.fluxcore.business.dto.ContractChangeItemRequest;
import com.fluxcore.business.dto.CreateContractApplicationRequest;
import com.fluxcore.business.dto.CreatePurchaseApplicationRequest;
import com.fluxcore.business.dto.PurchaseItemRequest;
import com.fluxcore.business.entity.ApplicationEntity;
import com.fluxcore.business.entity.ApplicationExtEntity;
import com.fluxcore.business.entity.ContractChangeItemEntity;
import com.fluxcore.business.entity.ContractChangeRequestEntity;
import com.fluxcore.business.entity.ProcurementItemEntity;
import com.fluxcore.business.entity.ProcurementRequestEntity;
import com.fluxcore.business.mapper.ApplicationExtMapper;
import com.fluxcore.business.mapper.ApplicationMapper;
import com.fluxcore.business.mapper.ContractChangeItemMapper;
import com.fluxcore.business.mapper.ContractChangeRequestMapper;
import com.fluxcore.business.mapper.ProcurementItemMapper;
import com.fluxcore.business.mapper.ProcurementRequestMapper;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessApplicationService {
    public static final String PURCHASE = "PURCHASE";
    public static final String CONTRACT_CHANGE = "CONTRACT_CHANGE";
    private static final String DRAFT = "DRAFT";

    private final ObjectMapper objectMapper;
    private final ApplicationMapper applicationMapper;
    private final ApplicationExtMapper applicationExtMapper;
    private final ProcurementRequestMapper procurementRequestMapper;
    private final ProcurementItemMapper procurementItemMapper;
    private final ContractChangeRequestMapper contractChangeRequestMapper;
    private final ContractChangeItemMapper contractChangeItemMapper;

    public BusinessApplicationService(ObjectMapper objectMapper, ApplicationMapper applicationMapper,
                                      ApplicationExtMapper applicationExtMapper,
                                      ProcurementRequestMapper procurementRequestMapper,
                                      ProcurementItemMapper procurementItemMapper,
                                      ContractChangeRequestMapper contractChangeRequestMapper,
                                      ContractChangeItemMapper contractChangeItemMapper) {
        this.objectMapper = objectMapper;
        this.applicationMapper = applicationMapper;
        this.applicationExtMapper = applicationExtMapper;
        this.procurementRequestMapper = procurementRequestMapper;
        this.procurementItemMapper = procurementItemMapper;
        this.contractChangeRequestMapper = contractChangeRequestMapper;
        this.contractChangeItemMapper = contractChangeItemMapper;
    }

    @Transactional
    public ApplicationResponse createPurchase(CreatePurchaseApplicationRequest request) {
        ApplicationEntity existing = applicationMapper.selectByIdempotencyKey(PURCHASE, request.getIdempotencyKey());
        if (existing != null) return toResponse(existing);
        String businessId = nextBusinessId("PUR");
        ApplicationEntity application = newApplication(PURCHASE, businessId, request.getTitle(), request.getApplicantId(), request.getIdempotencyKey());
        long applicationId = insertApplication(application);
        ProcurementRequestEntity procurement = new ProcurementRequestEntity();
        procurement.setApplicationId(applicationId); procurement.setRequestNo(businessId); procurement.setApplicantId(request.getApplicantId());
        procurement.setDepartmentCode(request.getDepartmentCode()); procurement.setTotalAmount(request.getTotalAmount());
        procurement.setCurrency(request.getCurrency()); procurement.setStatus(DRAFT);
        procurementRequestMapper.insert(procurement);
        for (PurchaseItemRequest item : request.getItems()) {
            ProcurementItemEntity entity = new ProcurementItemEntity(); entity.setProcurementId(procurement.getId());
            entity.setItemName(item.getItemName()); entity.setQuantity(item.getQuantity()); entity.setUnitPrice(item.getUnitPrice());
            entity.setAmount(item.getQuantity().multiply(item.getUnitPrice())); procurementItemMapper.insert(entity);
        }
        insertExtension(applicationId, request); application.setId(applicationId); return toResponse(application);
    }

    @Transactional
    public ApplicationResponse createContract(CreateContractApplicationRequest request) {
        ApplicationEntity existing = applicationMapper.selectByIdempotencyKey(CONTRACT_CHANGE, request.getIdempotencyKey());
        if (existing != null) return toResponse(existing);
        String businessId = nextBusinessId("CCHG");
        ApplicationEntity application = newApplication(CONTRACT_CHANGE, businessId, request.getTitle(), request.getApplicantId(), request.getIdempotencyKey());
        long applicationId = insertApplication(application);
        ContractChangeRequestEntity contract = new ContractChangeRequestEntity(); contract.setApplicationId(applicationId);
        contract.setChangeNo(businessId); contract.setContractNo(request.getContractNo()); contract.setApplicantId(request.getApplicantId());
        contract.setChangeReason(request.getChangeReason()); contract.setChangeAmount(request.getChangeAmount()); contract.setCurrency(request.getCurrency());
        contract.setStatus(DRAFT); contractChangeRequestMapper.insert(contract);
        if (request.getItems() != null) for (ContractChangeItemRequest item : request.getItems()) {
            ContractChangeItemEntity entity = new ContractChangeItemEntity(); entity.setContractChangeId(contract.getId());
            entity.setFieldName(item.getFieldName()); entity.setOldValue(item.getOldValue()); entity.setNewValue(item.getNewValue());
            contractChangeItemMapper.insert(entity);
        }
        insertExtension(applicationId, request); application.setId(applicationId); return toResponse(application);
    }

    @Transactional(readOnly = true)
    public BusinessDataResponse getBusinessData(String businessType, String businessId) {
        ApplicationEntity application = Optional.ofNullable(applicationMapper.selectByBusiness(businessType, businessId))
                .orElseThrow(() -> new IllegalArgumentException("业务申请不存在: " + businessType + "/" + businessId));
        ObjectNode data = objectMapper.createObjectNode(); data.put("title", application.getTitle()); data.put("applicantId", application.getApplicantId());
        data.put("businessType", businessType); data.put("businessId", businessId);
        if (PURCHASE.equals(businessType)) {
            ProcurementRequestEntity request = procurementRequestMapper.selectByApplicationId(application.getId());
            data.put("departmentCode", request.getDepartmentCode()); data.put("totalAmount", request.getTotalAmount()); data.put("currency", request.getCurrency());
            var items = data.putArray("items"); procurementItemMapper.selectByProcurementId(request.getId()).forEach(item -> {
                ObjectNode node = items.addObject(); node.put("itemName", item.getItemName()); node.put("quantity", item.getQuantity()); node.put("unitPrice", item.getUnitPrice()); node.put("amount", item.getAmount());
            });
        } else if (CONTRACT_CHANGE.equals(businessType)) {
            ContractChangeRequestEntity request = contractChangeRequestMapper.selectByApplicationId(application.getId());
            data.put("contractNo", request.getContractNo()); data.put("changeReason", request.getChangeReason()); data.put("changeAmount", request.getChangeAmount()); data.put("currency", request.getCurrency());
            var items = data.putArray("items"); contractChangeItemMapper.selectByContractChangeId(request.getId()).forEach(item -> {
                ObjectNode node = items.addObject(); node.put("fieldName", item.getFieldName()); node.put("oldValue", item.getOldValue()); node.put("newValue", item.getNewValue());
            });
        } else throw new IllegalArgumentException("不支持的业务类型: " + businessType);
        return new BusinessDataResponse(application.getId(), application.getApplicationNo(), businessType, businessId, application.getTitle(), application.getApplicantId(), application.getStatus(), data);
    }

    @Transactional
    public void markSubmitted(long applicationId) {
        ApplicationEntity application = Optional.ofNullable(applicationMapper.selectById(applicationId)).orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        if (DRAFT.equals(application.getStatus())) {
            if (applicationMapper.markSubmitted(applicationId) != 1) throw new IllegalStateException("申请提交状态更新失败");
            if (PURCHASE.equals(application.getBusinessType())) {
                if (procurementRequestMapper.markSubmitted(applicationId) != 1) throw new IllegalStateException("采购申请提交状态更新失败");
            } else if (CONTRACT_CHANGE.equals(application.getBusinessType())) {
                if (contractChangeRequestMapper.markSubmitted(applicationId) != 1) throw new IllegalStateException("合同变更申请提交状态更新失败");
            }
        }
        else if (!"SUBMITTED".equals(application.getStatus())) throw new IllegalStateException("只有草稿申请可以提交");
    }

    @Transactional
    public void markWithdrawn(long applicationId) {
        ApplicationEntity application = Optional.ofNullable(applicationMapper.selectById(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        if ("SUBMITTED".equals(application.getStatus())) {
            if (applicationMapper.markWithdrawn(applicationId) != 1) {
                throw new IllegalStateException("申请撤回状态更新失败");
            }
            if (PURCHASE.equals(application.getBusinessType())) {
                if (procurementRequestMapper.markWithdrawn(applicationId) != 1) {
                    throw new IllegalStateException("采购申请撤回状态更新失败");
                }
            } else if (CONTRACT_CHANGE.equals(application.getBusinessType())) {
                if (contractChangeRequestMapper.markWithdrawn(applicationId) != 1) {
                    throw new IllegalStateException("合同变更申请撤回状态更新失败");
                }
            }
        } else if (!"WITHDRAWN".equals(application.getStatus())) {
            throw new IllegalStateException("只有已提交申请可以撤回");
        }
    }

    @Transactional
    public void markRejected(long applicationId) {
        ApplicationEntity application = Optional.ofNullable(applicationMapper.selectById(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        if ("SUBMITTED".equals(application.getStatus())) {
            if (applicationMapper.markRejected(applicationId) != 1) throw new IllegalStateException("申请驳回状态更新失败");
        } else if (!"REJECTED".equals(application.getStatus())) {
            throw new IllegalStateException("只有已提交申请可以驳回");
        }
        if (PURCHASE.equals(application.getBusinessType())) {
            if (procurementRequestMapper.markRejected(applicationId) != 1
                    && procurementRequestMapper.selectByApplicationId(applicationId) == null) {
                throw new IllegalStateException("采购申请驳回状态更新失败");
            }
        } else if (CONTRACT_CHANGE.equals(application.getBusinessType())) {
            if (contractChangeRequestMapper.markRejected(applicationId) != 1
                    && contractChangeRequestMapper.selectByApplicationId(applicationId) == null) {
                throw new IllegalStateException("合同变更申请驳回状态更新失败");
            }
        }
    }

    @Transactional
    public void markApproved(long applicationId) {
        ApplicationEntity application = Optional.ofNullable(applicationMapper.selectById(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("申请不存在: " + applicationId));
        if ("SUBMITTED".equals(application.getStatus())) {
            if (applicationMapper.markApproved(applicationId) != 1) {
                throw new IllegalStateException("申请通过状态更新失败");
            }
        } else if (!"APPROVED".equals(application.getStatus())) {
            throw new IllegalStateException("只有已提交申请可以通过");
        }
        if (PURCHASE.equals(application.getBusinessType())) {
            procurementRequestMapper.markApproved(applicationId);
        } else if (CONTRACT_CHANGE.equals(application.getBusinessType())) {
            contractChangeRequestMapper.markApproved(applicationId);
        }
    }

    private long insertApplication(ApplicationEntity application) {
        try { applicationMapper.insert(application); return application.getId(); }
        catch (DuplicateKeyException exception) {
            ApplicationEntity existing = applicationMapper.selectByIdempotencyKey(application.getBusinessType(), application.getIdempotencyKey());
            if (existing != null) return existing.getId(); throw exception;
        }
    }
    private ApplicationEntity newApplication(String type, String businessId, String title, String applicantId, String idempotencyKey) {
        ApplicationEntity entity = new ApplicationEntity(); entity.setApplicationNo("APP-" + shortUuid()); entity.setBusinessType(type); entity.setBusinessId(businessId);
        entity.setTitle(title); entity.setApplicantId(applicantId); entity.setIdempotencyKey(idempotencyKey); entity.setStatus(DRAFT); entity.setVersion(0L); return entity;
    }
    private String nextBusinessId(String prefix) { return prefix + "-" + shortUuid(); }
    private String shortUuid() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }
    private void insertExtension(long applicationId, Object request) {
        try { ApplicationExtEntity entity = new ApplicationExtEntity(); entity.setApplicationId(applicationId); entity.setFormData(objectMapper.writeValueAsString(request)); applicationExtMapper.insert(entity); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("申请扩展数据序列化失败", exception); }
    }
    private ApplicationResponse toResponse(ApplicationEntity e) { return new ApplicationResponse(e.getId(), e.getApplicationNo(), e.getBusinessType(), e.getBusinessId(), e.getTitle(), e.getApplicantId(), e.getStatus()); }
}
