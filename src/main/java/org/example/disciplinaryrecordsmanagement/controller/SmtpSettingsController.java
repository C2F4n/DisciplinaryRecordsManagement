package org.example.disciplinaryrecordsmanagement.controller;

import java.security.Principal;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.ChangeSettings;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.PasswordChange;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.Settings;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.Unlock;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.Unlocked;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.example.disciplinaryrecordsmanagement.service.SmtpSettingsService;
import org.example.disciplinaryrecordsmanagement.service.SmtpSettingsService.Access;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.SMTP_SETTINGS_PATH;
import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.SMTP_UNLOCK_HEADER;
import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.SMTP_UNLOCK_MILLIS;

@RestController
@RequestMapping(ROOT + SMTP_SETTINGS_PATH)
public class SmtpSettingsController {
    private static final String ACCESS_ATTRIBUTE = SmtpSettingsController.class.getName() + ".access";
    private final AccountService accounts;
    private final SmtpSettingsService settings;

    SmtpSettingsController(AccountService accounts, SmtpSettingsService settings) {
        this.accounts = accounts;
        this.settings = settings;
    }

    @PostMapping("/unlock")
    ResponseEntity<Result<Unlocked>> unlock(Principal principal, HttpSession session, @Valid @RequestBody Unlock input) {
        var viewer = accounts.viewer(principal);
        var access = settings.unlock(viewer, input);
        var details = settings.settings(viewer, access, access.token());
        session.setAttribute(ACCESS_ATTRIBUTE, access);
        return noStore(new Unlocked(access.token(), SMTP_UNLOCK_MILLIS, details));
    }

    @DeleteMapping("/unlock")
    Result<Void> lock(Principal principal, HttpSession session,
                      @RequestHeader(name = SMTP_UNLOCK_HEADER, required = false) String token) {
        accounts.viewer(principal).requireAdmin();
        var access = access(session);
        if (access != null && access.token().equals(token)) session.removeAttribute(ACCESS_ATTRIBUTE);
        return Result.success();
    }

    @GetMapping
    ResponseEntity<Result<Settings>> get(Principal principal, HttpSession session,
                                        @RequestHeader(name = SMTP_UNLOCK_HEADER, required = false) String token) {
        return noStore(settings.settings(accounts.viewer(principal), access(session), token));
    }

    @PutMapping
    ResponseEntity<Result<Settings>> save(Principal principal, HttpSession session,
                                         @RequestHeader(name = SMTP_UNLOCK_HEADER, required = false) String token,
                                         @Valid @RequestBody ChangeSettings input) {
        return noStore(settings.save(accounts.viewer(principal), access(session), token, input));
    }

    @PutMapping("/password")
    Result<Void> changePassword(Principal principal, HttpSession session,
                                @RequestHeader(name = SMTP_UNLOCK_HEADER, required = false) String token,
                                @Valid @RequestBody PasswordChange input) {
        settings.changePassword(accounts.viewer(principal), access(session), token, input);
        session.removeAttribute(ACCESS_ATTRIBUTE);
        return Result.success();
    }

    private static Access access(HttpSession session) {
        return (Access) session.getAttribute(ACCESS_ATTRIBUTE);
    }

    private static <T> ResponseEntity<Result<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(body));
    }
}
