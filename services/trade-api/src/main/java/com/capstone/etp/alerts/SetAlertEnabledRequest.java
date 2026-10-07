package com.capstone.etp.alerts;

import jakarta.validation.constraints.NotNull;


public record SetAlertEnabledRequest(@NotNull Boolean enabled) {
}
