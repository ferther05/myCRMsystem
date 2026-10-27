package com.bishe.crm.module.customer.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.service.CustomerService;
import com.bishe.crm.module.customer.dto.*;
import com.bishe.crm.module.customer.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/customers") @RequiredArgsConstructor @Validated
public class CustomerReadController {
    private final CustomerService customers;

    @GetMapping
    public ApiResponse<PageResult<CustomerSummaryVO>> list(@AuthenticationPrincipal AuthPrincipal actor,
            @Valid CustomerQuery query) {
        return ApiResponse.success(customers.list(actor, query));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerDetailVO> detail(@AuthenticationPrincipal AuthPrincipal actor,
                                                @Positive @PathVariable Long id) {
        return ApiResponse.success(customers.detail(actor, id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Long> create(@AuthenticationPrincipal AuthPrincipal actor,@Valid @RequestBody CustomerRequests.Create request) {
        return ApiResponse.success(customers.create(actor,request));
    }
    @PutMapping("/{id}")
    public ApiResponse<Void> edit(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                  @Valid @RequestBody CustomerRequests.Edit request) {
        customers.edit(actor,id,request); return ApiResponse.success(null);
    }
    @PatchMapping("/{id}/classification")
    public ApiResponse<Void> classify(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                     @Valid @RequestBody CustomerRequests.Classification request) {
        customers.classify(actor,id,request); return ApiResponse.success(null);
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                    @RequestParam @jakarta.validation.constraints.PositiveOrZero long version) {
        customers.delete(actor,id,version); return ApiResponse.success(null);
    }
    @GetMapping("/{id}/contacts")
    public ApiResponse<java.util.List<ContactVO>> contacts(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id) {
        return ApiResponse.success(customers.detail(actor,id).getContacts());
    }
    @PostMapping("/{id}/contacts")
    public ApiResponse<Long> addContact(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                       @Valid @RequestBody CustomerRequests.ContactWrite request) {
        return ApiResponse.success(customers.addContact(actor,id,request));
    }
    @PutMapping("/{id}/contacts/{contactId}")
    public ApiResponse<Void> editContact(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
            @Positive @PathVariable Long contactId,@Valid @RequestBody CustomerRequests.ContactWrite request) {
        customers.editContact(actor,id,contactId,request); return ApiResponse.success(null);
    }
    @DeleteMapping("/{id}/contacts/{contactId}")
    public ApiResponse<Void> deleteContact(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
            @Positive @PathVariable Long contactId,@RequestParam @jakarta.validation.constraints.PositiveOrZero long customerVersion) {
        customers.deleteContact(actor,id,contactId,customerVersion); return ApiResponse.success(null);
    }
    @PostMapping("/{id}/follow-records")
    public ApiResponse<Long> addFollow(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                       @Valid @RequestBody CustomerRequests.FollowWrite request) {
        return ApiResponse.success(customers.addFollow(actor,id,request));
    }
    @PatchMapping("/{id}/follow-records/{followId}/review")
    public ApiResponse<Void> reviewFollow(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
            @Positive @PathVariable Long followId,@Valid @RequestBody CustomerRequests.FollowReview request) {
        customers.reviewFollow(actor,id,followId,request); return ApiResponse.success(null);
    }
    @PostMapping("/{id}/claim")
    public ApiResponse<Void> claim(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                   @RequestParam @jakarta.validation.constraints.PositiveOrZero long version) {
        customers.claim(actor,id,version); return ApiResponse.success(null);
    }
    @PatchMapping("/{id}/ownership")
    public ApiResponse<Void> transfer(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                      @Valid @RequestBody CustomerRequests.OwnershipTransfer request) {
        customers.transfer(actor,id,request); return ApiResponse.success(null);
    }
}
