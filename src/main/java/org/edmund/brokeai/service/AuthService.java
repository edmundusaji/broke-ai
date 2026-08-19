package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.dto.MergeGuestRequest;
import org.edmund.brokeai.dto.MergeGuestResponse;

public interface AuthService {
    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse guestLogin();

    LoginResponse upgradeGuest(UpgradeGuestRequest request);

    CurrentUserResponse getCurrentUser();

    MergeGuestResponse mergeGuest(MergeGuestRequest request);
}
