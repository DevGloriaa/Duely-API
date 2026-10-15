package com.duesync.service;

import com.duesync.model.Setting;
import com.duesync.repo.SettingRepository;
import com.duesync.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SettingsService {

    private static final String DUES_AMOUNT = "dues-amount";

    private final SettingRepository settings;
    private final BigDecimal defaultDuesAmount;

    public SettingsService(SettingRepository settings, @Value("${duesync.default-dues-amount}") BigDecimal defaultDuesAmount) {
        this.settings = settings;
        this.defaultDuesAmount = defaultDuesAmount;
    }

    public BigDecimal getDuesAmount() {
        return settings.findById(DUES_AMOUNT)
                .map(setting -> new BigDecimal(setting.getValue()))
                .orElse(defaultDuesAmount);
    }

    public BigDecimal setDuesAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("The dues amount must be greater than zero.");
        }
        settings.save(new Setting(DUES_AMOUNT, amount.toPlainString()));
        return amount;
    }
}
