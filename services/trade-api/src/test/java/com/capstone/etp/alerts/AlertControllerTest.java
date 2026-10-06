package com.capstone.etp.alerts;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.controller.ExtensionExceptionHandler;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertControllerTest {

    private AlertService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AlertService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AlertController(service))
                .setControllerAdvice(new ExtensionExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonConfig().objectMapper()))
                .setValidator(validator)
                .build();
    }

    private static AlertResponse alert() {
        return new AlertResponse(7L, "AAPL", AlertDirection.ABOVE, new BigDecimal("235.00"), AlertStatus.ACTIVE,
                DeliveryState.NONE, null, null, Instant.parse("2026-10-06T09:00:00Z"));
    }

    @Test
    void createReturns201WithTheAlertAndItsState() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(alert());

        mvc.perform(post("/api/v1/accounts/1/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"ABOVE\",\"threshold\":235.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.deliveryState").value("NONE"));
    }

    @Test
    void aThresholdThatIsNotAPositiveNumberIsRefused() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"ABOVE\",\"threshold\":-5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        verifyNoInteractions(service);
    }

    @Test
    void aSymbolWithUnexpectedCharactersIsRefused() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL; DROP TABLE accounts\",\"direction\":\"ABOVE\",\"threshold\":5}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void anAccountIdInTheBodyIsNotAField() throws Exception {
        // The account always comes from the path and the token; a body that tries to name one is refused.
        mvc.perform(post("/api/v1/accounts/1/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":2,\"symbol\":\"AAPL\",\"direction\":\"ABOVE\",\"threshold\":5}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void theCapIsReportedAs409() throws Exception {
        when(service.create(eq(1L), any()))
                .thenThrow(new ModuleException(HttpStatus.CONFLICT, "ALR-409", "You can keep at most 25 alerts"));

        mvc.perform(post("/api/v1/accounts/1/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"ABOVE\",\"threshold\":235}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALR-409"));
    }

    @Test
    void anotherAccountIsForbidden() throws Exception {
        when(service.list(2L)).thenThrow(new AccountAccessDeniedException());

        mvc.perform(get("/api/v1/accounts/2/alerts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    @Test
    void listAndDeleteWork() throws Exception {
        when(service.list(1L)).thenReturn(List.of(alert()));
        when(service.remove(1L, 7L)).thenReturn(alert());

        mvc.perform(get("/api/v1/accounts/1/alerts")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7));
        mvc.perform(delete("/api/v1/accounts/1/alerts/7")).andExpect(status().isOk());
    }
}
