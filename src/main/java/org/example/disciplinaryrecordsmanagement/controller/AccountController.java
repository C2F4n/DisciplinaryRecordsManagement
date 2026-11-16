package org.example.disciplinaryrecordsmanagement.controller;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.*;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.RegistrationEmailService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.*;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.REGISTRATION_CODE_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_USERNAME_LENGTH;

@RestController
@RequestMapping(ROOT)
public class AccountController {
    private final AccountService accounts;
    private final RegistrationEmailService registrationEmails;

    AccountController(AccountService accounts, RegistrationEmailService registrationEmails) {
        this.accounts = accounts;
        this.registrationEmails = registrationEmails;
    }

    @GetMapping(CSRF)
    ResponseEntity<Result<Csrf>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(new Csrf(token.getHeaderName(), token.getToken())));
    }

    @GetMapping("/me")
    Result<CurrentUser> me(Principal principal) {
        return Result.success(accounts.currentUser(principal));
    }

    @PutMapping(PASSWORD)
    Result<Void> changePassword(Principal principal, @Valid @RequestBody PasswordChange input,
                                HttpServletRequest request, HttpServletResponse response) {
        accounts.changePassword(principal, input);
        new SecurityContextLogoutHandler().logout(request, response, null);
        return Result.success();
    }

    @GetMapping(BINDING_CLASSES)
    Result<List<PublicClass>> bindingClasses() {
        return Result.success(accounts.bindingClasses());
    }

    @PostMapping(BIND_EMAIL_CODE)
    Result<EmailCodeSent> sendRegistrationEmail(@Valid @RequestBody RegistrationIdentity input) {
        return Result.success(registrationEmails.send(input));
    }

    @PostMapping(BIND)
    Result<Void> bind(@Valid @RequestBody Bind input) {
        if (!accounts.bind(input)) throw BusinessException.bad(REGISTRATION_CODE_INVALID);
        return Result.success();
    }

    @GetMapping("/notification-email")
    ResponseEntity<Result<EmailSettings>> notificationEmail(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(accounts.notificationEmail(accounts.viewer(principal))));
    }

    @PutMapping("/notification-email")
    Result<Void> updateNotificationEmail(Principal principal, @Valid @RequestBody EmailAddress input) {
        accounts.updateNotificationEmail(accounts.viewer(principal), input);
        return Result.success();
    }

    @GetMapping("/admin/classes")
    Result<List<Group>> groups(Principal principal) {
        return Result.success(accounts.groups(accounts.viewer(principal)));
    }

    @PostMapping("/admin/classes")
    Result<Group> addGroup(Principal principal, @Valid @RequestBody NewGroup input) {
        return Result.success(accounts.createGroup(accounts.viewer(principal), input));
    }

    @PostMapping("/admin/students")
    Result<Void> addStudent(Principal principal, @Valid @RequestBody NewStudent input) {
        accounts.createStudent(accounts.viewer(principal), input);
        return Result.success();
    }

    @GetMapping("/admin/accounts")
    Result<List<UserSummary>> users(Principal principal, @RequestParam @NotBlank @Size(max = MAX_USERNAME_LENGTH) String username) {
        return Result.success(accounts.findUsers(accounts.viewer(principal), username));
    }

    @PostMapping("/admin/accounts")
    Result<UserSummary> newStaff(Principal principal, @Valid @RequestBody NewStaff input) {
        return Result.success(accounts.createStaff(accounts.viewer(principal), input));
    }

    @GetMapping("/admin/accounts/{id}/roles")
    Result<List<Grant>> grants(Principal principal, @PathVariable long id) {
        return Result.success(accounts.grants(accounts.viewer(principal), id));
    }

    @PostMapping("/admin/accounts/{id}/roles")
    Result<Void> grant(Principal principal, @PathVariable long id, @Valid @RequestBody GrantChange input) {
        accounts.changeGrant(accounts.viewer(principal), id, input, true);
        return Result.success();
    }

    @DeleteMapping("/admin/accounts/{id}/roles")
    Result<Void> revoke(Principal principal, @PathVariable long id, @Valid @RequestBody GrantChange input) {
        accounts.changeGrant(accounts.viewer(principal), id, input, false);
        return Result.success();
    }

    @PutMapping("/admin/accounts/{id}/enabled")
    Result<Void> toggle(Principal principal, @PathVariable long id, @Valid @RequestBody Enabled input) {
        accounts.setEnabled(accounts.viewer(principal), id, input.enabled());
        return Result.success();
    }

    @PostMapping("/admin/accounts/{id}/password-reset")
    ResponseEntity<Result<TemporaryPassword>> resetPassword(Principal principal, @PathVariable long id) {
        var temporary = accounts.resetPassword(accounts.viewer(principal), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(temporary));
    }
}
