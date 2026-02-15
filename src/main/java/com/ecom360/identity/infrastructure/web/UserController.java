package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.dto.UserProfileRequest;
import com.ecom360.identity.application.dto.UserProfileResponse;
import com.ecom360.identity.application.service.UserProfileService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/users")
@Tag(name = "Users", description = "User profile management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(userProfileService.get(p));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserProfileResponse> updateMe(@Valid @RequestBody UserProfileRequest req,
                                                        @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(userProfileService.update(req, p));
    }
}
