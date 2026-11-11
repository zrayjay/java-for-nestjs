package dev.javafornestjs.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security")
public record SecurityProperties(@NotBlank String issuer, @NotBlank String audience,
                                 @DefaultValue("300") @Min(1) @Max(3600) int accessSeconds,
                                 @DefaultValue("604800") @Min(1) @Max(2592000) long refreshSeconds,
                                 @DefaultValue("false") boolean ephemeralKeys,
                                 String privateKeyPath, String publicKeyPath) { }
