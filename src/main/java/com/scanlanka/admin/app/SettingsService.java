package com.scanlanka.admin.app;

import com.scanlanka.admin.domain.AppSetting;
import com.scanlanka.admin.infra.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Admin-tunable settings (08 FR-ADMIN-4). Read by checkout/payments (toggles) — one source of truth. */
@Service
public class SettingsService {

    private final AppSettingRepository repo;
    private final AuditService audit;

    public SettingsService(AppSettingRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public Optional<String> get(String key) {
        return repo.findById(key).map(AppSetting::getValue);
    }

    public boolean getBool(String key, boolean defaultValue) {
        return get(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    @Transactional
    public void put(String key, String value, Long adminId) {
        String before = get(key).orElse(null);
        AppSetting setting = repo.findById(key).orElse(new AppSetting(key, value, adminId));
        setting.update(value, adminId);
        repo.save(setting);
        audit.log(adminId, "SETTING_UPDATE", "app_setting", key, before, value);
    }
}
