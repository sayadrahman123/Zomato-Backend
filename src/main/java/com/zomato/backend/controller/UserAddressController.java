package com.zomato.backend.controller;

import com.zomato.backend.dto.request.SaveAddressRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.UserAddressResponse;
import com.zomato.backend.service.UserAddressService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Saved delivery address endpoints for customers.
 *
 * Base path: /api/addresses
 * Access: CUSTOMER only (restaurant owners don't have delivery addresses).
 *
 * The userId is always extracted from the JWT — clients never supply it.
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Addresses", description = "Manage saved delivery addresses — add, update, set default, delete")
public class UserAddressController {

    private final UserAddressService addressService;
    private final AuthUtils          authUtils;

    @Operation(
        summary     = "Get all my saved addresses",
        description = "Returns the customer's saved delivery addresses — default first, then newest first."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getMyAddresses(
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        List<UserAddressResponse> addresses = addressService.getMyAddresses(userId);
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully", addresses));
    }

    @Operation(
        summary     = "Get my default address",
        description = "Returns the address marked as default. Returns null data if no address is saved yet."
    )
    @GetMapping("/default")
    public ResponseEntity<ApiResponse<UserAddressResponse>> getDefaultAddress(
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        UserAddressResponse address = addressService.getDefaultAddress(userId);
        String msg = address != null ? "Default address fetched" : "No default address set";
        return ResponseEntity.ok(ApiResponse.success(msg, address));
    }

    @Operation(
        summary     = "Add a new address",
        description = "Saves a new delivery address. Max 10 per customer. " +
                      "The first address added automatically becomes the default."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<UserAddressResponse>> addAddress(
            @Valid @RequestBody SaveAddressRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        UserAddressResponse address = addressService.addAddress(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address saved successfully", address));
    }

    @Operation(
        summary     = "Update an address",
        description = "Replaces all fields of an existing saved address. " +
                      "Set isDefault=true to also make it the new default."
    )
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody SaveAddressRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        UserAddressResponse address = addressService.updateAddress(addressId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", address));
    }

    @Operation(
        summary     = "Set an address as default",
        description = "Marks the specified address as the customer's default for checkout. " +
                      "Clears the default flag from all other addresses automatically."
    )
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<UserAddressResponse>> setDefault(
            @PathVariable Long addressId,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        UserAddressResponse address = addressService.setDefault(addressId, userId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated", address));
    }

    @Operation(
        summary     = "Delete an address",
        description = "Permanently removes a saved address. " +
                      "If the deleted address was the default, the next most recent address is promoted."
    )
    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long addressId,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        addressService.deleteAddress(addressId, userId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully"));
    }
}
